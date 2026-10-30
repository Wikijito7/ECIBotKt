package spike.jdadave

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.audio.AudioSendHandler
import net.dv8tion.jda.api.audio.hooks.ConnectionListener
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.GuildVoiceState
import net.dv8tion.jda.api.entities.SelfMember
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.managers.AudioManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Connection / cleanup coverage for [VoiceTestProbe]'s internal seams.
 *
 * The probe owns an [AudioManager] through [Guild], so the audio manager is injected as a
 * MockK fake and the internal worker entry points are called directly.
 * No test talks to Discord and none of them wait for a real connection.
 */
class VoiceTestProbeConnectionTest {

    private val testGuildId = 123_456_789_012_345_678L

    @Test
    fun `a custom connect timeout is passed to JDA and bounds the waiter`() {
        val timeoutMillis = 250L
        val probe = VoiceTestProbe(testGuildId, timeoutMillis) {}
        try {
            val fixture = pendingFixture(clearVoiceState = true)

            val startedAt = System.nanoTime()
            val outcome = probe.connectAndPlay(fixture.guild, fixture.channel)
            val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

            assertFailureContains(outcome, "Timed out waiting for the voice connection")
            verify(exactly = 1) { fixture.audioManager.setConnectTimeout(timeoutMillis) }

            // The waiter must use the same configured deadline: it must not return before
            // it (which would mean a hardcoded 0/instant timeout) and must not wait for the
            // 20 s default.
            assertTrue(
                elapsedMillis >= timeoutMillis - 25,
                "waiter returned before the configured ${timeoutMillis}ms deadline (${elapsedMillis}ms)",
            )
            assertTrue(
                elapsedMillis < 10_000,
                "waiter did not use the configured ${timeoutMillis}ms deadline (${elapsedMillis}ms)",
            )
        } finally {
            probe.close()
        }
    }

    @Test
    fun `the default 20 second timeout is passed to JDA on a successful connection`() {
        val probe = VoiceTestProbe(testGuildId) {}
        try {
            val fixture = connectedFixture(cleanupSucceeds = true)
            var tone: ToneSendHandler? = null
            every { fixture.audioManager.setSendingHandler(any<AudioSendHandler>()) } answers {
                val handler = firstArg<AudioSendHandler>()
                if (handler is ToneSendHandler) {
                    tone = handler
                    while (handler.canProvide()) {
                        handler.provide20MsAudio()
                    }
                }
            }
            every { fixture.audioManager.setSendingHandler(null) } returns Unit

            val outcome = probe.connectAndPlay(fixture.guild, fixture.channel)

            assertEquals(ProbeOutcome.Success, outcome)
            verify(exactly = 1) {
                fixture.audioManager.setConnectTimeout(SpikeConfig.DEFAULT_VOICE_CONNECT_TIMEOUT_MILLIS)
            }
            assertEquals(75, tone?.framesProvided, "the probe must hand the whole tone to JDA")
        } finally {
            probe.close()
        }
    }

    @Test
    fun `an unexpected disconnect after CONNECTED fails instead of reporting success`() {
        val probe = VoiceTestProbe(testGuildId) {}
        try {
            val fixture = connectedFixture(cleanupSucceeds = true, disconnectAfterConnected = true)

            val outcome = probe.connectAndPlay(fixture.guild, fixture.channel)

            // The disconnect arrives before the tone starts, so the probe must fail with the
            // precise reason and must still close the connection during cleanup.
            assertFailureContains(outcome, "lost after CONNECTED")
            verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
        } finally {
            probe.close()
        }
    }

