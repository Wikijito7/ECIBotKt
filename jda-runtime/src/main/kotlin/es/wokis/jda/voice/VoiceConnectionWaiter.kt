package es.wokis.jda.voice

import kotlinx.coroutines.withTimeoutOrNull
import net.dv8tion.jda.api.audio.hooks.ConnectionListener
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

/**
 * Application-owned voice connection state for one playback.
 *
 * JDA exposes connection status, not the causal source of a `NOT_CONNECTED`. This waiter
 * records what the adapter intended at each point, so an unexpected loss while connecting
 * or playing is a failure, while a `NOT_CONNECTED` after [expectDisconnect] confirms only
 * the requested end state. It never logs identifiers.
 */
internal class VoiceConnectionWaiter : ConnectionListener {

    private val connected = CompletableFuture<Unit>()

    /** Completes with the first terminal reason so playback can race it against the frame hand-off wait. */
    private val terminalSignal = CompletableFuture<String>()

    /** Serializes the desired state with the active disconnect request. */
    private val stateLock = Any()

    private var connectionRequested = false
    private var everConnected = false

    private val desiredState = AtomicReference(ConnectionState.IDLE)

    private var disconnectRequest: DisconnectRequest? = null

    private val terminalFailure = AtomicReference<String?>(null)

    fun connectionState(): ConnectionState = desiredState.get()

    fun markRequested() {
        synchronized(stateLock) {
            connectionRequested = true
            disconnectRequest = null
            desiredState.set(ConnectionState.CONNECTING)
        }
    }

    fun markPlaying() {
        synchronized(stateLock) {
            desiredState.compareAndSet(ConnectionState.CONNECTED, ConnectionState.PLAYING)
        }
    }

    fun expectDisconnect(): DisconnectRequest = synchronized(stateLock) {
        val request = DisconnectRequest()
        disconnectRequest = request
        desiredState.set(ConnectionState.DISCONNECTING)
        request
    }

    fun hasPendingConnectionRequest(): Boolean = synchronized(stateLock) {
        connectionRequested && !everConnected
    }

    fun checkTerminal() {
        terminalFailure.get()?.let { throw VoiceConnectionException(it) }
    }

    fun terminalMessage(): String? = terminalFailure.get()

    /**
     * Blocking wait kept for non-coroutine callers and focused tests.
     *
     * Production coroutine paths must use [awaitConnectedCancellable] instead.
     */
    fun awaitConnected(timeout: Long, unit: TimeUnit) {
        try {
            connected.get(timeout, unit)
        } catch (_: TimeoutException) {
            terminalFailure.get()?.let { throw VoiceConnectionException(it) }
            throw TimeoutException("Voice connection did not reach CONNECTED within ${unit.toMillis(timeout)} ms.")
        } catch (error: ExecutionException) {
            throw error.cause as? VoiceConnectionException
                ?: VoiceConnectionException(error.cause?.message ?: "Voice connection failed.", error.cause)
        }
    }

    /**
     * Cancellable connection wait used by the coroutine playback path.
     *
     * It suspends instead of blocking, so coroutine cancellation resumes it immediately. A
     * terminal status recorded during the wait is surfaced as [VoiceConnectionException]; an
     * expired wait without a terminal status is a [TimeoutException].
     */
    suspend fun awaitConnectedCancellable(timeoutMillis: Long) {
        val connectedInTime = withTimeoutOrNull(timeoutMillis) {
            connected.awaitCancellable(cancelFutureOnCancellation = false)
            true
        } ?: false

        if (!connectedInTime) {
            terminalFailure.get()?.let { throw VoiceConnectionException(it) }
            throw TimeoutException("Voice connection did not reach CONNECTED within $timeoutMillis ms.")
        }
    }

    /**
     * Suspends until the first terminal status is recorded and returns its safe reason.
     *
     * The signal is shared and is never cancelled by a caller, so a racing playback can stop
     * waiting without changing the waiter's own state.
     */
    suspend fun awaitTerminalFailure(): String =
        terminalSignal.awaitCancellable(cancelFutureOnCancellation = false)

    override fun onStatusChange(status: ConnectionStatus) {
        val decision: StatusDecision
        synchronized(stateLock) {
            val observedState = desiredState.get()
            val observedRequest = disconnectRequest
            decision = classifyLocked(status, observedState, observedRequest)
        }

        decision.acknowledgeRequest?.acknowledge()
        if (decision.connectSignal) {
            connected.complete(Unit)
        }
        decision.terminal?.let { recorded ->
            LOG.warn("Voice connection ended: {}", recorded.reason)
            terminalSignal.complete(recorded.reason)
            connected.completeExceptionally(VoiceConnectionException(recorded.reason))
        }
    }

