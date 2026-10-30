package spike.jdadave

import net.dv8tion.jda.api.audio.hooks.ConnectionListener
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.managers.AudioManager
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private val LOG = LoggerFactory.getLogger(VoiceTestProbe::class.java)

/**
 * Guild-scoped `/voice-test` handler.
 *
 * The gateway thread only validates the invocation and defers the reply. The actual
 * voice connection, tone playback and cleanup run on a dedicated single worker thread so
 * the gateway event loop is never blocked.
 *
 * Every invocation is tracked by a [ProbeRun] latch: whichever path finishes first (the
 * worker, the synchronous acknowledgement failure, or a gateway callback failure) reports
 * exactly one terminal outcome, so `Main`'s one-shot future cannot wait forever.
 */
class VoiceTestProbe(
    private val testGuildId: Long,
    private val voiceConnectTimeoutMillis: Long = SpikeConfig.DEFAULT_VOICE_CONNECT_TIMEOUT_MILLIS,
    private val onFinished: (ProbeOutcome) -> Unit,
) : ListenerAdapter(), AutoCloseable {

    private val running = AtomicBoolean(false)
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "voice-test-worker").apply { isDaemon = true }
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND_NAME) {
            return
        }

        val guild = event.guild
        if (guild == null || guild.idLong != testGuildId) {
            event.reply("This command only runs in the configured staging guild.")
                .setEphemeral(true)
                .queue()
            return
        }

        if (!running.compareAndSet(false, true)) {
            event.reply("A voice probe is already running. Wait for it to finish.")
                .setEphemeral(true)
                .queue()
            return
        }

        val run = ProbeRun(onFinished)

        // Acknowledge within Discord's 3 second window, then hand off to the worker.
        //
        // Both `deferReply(true)` itself and the `queue(...)` registration can throw
        // synchronously (a detached/unusual event, a rejected rest action, a broken JDA
        // callback implementation, ...). When that happens neither callback is installed,
        // so nothing would ever finish the run and one-shot mode would hang forever. In
        // that case the guard is released and the run is completed here with exactly one
        // Failure. The interaction is in an unknown acknowledgement state, so no second
        // acknowledgement is attempted: only logging and the terminal outcome are safe.
        val callbacksRan = AtomicBoolean(false)
        val deferAction = try {
            event.deferReply(true)
        } catch (t: Throwable) {
            LOG.error("[interaction] could not create the defer reply action", t)
            running.set(false)
            run.finish(ProbeOutcome.Failure(ACKNOWLEDGE_FAILURE_MESSAGE))
            return
        }

        try {
            deferAction.queue(
                {
                    callbacksRan.set(true)
                    try {
                        worker.execute { executeProbe(event, run) }
                    } catch (t: Throwable) {
                        LOG.error("[interaction] could not schedule probe worker", t)
                        running.set(false)
                        // The interaction is already deferred, so editing the deferred reply is
                        // the only valid user-visible error path here. It is best effort: the
                        // outcome is reported even if the edit never reaches Discord.
                        editDeferredReplyBestEffort(event, WORKER_SCHEDULE_FAILURE_MESSAGE)
                        run.finish(ProbeOutcome.Failure(WORKER_SCHEDULE_FAILURE_MESSAGE))
                    }
                },
                { t ->
                    callbacksRan.set(true)
                    LOG.error("[interaction] could not defer reply", t)
                    running.set(false)
                    // The interaction may already be acknowledged server-side, so sending a
                    // second acknowledgement would be invalid. Log only and finish the run so
                    // one-shot mode exits instead of hanging.
                    run.finish(ProbeOutcome.Failure(ACKNOWLEDGE_FAILURE_MESSAGE))
                },
            )
        } catch (t: Throwable) {
            LOG.error("[interaction] could not register the defer reply callbacks", t)
            // If a callback already ran it owns both the guard and the run, and finishing
            // here would race a valid in-flight probe. Otherwise nothing was acknowledged
            // and nothing can start, so this is the only terminal path.
            if (callbacksRan.compareAndSet(false, true)) {
                running.set(false)
                run.finish(ProbeOutcome.Failure(ACKNOWLEDGE_FAILURE_MESSAGE))
            }
        }
    }

    override fun close() {
        worker.shutdownNow()
    }

    private fun executeProbe(event: SlashCommandInteractionEvent, run: ProbeRun) {
        try {
            val outcome = doProbe(event) ?: return // user error, already answered
            replyAndAwait(event, outcome.userMessage)
            run.finish(outcome)
        } catch (t: Throwable) {
            LOG.error("[probe] unexpected failure", t)
            val failure = ProbeOutcome.Failure(failureMessage(t))
            replyAndAwait(event, failure.userMessage)
            run.finish(failure)
        } finally {
            running.set(false)
        }
    }

    /**
     * Returns null when the invocation cannot start a probe and was answered with guidance.
     *
     * Package-visible so the spike's tests can drive the invocation paths directly instead
     * of reaching into private members by reflection.
     */
    internal fun doProbe(event: SlashCommandInteractionEvent): ProbeOutcome? {
        val guild = event.guild ?: return null.also {
            replyAndAwait(event, "This command must be used inside the staging guild.")
        }

        if (guild.idLong != testGuildId) {
            replyAndAwait(event, "This command only runs in the configured staging guild.")
            return null
        }

        val member = event.member
        if (member == null) {
            replyAndAwait(event, "Could not resolve your guild member. Try again from the staging guild.")
            return null
        }

        val channel = try {
            member.voiceState?.channel
        } catch (t: Throwable) {
            LOG.warn("[probe] could not resolve voice state for {}", member.id, t)
            replyAndAwait(event, "Could not read your voice state. Make sure the bot has the GUILD_VOICE_STATES intent.")
            return null
        }

        if (channel == null) {
            replyAndAwait(event, "Join a regular voice channel in this guild, then run /$COMMAND_NAME again.")
            return null
        }

        val voiceChannel = channel as? VoiceChannel
        if (voiceChannel == null) {
            replyAndAwait(event, "Use a regular voice channel. Stage channels are not supported by this probe.")
            return null
        }

        if (voiceChannel.guild.idLong != testGuildId) {
            replyAndAwait(event, "Join a voice channel inside the configured staging guild.")
            return null
        }

        return connectAndPlay(guild, voiceChannel)
    }

    /** Package-visible test seam; the production entry point is [executeProbe]. */
    internal fun connectAndPlay(guild: Guild, channel: VoiceChannel): ProbeOutcome {
        val audioManager = guild.audioManager
        val waiter = ConnectionWaiter()
        var tone: ToneSendHandler? = null
        var outcome: ProbeOutcome = ProbeOutcome.Failure("Probe ended without an outcome. Check the logs.")

        try {
            audioManager.setConnectionListener(waiter)
            audioManager.setAutoReconnect(false)
            audioManager.setConnectTimeout(voiceConnectTimeoutMillis)

            LOG.info(
                "[voice] opening connection to '{}' ({}) in guild {}",
                channel.name,
                channel.id,
                guild.id,
            )
            waiter.markRequested()
            audioManager.openAudioConnection(channel)

            waiter.awaitConnected(voiceConnectTimeoutMillis, TimeUnit.MILLISECONDS)
            waiter.checkTerminal()
            LOG.info("[voice] connected to '{}' ({})", channel.name, channel.id)

            // CONNECTED is reached after JDA has processed SELECT_PROTOCOL_ACK. Give the
            // MLS group a short, fixed window to complete its transition before sending
            // media, so the tone is not competing with the DAVE handshake.
            LOG.info("[voice] waiting {} ms for the DAVE transition before the tone", DAVE_SETTLE_MILLIS)
            Thread.sleep(DAVE_SETTLE_MILLIS)
            waiter.checkTerminal()

            val handler = ToneSendHandler()
            tone = handler
            audioManager.setSendingHandler(handler)
            // The probe now owns a media-sending phase. A NOT_CONNECTED while in this
            // application-owned state is an unexpected loss unless cleanup asks for it.
            waiter.markPlaying()
            LOG.info(
                "[tone] started: {} Hz for {} ms ({} frames x {} bytes, PCM {} Hz/{} bit/{} ch big-endian, amplitude={})",
                handler.frequencyHz,
                handler.durationMillis,
                handler.totalFrames,
                ToneSendHandler.FRAME_BYTES,
                ToneSendHandler.SAMPLE_RATE,
                ToneSendHandler.BITS_PER_SAMPLE,
                ToneSendHandler.CHANNELS,
                handler.amplitude,
            )

            handler.awaitFinished(TONE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            // A terminal status while the tone was playing must not be reported as success.
            waiter.checkTerminal()
            // Success requires every frame to have been handed to JDA. `awaitFinished`
            // normally guarantees this, but the explicit check keeps the contract visible.
            if (handler.framesProvided != handler.totalFrames) {
                throw VoiceProbeException(
                    "Only ${handler.framesProvided}/${handler.totalFrames} tone frames were handed to JDA.",
                )
            }
            // Let the send loop push the final packet before tearing the connection down.
            Thread.sleep(FLUSH_MILLIS)
            LOG.info(
                "[tone] finished: framesProvided={}/{} (frames handed to JDA, not a confirmed transmission)",
                handler.framesProvided,
                handler.totalFrames,
            )
            // A connection lost during the final flush must fail the run instead of
            // slipping through as a success after the last check above.
            waiter.checkTerminal()
            outcome = ProbeOutcome.Success
        } catch (e: InsufficientPermissionException) {
            LOG.error("[voice] missing permission {}", e.permission, e)
            outcome = ProbeOutcome.Failure("The bot is missing permission ${e.permission}.")
        } catch (e: VoiceProbeException) {
            LOG.error("[voice] terminal connection failure: {}", e.message, e)
            outcome = ProbeOutcome.Failure(e.message ?: "Voice connection failed.")
        } catch (e: TimeoutException) {
            val terminalReason = waiter.terminalMessage()
            if (terminalReason != null) {
                // The deadline expired after a terminal status was observed: report the
                // precise cause instead of a generic timeout.
                LOG.error("[voice] timed out after a terminal status: {}", terminalReason, e)
                outcome = ProbeOutcome.Failure(terminalReason)
            } else {
                LOG.error("[voice] timed out waiting for the connection or tone send", e)
                outcome = ProbeOutcome.Failure("Timed out waiting for the voice connection or tone send.")
            }
        } catch (e: ExecutionException) {
            val cause = e.cause ?: e
            LOG.error("[voice] connection failed", cause)
            outcome = ProbeOutcome.Failure("Voice connection failed: ${cause.message ?: cause.javaClass.simpleName}")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            LOG.error("[voice] probe interrupted", e)
            outcome = ProbeOutcome.Failure("Probe interrupted.")
        } catch (t: Throwable) {
            LOG.error("[probe] voice probe failed", t)
            outcome = ProbeOutcome.Failure(failureMessage(t))
        } finally {
            val cleanupOk = cleanup(audioManager, waiter, tone)
            // A disconnect callback that started before cleanup recorded its local
            // disconnect intent is recorded as terminal even if cleanup changes the state
            // while the callback runs. A NOT_CONNECTED that starts after that intent is
            // accepted as the requested state, but the public API cannot prove JDA's close
            // caused it; only the terminal status/message is reported here.
            val terminalReason = waiter.terminalMessage()
            if (outcome is ProbeOutcome.Success && terminalReason != null) {
                LOG.error("[voice] connection was lost before cleanup: {}", terminalReason)
                outcome = ProbeOutcome.Failure(terminalReason)
            } else if (outcome is ProbeOutcome.Success && !cleanupOk) {
                outcome = ProbeOutcome.Failure(
                    "The tone finished, but the voice connection was not cleanly closed. Check the logs.",
                )
            }
        }

        return outcome
    }

    /** Package-visible test seam; returns whether the connection was closed cleanly. */
    internal fun cleanup(
        audioManager: AudioManager,
        waiter: ConnectionWaiter,
        tone: ToneSendHandler?,
    ): Boolean {
        var clean = true

        LOG.info("[cleanup] starting from desiredState={}", waiter.connectionState())
        try {
            audioManager.setSendingHandler(null)
        } catch (t: Throwable) {
            clean = false
            LOG.warn("[cleanup] could not clear the send handler", t)
        }

        if (tone != null) {
            LOG.info(
                "[tone] final framesProvided={}/{} (frames handed to JDA, not a confirmed transmission)",
                tone.framesProvided,
                tone.totalFrames,
            )
        }

        try {
            val connected = audioManager.isConnected
            val status = audioManager.connectionStatus

            if (connected || status != ConnectionStatus.NOT_CONNECTED) {
                LOG.info("[voice] disconnect requested (connected={}, status={})", connected, status)
                // Record the application-owned DISCONNECTING state before asking JDA to
                // close. A later NOT_CONNECTED is then consistent with the requested
                // state, but the public API cannot prove JDA's close caused it.
                //
                // The acknowledgement is created with the intent transition and belongs to
                // this request only. A NOT_CONNECTED observed earlier (for example while
                // IDLE) or processed by a stale/duplicate callback cannot satisfy it.
                val disconnectRequest = waiter.expectDisconnect()
                audioManager.closeAudioConnection()
                val statusConfirmed = disconnectRequest.await(DISCONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (statusConfirmed) {
                    LOG.info(
                        "[voice] NOT_CONNECTED observed after the local disconnect request; " +
                            "requested disconnected state reached (cause not attributable via public JDA API)",
                    )
                } else {
                    clean = false
                    LOG.warn("[voice] disconnect was not confirmed within {}s", DISCONNECT_TIMEOUT_SECONDS)
                }
            } else if (waiter.hasPendingConnectionRequest()) {
                // JDA 6.7.0 only creates an AudioConnection after VOICE_SERVER_UPDATE, so a
                // connect that is still waiting on the gateway is neither `isConnected` nor
                // reflected in `connectionStatus`. Closing it here cancels the queued request
                // instead of leaving the bot to join after the probe has already failed.
                LOG.info("[voice] cancelling pending voice connection (no JDA audio session yet)")
                waiter.expectDisconnect()
                audioManager.closeAudioConnection()
                if (awaitSelfVoiceStateCleared(audioManager.guild)) {
                    LOG.info("[voice] pending voice connection cancelled")
                } else {
                    clean = false
                    LOG.warn(
                        "[voice] pending voice connection cancellation was not confirmed within {}s; " +
                            "Discord may still place the bot in a voice channel",
                        DISCONNECT_TIMEOUT_SECONDS,
                    )
                }
            } else {
                LOG.info("[voice] no audio connection to close")
            }
        } catch (t: Throwable) {
            clean = false
            LOG.warn("[cleanup] could not close the audio connection", t)
        } finally {
            try {
                audioManager.setConnectionListener(null)
            } catch (t: Throwable) {
                clean = false
                LOG.warn("[cleanup] could not clear the connection listener", t)
            }
            try {
                audioManager.setAutoReconnect(true)
            } catch (t: Throwable) {
                clean = false
                LOG.warn("[cleanup] could not restore auto-reconnect", t)
            }
        }

        return clean
    }

    /**
     * Waits for the guild's own voice state to disappear after a pending connect was
     * cancelled. JDA exposes no "attempting" flag on [AudioManager] in 6.7.0, so the
     * gateway-visible voice state is the only public confirmation that the bot is no
     * longer queued to join a channel.
     *
     * Returns true only after a voice state was actually observed and then cleared. If the
     * bot never became visible in a channel there is no way to confirm that the queued
     * request was drained, so the caller must not report a clean cleanup.
     */
    private fun awaitSelfVoiceStateCleared(guild: Guild): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DISCONNECT_TIMEOUT_SECONDS)
        var observedVoiceState = guild.selfMember.voiceState?.channel != null
        while (System.nanoTime() < deadline) {
            val inVoiceChannel = guild.selfMember.voiceState?.channel != null
            if (inVoiceChannel) {
                observedVoiceState = true
            } else if (observedVoiceState) {
                return true
            }
            try {
                Thread.sleep(SELF_VOICE_STATE_POLL_MILLIS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return observedVoiceState && guild.selfMember.voiceState?.channel == null
    }

    /** Package-visible test seam; always swallows edit failures. */
    internal fun replyAndAwait(event: SlashCommandInteractionEvent, text: String) {
        try {
            event.hook.editOriginal(text).submit().get(REPLY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (t: Throwable) {
            LOG.error("[interaction] could not edit the deferred reply", t)
        }
    }

    /**
     * Fire-and-forget edit of an already deferred reply. Used from JDA callback threads
     * where blocking for a round trip would stall the gateway's response handling; the
     * caller reports the outcome independently of whether the edit lands.
     */
    private fun editDeferredReplyBestEffort(event: SlashCommandInteractionEvent, text: String) {
        try {
            event.hook.editOriginal(text).submit().whenComplete { _, t ->
                if (t != null) {
                    LOG.warn("[interaction] could not edit the deferred reply", t)
                }
            }
        } catch (t: Throwable) {
            LOG.warn("[interaction] could not submit the deferred reply edit", t)
        }
    }

    private fun failureMessage(t: Throwable): String = when (t) {
        is InsufficientPermissionException -> "The bot is missing permission ${t.permission}."

        is VoiceProbeException -> t.message ?: "Voice connection failed."

        is TimeoutException -> "Timed out waiting for the voice connection or tone send."

        else -> "Probe failed (${t.javaClass.simpleName}). Check the container logs."
    }

    /**
     * Application-owned connection state for one probe run.
     *
     * JDA's [ConnectionStatus] is observed input; this state records what the probe itself
     * intended at the time. That distinction is what lets the probe fail an unexpected
     * `NOT_CONNECTED` while connecting or playing, without pretending the public API can
     * attribute the cause of a `NOT_CONNECTED` observed after cleanup requested a close.
     */
    internal enum class ProbeConnectionState {
        /** No voice connection has been requested in this run. */
        IDLE,

        /** `openAudioConnection` was issued and `CONNECTED` has not been observed yet. */
        CONNECTING,

        /** `CONNECTED` was observed; the DAVE settle window is running. */
        CONNECTED,

        /** The tone send handler is installed and frames are being handed to JDA. */
        PLAYING,

        /**
         * The probe has recorded local disconnect intent and is about to call (or has
         * called) [AudioManager.closeAudioConnection].
         */
        DISCONNECTING,

        /**
         * A `NOT_CONNECTED` was observed after local disconnect intent. This means the
         * requested disconnected state was reached; it does not identify the cause.
         */
        DISCONNECTED,
    }

    /** Tracks the JDA audio connection state without blocking the audio threads. */
    internal class ConnectionWaiter(
        private val statusBarrier: StatusCallbackBarrier = StatusCallbackBarrier.NONE,
    ) : ConnectionListener {

        /**
         * Deterministic synchronization seam for same-package tests. It runs exactly after
         * [onStatusChange] snapshots the application-owned state at callback entry and
         * before the callback classifies it, while the waiter's state lock is held.
         * Production installs the no-op default, so the critical section has no logging or
         * monitor dependency; the callback emits its log lines only after the lock is
         * released.
         *
         * A test barrier must not call a waiter method that acquires the state lock
         * ([markRequested], [markPlaying], [expectDisconnect]) from the barrier itself; that
         * would deadlock. Those calls belong on another thread and can only proceed after
         * the barrier releases the callback.
         */
        internal fun interface StatusCallbackBarrier {
            fun afterEntrySnapshot(status: ConnectionStatus)

            companion object {
                val NONE: StatusCallbackBarrier = StatusCallbackBarrier { }
            }
        }

        private val connected = CompletableFuture<Unit>()

        /** Serializes the desired state with the active disconnect request. */
        private val stateLock = Any()

        @Volatile
        private var connectionRequested = false

        @Volatile
        private var everConnected = false

        /**
         * Application-owned desired state. It is transitioned by the probe, not derived
         * from JDA, so a status callback can tell "nothing was requested yet" from
         * "the connection this probe requested was lost".
         */
        private val desiredState = AtomicReference(ProbeConnectionState.IDLE)

        /**
         * The acknowledgement for the one local disconnect request currently in flight, or
         * null when no request is active. Replaced atomically with the DISCONNECTING
         * transition so a stale or duplicate status can never confirm a newer request.
         */
        @Volatile
        private var disconnectRequest: DisconnectRequest? = null

        private val terminalFailure = AtomicReference<String?>(null)

        /**
         * One local cleanup request. Only a `NOT_CONNECTED` processed while this exact
         * request is still active can complete it.
         */
        internal class DisconnectRequest {
            private val acknowledged = CompletableFuture<Unit>()

            internal fun acknowledge() {
                acknowledged.complete(Unit)
            }

            /** Returns true only when this request's acknowledgement arrived in time. */
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
        }

        fun connectionState(): ProbeConnectionState = desiredState.get()

        fun markRequested() {
            synchronized(stateLock) {
                connectionRequested = true
                disconnectRequest = null
                desiredState.set(ProbeConnectionState.CONNECTING)
            }
        }

        /** Records that the probe has handed the tone to JDA's send loop. */
        fun markPlaying() {
            synchronized(stateLock) {
                desiredState.compareAndSet(ProbeConnectionState.CONNECTED, ProbeConnectionState.PLAYING)
            }
        }

        /**
         * Atomically records the local disconnect intent and creates the fresh
         * acknowledgement for this request. The caller must await the returned request
         * instead of any shared/global status future, so an earlier `NOT_CONNECTED` cannot
         * confirm this cleanup.
         */
        fun expectDisconnect(): DisconnectRequest = synchronized(stateLock) {
            // This transition shares the callback's lock, so a callback that snapshotted
            // CONNECTED/PLAYING either records its terminal failure before this point or
            // classifies the status against the DISCONNECTING state, never both in an order
            // that lets cleanup observe a stale "no failure".
            //
            // A replacement request is still created when an unexpected terminal was already
            // recorded: cleanup must be able to observe its own disconnect. The terminal
            // failure is never cleared here, so the recorded cause stays first-wins.
            val request = DisconnectRequest()
            disconnectRequest = request
            desiredState.set(ProbeConnectionState.DISCONNECTING)
            request
        }

        /** True while the request has never reached CONNECTED in this run. */
        fun hasPendingConnectionRequest(): Boolean = synchronized(stateLock) {
            // Both fields are guarded by [stateLock], so read them under the same lock that
            // [markRequested] and the callback's CONNECTED branch use. Reading them without
            // the lock could observe a CONNECTED callback's `everConnected` write without
            // its matching connection request, or vice versa.
            connectionRequested && !everConnected
        }

        override fun onStatusChange(status: ConnectionStatus) {
            // The full callback decision is atomic with respect to local intent and
            // cleanup's disconnect request. Cleanup creates its request with
            // `expectDisconnect()` under this same lock, so a callback that snapshotted
            // CONNECTED/PLAYING records its terminal failure before cleanup can transition
            // to DISCONNECTING and read "no failure", and a callback that observes
            // DISCONNECTING is the one that acknowledges the request.
            //
            // Nothing here calls JDA, waits on the probe worker, logs, or invokes a run
            // completion callback while holding the lock. The only blocking point inside
            // the critical section is the test barrier, which production installs as a
            // no-op. The decision is captured as a plain value, then all logging,
            // acknowledgement and future publication happen after the lock is released so
            // slow log appenders and future continuations never run with the probe lock
            // held.
            val decision: StatusDecision
            synchronized(stateLock) {
                // Snapshot the application-owned state and the active disconnect request
                // under the same lock that guards their transitions. Classification,
                // state mutation and terminal recording below all use this one coherent
                // view; cleanup cannot interleave its DISCONNECTING transition.
                val observedState = desiredState.get()
                val observedRequest = disconnectRequest
                statusBarrier.afterEntrySnapshot(status)
                decision = classifyLocked(status, observedState, observedRequest)
            }

            // Everything below runs after the state lock is released.
            LOG.info("[voice] status={} (desiredState={})", status, decision.observedState)
            decision.infoMessage?.let { message -> LOG.info("[voice] {}", message) }
            decision.acknowledgeRequest?.acknowledge()
            if (decision.connectSignal) {
                connected.complete(Unit)
            }
            decision.terminal?.let { recorded ->
                LOG.error("[voice] terminal status={} reason={}", recorded.status, recorded.reason)
                connected.completeExceptionally(VoiceProbeException(recorded.reason))
            }
        }

        /**
         * Classifies one status against the state snapshotted at callback entry. Must be
         * called while holding [stateLock]; it mutates [desiredState], [everConnected] and
         * [disconnectRequest] but performs no logging, no future completion and no
         * acknowledgement, so the caller can publish all side effects after unlocking.
         */
        private fun classifyLocked(
            status: ConnectionStatus,
            observedState: ProbeConnectionState,
            observedRequest: DisconnectRequest?,
        ): StatusDecision = when {
            status == ConnectionStatus.CONNECTED -> {
                everConnected = true
                desiredState.compareAndSet(
                    ProbeConnectionState.CONNECTING,
                    ProbeConnectionState.CONNECTED,
                )
                StatusDecision(observedState = observedState, connectSignal = true)
            }

            status == ConnectionStatus.NOT_CONNECTED -> classifyNotConnectedLocked(
                status,
                observedState,
                observedRequest,
            )

            status == ConnectionStatus.SHUTTING_DOWN -> terminalDecisionLocked(
                status,
                observedState,
                "JDA shut down while the voice connection was open (SHUTTING_DOWN).",
            )

            status == ConnectionStatus.DISCONNECTED_CHANNEL_DELETED -> terminalDecisionLocked(
                status,
                observedState,
                "The voice channel was deleted while the probe was connected " +
                    "(DISCONNECTED_CHANNEL_DELETED).",
            )

            status == ConnectionStatus.DISCONNECTED_AUTHENTICATION_FAILURE -> terminalDecisionLocked(
                status,
                observedState,
                "Discord rejected the voice connection authentication " +
                    "(DISCONNECTED_AUTHENTICATION_FAILURE). Check that the staging bot token is still " +
                    "valid and that the bot may join voice in the test guild.",
            )

            status == ConnectionStatus.AUDIO_REGION_CHANGE -> terminalDecisionLocked(
                status,
                observedState,
                "Discord moved the voice audio region and JDA aborted the connection " +
                    "(AUDIO_REGION_CHANGE). The probe treats a region change as terminal; " +
                    "re-run /${VoiceTestProbe.COMMAND_NAME} to try again.",
            )

            status.name.startsWith("ERROR_") -> terminalDecisionLocked(
                status,
                observedState,
                "Voice connection failed: $status.",
            )

            !status.shouldReconnect() -> terminalDecisionLocked(
                status,
                observedState,
                "Voice connection failed: $status.",
            )

            else -> StatusDecision(observedState = observedState)
        }

        /** Must be called while holding [stateLock]; see [classifyLocked]. */
        private fun classifyNotConnectedLocked(
            status: ConnectionStatus,
            observedState: ProbeConnectionState,
            observedRequest: DisconnectRequest?,
        ): StatusDecision = when (observedState) {
            ProbeConnectionState.DISCONNECTING -> {
                // The request must still be the one this callback snapshotted, and the
                // intent must still be current. Otherwise this is a stale or duplicate
                // status and must not confirm a newer request. The snapshot and this check
                // are under one lock acquisition, so no request can be created between them.
                if (
                    observedRequest != null &&
                    disconnectRequest === observedRequest &&
                    desiredState.get() == ProbeConnectionState.DISCONNECTING
                ) {
                    // Reach the requested state and consume the request before releasing
                    // the lock. The acknowledgement is published by the caller after the
                    // lock is released, so waking the waiter cannot run dependent work
                    // under the probe lock.
                    desiredState.set(ProbeConnectionState.DISCONNECTED)
                    disconnectRequest = null
                    StatusDecision(
                        observedState = observedState,
                        infoMessage = "NOT_CONNECTED observed after the local disconnect request; " +
                            "requested disconnected state reached " +
                            "(cause not attributable via public JDA API)",
                        acknowledgeRequest = observedRequest,
                    )
                } else {
                    StatusDecision(
                        observedState = observedState,
                        infoMessage = "NOT_CONNECTED was stale or already consumed; " +
                            "it did not confirm the active disconnect request",
                    )
                }
            }

            ProbeConnectionState.DISCONNECTED -> StatusDecision(
                observedState = observedState,
                infoMessage = "NOT_CONNECTED observed again after the requested disconnected state was reached",
            )

            ProbeConnectionState.IDLE -> StatusDecision(
                observedState = observedState,
                infoMessage = "NOT_CONNECTED observed while no connection was requested; ignoring",
            )

            ProbeConnectionState.CONNECTING -> terminalDecisionLocked(
                status,
                observedState,
                "Voice connection closed before it reached CONNECTED (NOT_CONNECTED).",
            )

            // Once CONNECTED was observed, a NOT_CONNECTED that cleanup did not ask for
            // means the connection dropped during the tone/flush. It is terminal: the run
            // must not report Success.
            ProbeConnectionState.CONNECTED,
            ProbeConnectionState.PLAYING -> terminalDecisionLocked(
                status,
                observedState,
                "Voice connection was lost after CONNECTED (NOT_CONNECTED) before cleanup. " +
                    "The tone could not be confirmed.",
            )
        }

        /** Must be called while holding [stateLock]; see [classifyLocked]. */
        private fun terminalDecisionLocked(
            status: ConnectionStatus,
            observedState: ProbeConnectionState,
            reason: String,
        ): StatusDecision = StatusDecision(
            observedState = observedState,
            terminal = recordTerminalLocked(status, reason),
        )

        private data class Terminal(val status: ConnectionStatus, val reason: String)

        /**
         * The outcome of one status callback, produced under [stateLock] and consumed after
         * the lock is released. It carries the state snapshot taken at callback entry, the
         * log line to emit, and the side effects that must not run under the lock.
         */
        private data class StatusDecision(
            val observedState: ProbeConnectionState,
            val infoMessage: String? = null,
            val connectSignal: Boolean = false,
            val acknowledgeRequest: DisconnectRequest? = null,
            val terminal: Terminal? = null,
        )

        /**
         * Records a terminal status once. Must be called while holding [stateLock], so the
         * record lands before `expectDisconnect()` can transition to DISCONNECTING and let
         * cleanup observe the failure. The CAS keeps the first terminal reason first-wins
         * across concurrent callbacks, and later statuses cannot overwrite it.
         */
        private fun recordTerminalLocked(status: ConnectionStatus, reason: String): Terminal? =
            if (terminalFailure.compareAndSet(null, reason)) Terminal(status, reason) else null

        /** Fails fast when a terminal status arrived after [awaitConnected] returned. */
        fun checkTerminal() {
            terminalFailure.get()?.let { throw VoiceProbeException(it) }
        }

        /** The first terminal status reason, if any was observed. */
        fun terminalMessage(): String? = terminalFailure.get()

        fun awaitConnected(timeout: Long, unit: TimeUnit) {
            try {
                connected.get(timeout, unit)
            } catch (_: TimeoutException) {
                // If a terminal status raced with the deadline, report the real cause
                // instead of the generic connect timeout.
                terminalFailure.get()?.let { throw VoiceProbeException(it) }
                throw TimeoutException(
                    "Voice connection did not reach CONNECTED within ${unit.toMillis(timeout)} ms.",
                )
            }
        }
    }

    companion object {
        const val COMMAND_NAME = "voice-test"

        private const val WORKER_SCHEDULE_FAILURE_MESSAGE =
            "The probe worker could not be scheduled. Nothing was played; check the container logs."
        private const val ACKNOWLEDGE_FAILURE_MESSAGE =
            "Discord did not acknowledge /$COMMAND_NAME. Nothing was played; check the container logs."

        private const val DAVE_SETTLE_MILLIS = 1_500L
        private const val TONE_TIMEOUT_MILLIS = 15_000L
        private const val FLUSH_MILLIS = 300L
        private const val DISCONNECT_TIMEOUT_SECONDS = 5L
        private const val SELF_VOICE_STATE_POLL_MILLIS = 100L
        private const val REPLY_TIMEOUT_SECONDS = 10L
    }
}

sealed interface ProbeOutcome {
    val userMessage: String

    data object Success : ProbeOutcome {
        override val userMessage: String =
            "Tone frames were handed to JDA and the requested disconnected state was reached. " +
                "Check the container logs for DAVE Init/Transition evidence, and confirm the audible " +
                "tone with a second account. The public JDA API cannot attribute the cause of NOT_CONNECTED."
    }

    data class Failure(override val userMessage: String) : ProbeOutcome
}

/**
 * Terminal voice-connection failure. The message is short and safe to show in the
 * ephemeral reply, and it names the concrete [ConnectionStatus] that ended the probe.
 *
 * `internal` so the tests can assert this type directly instead of matching its name.
 */
internal class VoiceProbeException(message: String) : RuntimeException(message)

/**
 * One terminal outcome per `/voice-test` invocation.
 *
 * The worker path and the gateway callback failure paths race to finish the run (for
 * example a worker that is rejected right as the interaction is acknowledged). The first
 * call wins; later calls are ignored, so `Main`'s one-shot future is completed at most
 * once and never left hanging.
 *
 * `internal` so the tests can construct and finish it directly.
 */
internal class ProbeRun(private val onFinished: (ProbeOutcome) -> Unit) {
    private val finished = AtomicBoolean(false)

    fun finish(outcome: ProbeOutcome) {
        if (finished.compareAndSet(false, true)) {
            onFinished(outcome)
        }
    }
}