    @Test
    fun `cleanup records DISCONNECTING before the public close and DISCONNECTED after the status`() {
        val probe = VoiceTestProbe(testGuildId) {}
        try {
            val fixture = connectedFixture(cleanupSucceeds = true)
            val waiter = VoiceTestProbe.ConnectionWaiter()
            waiter.markRequested()
            waiter.onStatusChange(ConnectionStatus.CONNECTED)
            waiter.awaitConnected(1, TimeUnit.SECONDS)
            assertEquals(VoiceTestProbe.ProbeConnectionState.CONNECTED, waiter.connectionState())

            val stateAtClose = AtomicReference<VoiceTestProbe.ProbeConnectionState?>(null)
            every { fixture.audioManager.closeAudioConnection() } answers {
                stateAtClose.set(waiter.connectionState())
                waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)
            }

            val clean = probe.cleanup(fixture.audioManager, waiter, null)

            assertTrue(clean)
            assertEquals(
                VoiceTestProbe.ProbeConnectionState.DISCONNECTING,
                stateAtClose.get(),
                "the local disconnect intent must be recorded before AudioManager.closeAudioConnection()",
            )
            assertEquals(VoiceTestProbe.ProbeConnectionState.DISCONNECTED, waiter.connectionState())
            assertNull(
                waiter.terminalMessage(),
                "the requested end state must not be reported as an unexpected loss",
            )
            waiter.checkTerminal()
        } finally {
            probe.close()
        }
    }

    @Test
    fun `cleanup awaits its fresh request instead of a previously completed one`() {
        val probe = VoiceTestProbe(testGuildId) {}
        try {
            val fixture = connectedFixture(cleanupSucceeds = true)
            val waiter = VoiceTestProbe.ConnectionWaiter()
            waiter.markRequested()
            waiter.onStatusChange(ConnectionStatus.CONNECTED)
            waiter.awaitConnected(1, TimeUnit.SECONDS)

            // A previous request completes. If cleanup awaited a shared status future
            // instead of its own fresh request, this completed future would make cleanup
            // report success even though no new NOT_CONNECTED arrives.
            val staleRequest = waiter.expectDisconnect()
            waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)
            assertTrue(staleRequest.await(1, TimeUnit.SECONDS))
            assertEquals(VoiceTestProbe.ProbeConnectionState.DISCONNECTED, waiter.connectionState())

            // Return to a connected application state so cleanup takes its disconnect
            // branch and creates a new request.
            waiter.markRequested()
            waiter.onStatusChange(ConnectionStatus.CONNECTED)
            assertEquals(VoiceTestProbe.ProbeConnectionState.CONNECTED, waiter.connectionState())

            // The close deliberately publishes no NOT_CONNECTED. Interrupting the await
            // exercises the unconfirmed branch without waiting for the five-second deadline;
            // the stale request is already complete, so only a fresh request can make this
            // return true.
            every { fixture.audioManager.closeAudioConnection() } answers {
                Thread.currentThread().interrupt()
            }

            val clean = try {
                probe.cleanup(fixture.audioManager, waiter, null)
            } finally {
                Thread.interrupted()
            }

            assertFalse(clean, "cleanup must not reuse the completed previous request")
            assertEquals(VoiceTestProbe.ProbeConnectionState.DISCONNECTING, waiter.connectionState())
            assertTrue(staleRequest.await(1, TimeUnit.MILLISECONDS))
            verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
        } finally {
            Thread.interrupted()
            probe.close()
        }
    }

    @Test
    fun `an unexpected NOT_CONNECTED while CONNECTING fails the probe and is never a success`() {
        val probe = VoiceTestProbe(testGuildId) {}
        try {
            val fixture = pendingFixture(clearVoiceState = true)
            every { fixture.audioManager.openAudioConnection(any()) } answers {
                fixture.listener.get()?.onStatusChange(ConnectionStatus.NOT_CONNECTED)
            }

            val outcome = probe.connectAndPlay(fixture.guild, fixture.channel)

            assertFailureContains(outcome, "closed before it reached CONNECTED")
            verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
        } finally {
            probe.close()
        }
    }

    @Test
    fun `the frame-count success gate rejects a tone that was not fully handed to JDA`() {
        val probe = VoiceTestProbe(testGuildId) {}
        mockkConstructor(ToneSendHandler::class)
        try {
            val fixture = connectedFixture(cleanupSucceeds = true)
            every { fixture.audioManager.setSendingHandler(any<AudioSendHandler>()) } returns Unit
            every { anyConstructed<ToneSendHandler>().awaitFinished(any(), any()) } returns Unit
            every { anyConstructed<ToneSendHandler>().totalFrames } returns 75
            every { anyConstructed<ToneSendHandler>().framesProvided } returns 74

            val outcome = probe.connectAndPlay(fixture.guild, fixture.channel)

            assertFailureContains(outcome, "Only 74/75 tone frames")
            verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
        } finally {
            unmockkConstructor(ToneSendHandler::class)
            probe.close()
        }
    }

    @Test
    fun `the success message names the DAVE and audible evidence that unit tests cannot prove`() {
        val message = ProbeOutcome.Success.userMessage

        assertContains(message, "DAVE")
        assertContains(message, "audible")
        assertContains(message, "cannot attribute")
    }

    @Test
    fun `cleanup failure downgrades a tentative success to a failure`() {
        val probe = VoiceTestProbe(testGuildId) {}
        try {
            val fixture = connectedFixture(cleanupSucceeds = false)
            every { fixture.audioManager.setSendingHandler(any<AudioSendHandler>()) } answers {
                val handler = firstArg<AudioSendHandler>()
                if (handler is ToneSendHandler) {
                    while (handler.canProvide()) {
                        handler.provide20MsAudio()
                    }
                }
            }
            every { fixture.audioManager.setSendingHandler(null) } returns Unit

            val outcome = probe.connectAndPlay(fixture.guild, fixture.channel)

            assertFailureContains(outcome, "not cleanly closed")
            verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
            verify(exactly = 1) { fixture.audioManager.setAutoReconnect(true) }
        } finally {
            probe.close()
        }
    }

    @Test
    fun `pending request cleanup succeeds when the gateway voice state is observed then cleared`() {
        val probe = VoiceTestProbe(testGuildId) {}
        try {
            val fixture = pendingFixture(clearVoiceState = true)
            val waiter = VoiceTestProbe.ConnectionWaiter()
            waiter.markRequested()

            val clean = probe.cleanup(fixture.audioManager, waiter, null)

            assertTrue(clean, "an observed-then-cleared voice state must be a clean cancellation")
            verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
        } finally {
            probe.close()
        }
    }

    @Test
    fun `pending request cleanup fails when cancellation cannot be confirmed`() {
        val probe = VoiceTestProbe(testGuildId) {}
        val fixture = pendingFixture(clearVoiceState = false, interruptVoiceStateReads = true)
        try {
            val waiter = VoiceTestProbe.ConnectionWaiter()
            waiter.markRequested()

            // The fixture interrupts the polling thread on the first voice-state read, so
            // awaitSelfVoiceStateCleared() exits through its InterruptedException branch
            // without a real five-second wait.
            val clean = try {
                probe.cleanup(fixture.audioManager, waiter, null)
            } finally {
                Thread.interrupted()
            }

            assertFalse(clean, "an unconfirmed cancellation must not be reported as clean")
            verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
        } finally {
            Thread.interrupted()
            probe.close()
        }
    }

    @Test
    fun `cleanup with no pending request and no connection is a safe no-op`() {
        val probe = VoiceTestProbe(testGuildId) {}
        try {
            val audioManager = mockk<AudioManager>()
            every { audioManager.setSendingHandler(null) } returns Unit
            every { audioManager.setConnectionListener(null) } returns Unit
            every { audioManager.setAutoReconnect(true) } returns Unit
            every { audioManager.isConnected } returns false
            every { audioManager.connectionStatus } returns ConnectionStatus.NOT_CONNECTED

            val waiter = VoiceTestProbe.ConnectionWaiter()

            val clean = probe.cleanup(audioManager, waiter, null)

            assertTrue(clean)
            verify(exactly = 0) { audioManager.closeAudioConnection() }
        } finally {
            probe.close()
        }
    }

    @Test
    fun `terminal connection statuses surface their reason in the probe outcome`() {
        val statuses = listOf(
            ConnectionStatus.DISCONNECTED_AUTHENTICATION_FAILURE,
            ConnectionStatus.AUDIO_REGION_CHANGE,
            ConnectionStatus.SHUTTING_DOWN,
            ConnectionStatus.ERROR_CONNECTION_TIMEOUT,
            ConnectionStatus.ERROR_LOST_CONNECTION,
        )

        statuses.forEach { status ->
            val probe = VoiceTestProbe(testGuildId) {}
            try {
                val fixture = pendingFixture(clearVoiceState = true)
                every { fixture.audioManager.openAudioConnection(any()) } answers {
                    fixture.listener.get()?.onStatusChange(status)
                }

                val outcome = probe.connectAndPlay(fixture.guild, fixture.channel)

                val failure = assertIs<ProbeOutcome.Failure>(outcome, "status=$status")
                assertContains(failure.userMessage, status.name, message = "status=$status")
            } finally {
                probe.close()
            }
        }
    }

    @Test
    fun `an insufficient voice permission is reported with the missing permission`() {
        val probe = VoiceTestProbe(testGuildId) {}
        try {
            val fixture = pendingFixture(clearVoiceState = true)
            every { fixture.audioManager.openAudioConnection(any()) } throws
                InsufficientPermissionException(fixture.guild, Permission.VOICE_CONNECT)

            val outcome = probe.connectAndPlay(fixture.guild, fixture.channel)

            assertFailureContains(outcome, "missing permission VOICE_CONNECT")
            verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
        } finally {
            probe.close()
        }
    }

    @Test
    fun `cleanup racing a parked unexpected NOT_CONNECTED keeps the failure and never reports Success`() {
        val probe = VoiceTestProbe(testGuildId) {}
        try {
            val fixture = connectedFixture(cleanupSucceeds = true)
            val barrier = PausingNotConnectedBarrier()
            val waiter = VoiceTestProbe.ConnectionWaiter(barrier)
            waiter.markRequested()
            waiter.onStatusChange(ConnectionStatus.CONNECTED)
            waiter.awaitConnected(1, TimeUnit.SECONDS)
            waiter.markPlaying()
            assertEquals(VoiceTestProbe.ProbeConnectionState.PLAYING, waiter.connectionState())

            // connectAndPlay registers the waiter as JDA's listener before opening; do the
            // same so closeAudioConnection() can deliver the NOT_CONNECTED callback.
            fixture.audioManager.setConnectionListener(waiter)

            val closeCalled = AtomicBoolean(false)
            every { fixture.audioManager.closeAudioConnection() } answers {
                closeCalled.set(true)
                fixture.listener.get()?.onStatusChange(ConnectionStatus.NOT_CONNECTED)
            }

            // The gateway drops the connection while the tone is playing. The callback
            // snapshots PLAYING and parks while holding the waiter state lock.
            val callbackFailure = AtomicReference<Throwable?>(null)
            val callback = Thread {
                try {
                    waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)
                } catch (t: Throwable) {
                    callbackFailure.set(t)
                }
            }

            // Cleanup reaches expectDisconnect() on another thread and must block there until
            // the callback has recorded the terminal failure and released the lock.
            val cleanupResult = AtomicReference<Boolean?>(null)
            val cleanupReturned = CountDownLatch(1)
            val cleanup = Thread {
                cleanupResult.set(probe.cleanup(fixture.audioManager, waiter, null))
                cleanupReturned.countDown()
            }

            callback.start()
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS), "callback did not reach the barrier")

                cleanup.start()
                try {
                    assertFalse(
                        cleanupReturned.await(200, TimeUnit.MILLISECONDS),
                        "cleanup must block in expectDisconnect() while the callback holds the lock",
                    )
                    assertFalse(closeCalled.get(), "cleanup must not close before expectDisconnect() returns")
                    assertEquals(VoiceTestProbe.ProbeConnectionState.PLAYING, waiter.connectionState())
                    assertNull(waiter.terminalMessage())
                } finally {
                    barrier.release.countDown()
                }

                joinAllBounded(callback, cleanup)
                assertFalse(callback.isAlive, "callback thread did not finish")
                assertFalse(cleanup.isAlive, "cleanup thread did not finish")
                assertNull(callbackFailure.get(), "the callback itself must not throw")

                // Cleanup still reaches the requested disconnected state, but the original
                // unexpected failure recorded before its transition is preserved first-wins.
                assertEquals(true, cleanupResult.get(), "cleanup should still confirm the requested close")
                assertTrue(closeCalled.get(), "cleanup must call JDA's close after recording intent")
                assertEquals(VoiceTestProbe.ProbeConnectionState.DISCONNECTED, waiter.connectionState())
                assertContains(waiter.terminalMessage().orEmpty(), "lost after CONNECTED")
                val terminalFailure = assertFailsWith<VoiceProbeException> { waiter.checkTerminal() }
                assertContains(terminalFailure.message.orEmpty(), "NOT_CONNECTED")

                // Mirror connectAndPlay's finally gate: a tentative Success is downgraded whenever
                // a terminal reason was recorded, so this race can never report Success.
                val outcome: ProbeOutcome = waiter.terminalMessage()
                    ?.let { ProbeOutcome.Failure(it) }
                    ?: ProbeOutcome.Success
                val failure = assertIs<ProbeOutcome.Failure>(
                    outcome,
                    "a run whose connection was lost before cleanup must never report Success",
                )
                assertContains(failure.userMessage, "lost after CONNECTED")
                verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
            } finally {
                // A failed await or assertion must never leave the parked callback or the cleanup
                // worker blocked: release the barrier and join every spawned thread on every path.
                barrier.release.countDown()
                joinAllBounded(callback, cleanup)
            }
        } finally {
            probe.close()
        }
    }

    /**
     * A manager whose connection request was queued but never reached CONNECTED.
     *
     * `clearVoiceState = true` models the gateway reporting the bot in the channel and
     * then removing it, so `awaitSelfVoiceStateCleared` succeeds. `clearVoiceState = false`
     * keeps the bot invisible and, when [interruptVoiceStateReads] is set, interrupts the
     * polling thread so the unconfirmed branch returns without the real 5 s deadline.
     */
    private fun pendingFixture(
        clearVoiceState: Boolean,
        interruptVoiceStateReads: Boolean = false,
    ): AudioFixture {
        val channel = voiceChannel()
        val guild = mockk<Guild>()
        val audioManager = mockk<AudioManager>()
        val member = mockk<SelfMember>()
        val voiceState = mockk<GuildVoiceState>()
        val listener = AtomicReference<ConnectionListener?>(null)

        every { guild.id } returns testGuildId.toString()
        every { guild.idLong } returns testGuildId
        every { guild.audioManager } returns audioManager

        stubAudioManager(audioManager, guild, listener)

        every { audioManager.isConnected } returns false
        every { audioManager.connectionStatus } returns ConnectionStatus.NOT_CONNECTED
        every { audioManager.openAudioConnection(any()) } returns Unit

        every { guild.selfMember } returns member
        if (interruptVoiceStateReads) {
            every { member.voiceState } answers {
                Thread.currentThread().interrupt()
                voiceState
            }
        } else {
            every { member.voiceState } returns voiceState
        }
        if (clearVoiceState) {
            val channelInVoiceState = mockk<AudioChannelUnion>()
            every { voiceState.channel } returnsMany listOf(channelInVoiceState, null)
        } else {
            every { voiceState.channel } returns null
        }

        return AudioFixture(guild, audioManager, channel, listener)
    }

    /**
     * A manager that has already reached CONNECTED and has a disconnect status queued
     * behind `closeAudioConnection()`.
     *
     * When [cleanupSucceeds] is false the final `setAutoReconnect(true)` throws, which is
     * the last step cleanup performs and therefore forces `cleanup` to return false.
     *
     * When [disconnectAfterConnected] is true the gateway drops the connection right after
     * CONNECTED, before any tone frame is handed to JDA.
     */
    private fun connectedFixture(
        cleanupSucceeds: Boolean,
        disconnectAfterConnected: Boolean = false,
    ): AudioFixture {
        val channel = voiceChannel()
        val guild = mockk<Guild>()
        val audioManager = mockk<AudioManager>()
        val listener = AtomicReference<ConnectionListener?>(null)

        every { guild.id } returns testGuildId.toString()
        every { guild.idLong } returns testGuildId
        every { guild.audioManager } returns audioManager

        stubAudioManager(audioManager, guild, listener)

        every { audioManager.isConnected } returns true
        every { audioManager.connectionStatus } returns ConnectionStatus.CONNECTED
        every { audioManager.setAutoReconnect(false) } returns Unit
        if (cleanupSucceeds) {
            every { audioManager.setAutoReconnect(true) } returns Unit
        } else {
            every { audioManager.setAutoReconnect(true) } throws
                IllegalStateException("auto-reconnect could not be restored")
        }
        every { audioManager.openAudioConnection(any()) } answers {
            val currentListener = listener.get()
            currentListener?.onStatusChange(ConnectionStatus.CONNECTED)
            if (disconnectAfterConnected) {
                currentListener?.onStatusChange(ConnectionStatus.NOT_CONNECTED)
            }
        }
        every { audioManager.closeAudioConnection() } answers {
            listener.get()?.onStatusChange(ConnectionStatus.NOT_CONNECTED)
        }

        return AudioFixture(guild, audioManager, channel, listener)
    }

    private fun stubAudioManager(
        audioManager: AudioManager,
        guild: Guild,
        listener: AtomicReference<ConnectionListener?>,
    ) {
        every { audioManager.setConnectionListener(any()) } answers {
            listener.set(firstArg())
        }
        every { audioManager.setConnectionListener(null) } returns Unit
        every { audioManager.setConnectTimeout(any()) } returns Unit
        every { audioManager.setAutoReconnect(any()) } returns Unit
        every { audioManager.setSendingHandler(null) } returns Unit
        every { audioManager.guild } returns guild
        every { audioManager.closeAudioConnection() } returns Unit
    }

    private fun voiceChannel(): VoiceChannel {
        val channel = mockk<VoiceChannel>()
        every { channel.name } returns "staging-voice"
        every { channel.id } returns "987654321098765432"
        return channel
    }

    private fun assertFailureContains(outcome: ProbeOutcome, fragment: String) {
        val failure = assertIs<ProbeOutcome.Failure>(outcome)
        assertContains(failure.userMessage, fragment)
    }

    /**
     * Joins every spawned thread with a bounded wait. A thread that is still alive after the
     * bound is a test failure, so a failed assertion cannot leak a parked callback into the
     * next test or leave it blocked for the barrier's full deadline.
     */
    private fun joinAllBounded(vararg threads: Thread) {
        threads.forEach { it.join(THREAD_JOIN_TIMEOUT_MILLIS) }
        val alive = threads.filter { it.isAlive }
        assertTrue(
            alive.isEmpty(),
            "threads did not finish within ${THREAD_JOIN_TIMEOUT_MILLIS}ms: " +
                alive.joinToString { it.name },
        )
    }

    /**
     * Pauses the first NOT_CONNECTED callback after the production callback snapshot its
     * application-owned state, while the waiter state lock is held. Tests must release
     * [release] and must not call a lock-taking waiter method from the barrier itself.
     */
    private class PausingNotConnectedBarrier :
        VoiceTestProbe.ConnectionWaiter.StatusCallbackBarrier {
        private val paused = AtomicBoolean(false)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)

        override fun afterEntrySnapshot(status: ConnectionStatus) {
            if (status == ConnectionStatus.NOT_CONNECTED && paused.compareAndSet(false, true)) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS)) {
                    "the test did not release the NOT_CONNECTED callback barrier"
                }
            }
        }
    }

    private class AudioFixture(
        val guild: Guild,
        val audioManager: AudioManager,
        val channel: VoiceChannel,
        val listener: AtomicReference<ConnectionListener?>,
    )

    private companion object {
        private const val THREAD_JOIN_TIMEOUT_MILLIS = 5_000L
    }
}