    /** Must be called while holding [stateLock]; see [onStatusChange]. */
    private fun classifyLocked(
        status: ConnectionStatus,
        observedState: ConnectionState,
        observedRequest: DisconnectRequest?,
    ): StatusDecision = when {
        status == ConnectionStatus.CONNECTED -> {
            everConnected = true
            desiredState.compareAndSet(ConnectionState.CONNECTING, ConnectionState.CONNECTED)
            StatusDecision(connectSignal = true)
        }

        status == ConnectionStatus.NOT_CONNECTED -> classifyNotConnectedLocked(observedState, observedRequest)

        status == ConnectionStatus.SHUTTING_DOWN -> terminal(
            "JDA shut down while the voice connection was open.",
        )

        status == ConnectionStatus.DISCONNECTED_CHANNEL_DELETED -> terminal(
            "The voice channel was deleted while the connection was open.",
        )

        status == ConnectionStatus.DISCONNECTED_AUTHENTICATION_FAILURE -> terminal(
            "Discord rejected the voice connection authentication.",
        )

        status == ConnectionStatus.AUDIO_REGION_CHANGE -> terminal(
            "Discord moved the voice audio region and JDA aborted the connection.",
        )

        status.name.startsWith("ERROR_") -> terminal("Voice connection failed: $status.")

        !status.shouldReconnect() -> terminal("Voice connection failed: $status.")

        else -> StatusDecision()
    }

    /** Must be called while holding [stateLock]; see [onStatusChange]. */
    private fun classifyNotConnectedLocked(
        observedState: ConnectionState,
        observedRequest: DisconnectRequest?,
    ): StatusDecision = when (observedState) {
        ConnectionState.DISCONNECTING -> {
            if (
                observedRequest != null &&
                disconnectRequest === observedRequest &&
                desiredState.get() == ConnectionState.DISCONNECTING
            ) {
                desiredState.set(ConnectionState.DISCONNECTED)
                disconnectRequest = null
                StatusDecision(acknowledgeRequest = observedRequest)
            } else {
                StatusDecision()
            }
        }

        ConnectionState.DISCONNECTED, ConnectionState.IDLE ->
            StatusDecision()

        ConnectionState.CONNECTING -> terminal(
            "Voice connection closed before it reached CONNECTED.",
        )

        ConnectionState.CONNECTED, ConnectionState.PLAYING -> terminal(
            "Voice connection was lost before cleanup.",
        )
    }

    /** Must be called while holding [stateLock]; see [onStatusChange]. */
    private fun terminal(reason: String): StatusDecision =
        StatusDecision(terminal = recordTerminalLocked(reason))

    private fun recordTerminalLocked(reason: String): Terminal? =
        if (terminalFailure.compareAndSet(null, reason)) Terminal(reason) else null

    private data class Terminal(val reason: String)

    private data class StatusDecision(
        val connectSignal: Boolean = false,
        val acknowledgeRequest: DisconnectRequest? = null,
        val terminal: Terminal? = null,
    )

    /** One local cleanup request; only its own `NOT_CONNECTED` acknowledgement completes it. */
    internal class DisconnectRequest {
        private val acknowledged = CompletableFuture<Unit>()

        internal fun acknowledge() {
            acknowledged.complete(Unit)
        }

        /** Blocking acknowledge wait kept for non-coroutine callers and focused tests. */
        fun await(timeout: Long, unit: TimeUnit): Boolean = try {
            acknowledged.get(timeout, unit)
            true
        } catch (_: TimeoutException) {
            false
        } catch (_: ExecutionException) {
            false
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }

        /** Cancellable acknowledge wait used by coroutine cleanup. */
        suspend fun awaitCancellable(timeoutMillis: Long): Boolean =
            withTimeoutOrNull(timeoutMillis) {
                acknowledged.awaitCancellable()
                true
            } ?: false
    }

    internal enum class ConnectionState {
        IDLE,
        CONNECTING,
        CONNECTED,
        PLAYING,
        DISCONNECTING,
        DISCONNECTED,
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(VoiceConnectionWaiter::class.java)
    }
}

/** Terminal voice-connection failure with a user-safe message. */
internal class VoiceConnectionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
