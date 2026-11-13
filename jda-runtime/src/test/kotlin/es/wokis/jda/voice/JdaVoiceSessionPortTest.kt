package es.wokis.jda.voice

import es.wokis.commands.neutral.CommandContext
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import es.wokis.voice.AudioFrame
import es.wokis.voice.AudioFrameFormat
import es.wokis.voice.AudioFrameSource
import es.wokis.voice.VoiceFailureReason
import es.wokis.voice.VoicePlaybackResult
import es.wokis.voice.VoiceRequestRejection
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.audio.AudioSendHandler
import net.dv8tion.jda.api.audio.hooks.ConnectionListener
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.GuildVoiceState
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.SelfMember
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.managers.AudioManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertIs

class JdaVoiceSessionPortTest {

    @Test
    fun `Given a user in a regular voice channel When playing Then complete with every frame`() = runBlocking {
        // Given
        val fixture = Fixture()
        val source = FakeFrameSource(actualFrames = 3)

        // When
        val result = fixture.port().play(fixture.context, source)

        // Then
        assertEquals(VoicePlaybackResult.Completed(framesProvided = 3, expectedFrames = 3), result)
        // The send loop probes the source once more to observe exhaustion, hence four calls.
        assertEquals(4, source.nextFrameCalls.get())
        assertEquals(0, source.closeCalls.get(), "the session must never close the caller-owned source")
        assertTrue(fixture.events.contains("open"))
        assertTrue(fixture.events.contains("close"))
    }

    @Test
    fun `Given the source ends early When playing Then report an incomplete playback`() = runBlocking {
        // Given
        val fixture = Fixture()
        val source = FakeFrameSource(actualFrames = 2, expectedFrames = 5)

        // When
        val result = fixture.port().play(fixture.context, source)

        // Then
        assertEquals(
            VoicePlaybackResult.Failed(VoiceFailureReason.INCOMPLETE_PLAYBACK),
            result,
        )
    }

    @Test
    fun `Given the send loop consumes nothing When playing Then report a tone timeout`() = runBlocking {
        // Given
        val fixture = Fixture(drainFrames = false)

        // When
        val result = fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1))

        // Then
        assertEquals(VoicePlaybackResult.Failed(VoiceFailureReason.TONE_TIMEOUT), result)
        assertTrue(fixture.events.contains("close"))
    }

    @Test
    fun `Given the connection never becomes connected When playing Then report a connect timeout`() = runBlocking {
        // Given
        val fixture = Fixture()
        every { fixture.audioManager.openAudioConnection(any()) } answers {
            fixture.events += "open"
        }

        // When
        val result = fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1))

        // Then
        assertEquals(VoicePlaybackResult.Failed(VoiceFailureReason.CONNECT_TIMEOUT), result)
        assertFalse(fixture.events.contains("handler:set"))
        assertTrue(fixture.events.contains("close"), "a queued connection must be cancelled on timeout")
        assertTrue(fixture.events.indexOf("close") < fixture.events.indexOf("listener:clear"))
    }

    @Test
    fun `Given the bot is missing permission When opening the connection Then map to missing permission`() = runBlocking {
        // Given: a real exception, so the adapter can attach cleanup failures to it.
        val fixture = Fixture()
        val permissionError = InsufficientPermissionException(fixture.guild, Permission.VOICE_CONNECT)
        every { fixture.audioManager.openAudioConnection(any()) } answers {
            fixture.events += "open"
            throw permissionError
        }

        // When
        val result = fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1))

        // Then: the primary failure is preserved and the cleanup failure is attached to it.
        assertEquals(VoicePlaybackResult.Failed(VoiceFailureReason.MISSING_PERMISSION), result)
        assertTrue(fixture.events.contains("close"))
        val cleanupFailure = assertIs<VoiceConnectionException>(permissionError.suppressed.single())
        assertTrue(cleanupFailure.message.orEmpty().contains("not confirmed"))
    }

    @Test
    fun `Given the connection is lost after connecting When playing Then map to connection lost`() = runBlocking {
        // Given
        val fixture = Fixture()
        every { fixture.audioManager.openAudioConnection(any()) } answers {
            fixture.events += "open"
            fixture.connected = true
            fixture.listener?.onStatusChange(ConnectionStatus.CONNECTED)
            fixture.listener?.onStatusChange(ConnectionStatus.AUDIO_REGION_CHANGE)
        }

        // When
        val result = fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1))

        // Then
        assertEquals(VoicePlaybackResult.Failed(VoiceFailureReason.CONNECTION_LOST), result)
        assertFalse(fixture.events.contains("handler:set"), "the send handler must not be installed after a loss")
    }

    @Test
    fun `Given a successful playback When cleanup runs Then invalidate detach close and restore in that order`() = runBlocking {
        // Given
        val fixture = Fixture()

        // When
        fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1))

        // Then
        val cleanup = fixture.events.dropWhile { it != "handler:set" }.drop(1)
        assertEquals(
            listOf(
                "handler:closed-before-detach=true",
                "handler:null",
                "close",
                "listener:clear",
                "autoReconnect:true",
                "connectTimeout:5000",
                "handler:null",
            ),
            cleanup,
            "cleanup must invalidate the handler, detach it, close the connection and restore manager state",
        )
        assertTrue(
            fixture.events.indexOf("handler:closed-before-detach=true") <
                fixture.events.indexOf("handler:null"),
            "the handler must be closed before it is detached from the audio manager: ${fixture.events}",
        )
        assertTrue(fixture.events.contains("autoReconnect:false"))
        assertTrue(fixture.events.contains("connectTimeout:100"))
    }

    @Test
    fun `Given the sender cannot be cleared When a completed playback ends Then report a cleanup failure`() = runBlocking {
        // Given
        val fixture = Fixture(failClearHandler = true)

        // When
        val result = fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1))

        // Then: the disconnect is confirmed, so only the remaining cleanup step failed.
        assertEquals(
            VoicePlaybackResult.Failed(VoiceFailureReason.CLEANUP_FAILED),
            result,
        )
        assertTrue(fixture.events.contains("close"), "the connection must still be closed")
        assertTrue(fixture.events.contains("listener:clear"), "the manager must still be restored")
    }

    @Test
    fun `Given the sender cannot be cleared When the playback is incomplete Then preserve the playback failure`() =
        runBlocking {
            // Given
            val fixture = Fixture(failClearHandler = true)

            // When
            val result = fixture.port().play(
                fixture.context,
                FakeFrameSource(actualFrames = 2, expectedFrames = 5),
            )

            // Then: a confirmed cleanup failure must not replace the primary playback failure.
            assertEquals(VoicePlaybackResult.Failed(VoiceFailureReason.INCOMPLETE_PLAYBACK), result)
            assertTrue(fixture.events.contains("close"))
            assertTrue(fixture.events.contains("listener:clear"))
        }

    @Test
    fun `Given a direct message context When playing Then reject without resolving a guild`() = runBlocking {
        // Given
        val fixture = Fixture()
        val source = FakeFrameSource(actualFrames = 1)

        // When
        val result = fixture.port().play(fixture.context.copy(guildId = null), source)

        // Then
        assertEquals(VoicePlaybackResult.Rejected(VoiceRequestRejection.DIRECT_MESSAGE), result)
        verify(exactly = 0) { fixture.jda.getGuildById(any<Long>()) }
        assertEquals(0, source.closeCalls.get())
    }

    @Test
    fun `Given an unavailable guild When playing Then reject as another guild`() = runBlocking {
        // Given
        val fixture = Fixture()
        every { fixture.jda.getGuildById(100L) } returns null

        // When
        val result = fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1))

        // Then
        assertEquals(VoicePlaybackResult.Rejected(VoiceRequestRejection.OTHER_GUILD), result)
        assertFalse(fixture.events.contains("open"))
    }

    @Test
    fun `Given the invoking member is not cached When playing Then reject as not in voice`() = runBlocking {
        // Given
        val fixture = Fixture()
        every { fixture.guild.getMemberById(300L) } returns null

        // When
        val result = fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1))

        // Then
        assertEquals(VoicePlaybackResult.Rejected(VoiceRequestRejection.USER_NOT_IN_VOICE), result)
        assertFalse(fixture.events.contains("open"))
    }

    @Test
    fun `Given the member has no voice state When playing Then reject as not in voice`() = runBlocking {
        // Given
        val fixture = Fixture()
        every { fixture.member.voiceState } returns null

        // When
        val result = fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1))

        // Then
        assertEquals(VoicePlaybackResult.Rejected(VoiceRequestRejection.USER_NOT_IN_VOICE), result)
    }

    @Test
    fun `Given the member is connected to no channel When playing Then reject as not in voice`() = runBlocking {
        // Given
        val fixture = Fixture()
        every { fixture.voiceState.channel } returns null

        // When
        val result = fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1))

        // Then
        assertEquals(VoicePlaybackResult.Rejected(VoiceRequestRejection.USER_NOT_IN_VOICE), result)
    }

    @Test
    fun `Given the member is in a stage channel When playing Then reject as a stage channel`() = runBlocking {
        // Given
        val fixture = Fixture()
        val stageChannel = mockk<StageChannel>(null, false, AudioChannelUnion::class)
        every { fixture.voiceState.channel } returns (stageChannel as AudioChannelUnion)

        // When
        val result = fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1))

        // Then
        assertEquals(VoicePlaybackResult.Rejected(VoiceRequestRejection.STAGE_CHANNEL), result)
        assertFalse(fixture.events.contains("open"))
    }

    @Test
    fun `Given the voice channel belongs to another guild When playing Then reject as another guild`() = runBlocking {
        // Given
        val fixture = Fixture()
        val otherGuild = mockk<Guild>()
        every { fixture.voiceChannel.guild } returns otherGuild
        every { otherGuild.idLong } returns 999L

        // When
        val result = fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1))

        // Then
        assertEquals(VoicePlaybackResult.Rejected(VoiceRequestRejection.OTHER_GUILD), result)
        assertFalse(fixture.events.contains("open"))
    }

    @Test
    fun `Given the audio manager already has a connected session When playing Then reject before touching it`() =
        runBlocking {
            // Given: a pre-existing session with its own listener and send handler.
            val fixture = Fixture()
            // Strict mocks: the active-session guard must reject before the pre-existing listener
            // or send handler is used, so every member they could be exercised through records an
            // interaction that the assertions below forbid.
            val existingListener = mockk<ConnectionListener>()
            val existingHandler = mockk<AudioSendHandler>()
            every { existingListener.onStatusChange(any()) } answers {
                fixture.events += "existing:listener:status"
            }
            every { existingHandler.canProvide() } answers {
                fixture.events += "existing:handler:canProvide"
                false
            }
            every { existingHandler.provide20MsAudio() } answers {
                fixture.events += "existing:handler:provide"
                null
            }
            fixture.connected = true
            fixture.listener = existingListener
            every { fixture.audioManager.connectionListener } returns existingListener
            every { fixture.audioManager.sendingHandler } returns existingHandler
            val source = FakeFrameSource(actualFrames = 1)

            // When
            val result = fixture.port().play(fixture.context, source)

            // Then
            assertEquals(
                VoicePlaybackResult.Rejected(VoiceRequestRejection.VOICE_SESSION_ALREADY_ACTIVE),
                result,
            )
            assertTrue(fixture.events.isEmpty(), "the existing session must not be touched: ${fixture.events}")
            verify(exactly = 0) { fixture.audioManager.setConnectionListener(any()) }
            verify(exactly = 0) { fixture.audioManager.setSendingHandler(any()) }
            verify(exactly = 0) { fixture.audioManager.setAutoReconnect(any()) }
            verify(exactly = 0) { fixture.audioManager.setConnectTimeout(any()) }
            verify(exactly = 0) { fixture.audioManager.openAudioConnection(any()) }
            verify(exactly = 0) { fixture.audioManager.closeAudioConnection() }
            verify(exactly = 0) { existingListener.onStatusChange(any()) }
            verify(exactly = 0) { existingHandler.canProvide() }
            verify(exactly = 0) { existingHandler.provide20MsAudio() }
            assertTrue(fixture.connected, "the pre-existing session must stay connected")
            assertSame(existingListener, fixture.listener, "the pre-existing listener must be preserved")
            assertEquals(
                existingHandler,
                fixture.audioManager.sendingHandler,
                "the pre-existing sender must be preserved",
            )
            assertEquals(0, source.closeCalls.get(), "the caller-owned source is never closed by the adapter")
        }

    @Test
    fun `Given the audio manager is still connecting When playing Then reject before mutating the manager`() =
        runBlocking {
            // Given: JDA 6.7 creates its AudioConnection before the websocket finishes connecting, so
            // isConnected may already be true while the status is CONNECTING_*.
            val fixture = Fixture()
            // Strict mocks: rejecting a connecting session must not reach its listener or sender,
            // so their stubs only exist to record an interaction the assertions below forbid.
            val existingListener = mockk<ConnectionListener>()
            val existingHandler = mockk<AudioSendHandler>()
            every { existingListener.onStatusChange(any()) } answers {
                fixture.events += "existing:listener:status"
            }
            every { existingHandler.canProvide() } answers {
                fixture.events += "existing:handler:canProvide"
                false
            }
            every { existingHandler.provide20MsAudio() } answers {
                fixture.events += "existing:handler:provide"
                null
            }
            fixture.listener = existingListener
            every { fixture.audioManager.isConnected } returns false
            every { fixture.audioManager.connectionStatus } returns
                ConnectionStatus.CONNECTING_AWAITING_WEBSOCKET_CONNECT
            every { fixture.audioManager.connectionListener } returns existingListener
            every { fixture.audioManager.sendingHandler } returns existingHandler
            val source = FakeFrameSource(actualFrames = 1)

            // When
            val result = fixture.port().play(fixture.context, source)

            // Then
            assertEquals(
                VoicePlaybackResult.Rejected(VoiceRequestRejection.VOICE_SESSION_ALREADY_ACTIVE),
                result,
            )
            assertTrue(fixture.events.isEmpty(), "the connecting session must not be touched: ${fixture.events}")
            verify(exactly = 0) { fixture.audioManager.setConnectionListener(any()) }
            verify(exactly = 0) { fixture.audioManager.setSendingHandler(any()) }
            verify(exactly = 0) { fixture.audioManager.setAutoReconnect(any()) }
            verify(exactly = 0) { fixture.audioManager.setConnectTimeout(any()) }
            verify(exactly = 0) { fixture.audioManager.openAudioConnection(any()) }
            verify(exactly = 0) { fixture.audioManager.closeAudioConnection() }
            verify(exactly = 0) { existingListener.onStatusChange(any()) }
            verify(exactly = 0) { existingHandler.canProvide() }
            verify(exactly = 0) { existingHandler.provide20MsAudio() }
            assertEquals(
                ConnectionStatus.CONNECTING_AWAITING_WEBSOCKET_CONNECT,
                fixture.audioManager.connectionStatus,
                "the connecting session must keep its status",
            )
            assertSame(existingListener, fixture.listener, "the pre-existing listener must be preserved")
            assertEquals(
                existingHandler,
                fixture.audioManager.sendingHandler,
                "the pre-existing sender must be preserved",
            )
            assertEquals(0, source.closeCalls.get(), "the caller-owned source is never closed by the adapter")
        }

    @Test
    fun `Given an active session rejection When the session ends Then a later playback proceeds`() = runBlocking {
        // Given
        val fixture = Fixture()
        fixture.connected = true
        assertEquals(
            VoicePlaybackResult.Rejected(VoiceRequestRejection.VOICE_SESSION_ALREADY_ACTIVE),
            fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1)),
        )

        // When: the external session ends and the port is used again.
        fixture.connected = false
        val result = fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1))

        // Then: the guard is per-session and does not leave the port latched as active.
        assertEquals(VoicePlaybackResult.Completed(framesProvided = 1, expectedFrames = 1), result)
        assertTrue(fixture.events.contains("open"), "a later playback must be allowed to connect")
    }

    @Test
    fun `Given a playback is running When a second playback starts Then reject it without touching the manager`() =
        runBlocking {
            // Given
            val fixture = Fixture()
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            every { fixture.audioManager.openAudioConnection(any()) } answers {
                fixture.events += "open"
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS)) { "the test did not release the connection" }
                fixture.connected = true
                fixture.listener?.onStatusChange(ConnectionStatus.CONNECTED)
            }
            val port = fixture.port()
            val first = async(Dispatchers.IO) {
                port.play(fixture.context, FakeFrameSource(actualFrames = 1))
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS), "the first playback did not reach the connection")

            // When
            val second = port.play(fixture.context, FakeFrameSource(actualFrames = 1))

            // Then
            assertEquals(
                VoicePlaybackResult.Rejected(VoiceRequestRejection.PLAYBACK_IN_PROGRESS),
                second,
            )
            release.countDown()
            assertEquals(
                VoicePlaybackResult.Completed(framesProvided = 1, expectedFrames = 1),
                first.await(),
            )
            assertEquals(1, fixture.events.count { it == "open" })
        }

    @Test
    fun `Given an unexpected gateway failure When playing Then map to internal error and still clean up`() = runBlocking {
        // Given
        val fixture = Fixture()
        every { fixture.audioManager.openAudioConnection(any()) } answers {
            fixture.events += "open"
            throw IllegalStateException("unexpected gateway failure")
        }

        // When
        val result = fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1))

        // Then
        assertEquals(VoicePlaybackResult.Failed(VoiceFailureReason.INTERNAL_ERROR), result)
        assertTrue(fixture.events.contains("close"))
        assertTrue(fixture.events.contains("listener:clear"))
    }

    @Test
    fun `Given a queued connection that appears then clears When cancelled Then cleanup is confirmed`() = runBlocking {
        // Given
        val fixture = Fixture()
        every { fixture.audioManager.openAudioConnection(any()) } answers {
            fixture.events += "open"
        }
        val stateReads = AtomicInteger(0)
        every { fixture.selfMember.voiceState } answers {
            if (stateReads.getAndIncrement() == 0) fixture.voiceState else null
        }

        // When
        val result = fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1))

        // Then
        assertEquals(VoicePlaybackResult.Failed(VoiceFailureReason.CONNECT_TIMEOUT), result)
        assertTrue(stateReads.get() >= 2, "cleanup must poll the bot voice state until it clears")
        assertTrue(fixture.events.contains("close"))
    }

    @Test
    fun `Given the frame source fails When playing Then report an internal error`() = runBlocking {
        // Given
        val fixture = Fixture()
        val failing = object : AudioFrameSource {
            override val format: AudioFrameFormat = AudioFrameFormat.OPUS_20MS
            override val expectedFrameCount: Int = 1
            override fun nextFrame(): AudioFrame? = throw IllegalStateException("source boom")
            override fun close() = Unit
        }

        // When
        val result = fixture.port().play(fixture.context, failing)

        // Then
        assertEquals(VoicePlaybackResult.Failed(VoiceFailureReason.INTERNAL_ERROR), result)
        assertTrue(fixture.events.contains("close"))
    }

    @Test
    fun `Given the requested disconnect is not confirmed When cleanup runs Then report an unconfirmed disconnect`() =
        runBlocking {
            // Given
            val fixture = Fixture()
            every { fixture.audioManager.closeAudioConnection() } answers {
                fixture.events += "close"
                fixture.connected = false
            }

            // When
            val result = fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1))

            // Then
            assertEquals(VoicePlaybackResult.Failed(VoiceFailureReason.DISCONNECT_UNCONFIRMED), result)
            assertTrue(fixture.events.contains("close"))
        }

    @Test
    fun `Given an unrecognised audio channel kind When playing Then reject as an unsupported channel`() = runBlocking {
        // Given: the union resolves to neither a regular voice nor a stage channel.
        val fixture = Fixture()
        every { fixture.voiceState.channel } returns mockk<AudioChannelUnion>()

        // When
        val result = fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1))

        // Then
        assertEquals(VoicePlaybackResult.Rejected(VoiceRequestRejection.UNSUPPORTED_CHANNEL), result)
        assertFalse(fixture.events.contains("open"))
        assertFalse(fixture.events.contains("handler:set"))
    }

    @Test
    fun `Given a pending connect When the playback is cancelled Then cleanup runs immediately without the connect timeout`() =
        runBlocking {
            // Given: the production timeout is long, so only cancellation can end the wait quickly.
            val fixture = Fixture(connectTimeoutMillis = 20_000L)
            every { fixture.audioManager.openAudioConnection(any()) } answers {
                fixture.events += "open"
                fixture.opened.complete(Unit)
            }
            val playback = launch(Dispatchers.IO) {
                fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1))
            }
            withTimeout(2_000) { fixture.opened.await() }

            // When
            withTimeout(1_000) { playback.cancelAndJoin() }

            // Then
            assertTrue(playback.isCancelled)
            assertEquals(
                listOf(
                    "handler:null",
                    "close",
                    "listener:clear",
                    "autoReconnect:true",
                    "connectTimeout:5000",
                    "handler:null",
                ),
                fixture.events.dropWhile { it != "handler:null" },
                "cancellation must clear the sender, close the connection and restore the manager",
            )
            assertFalse(fixture.events.contains("handler:set"), "no sender may be installed after cancellation")
        }

    @Test
    fun `Given playback is pending When the playback is cancelled Then cleanup runs immediately without the tone timeout`() =
        runBlocking {
            // Given: the send loop never drains, so only cancellation can end playback.
            val fixture = Fixture(drainFrames = false, toneTimeoutMillis = 20_000L)
            val playback = launch(Dispatchers.IO) {
                fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1))
            }
            withTimeout(2_000) { fixture.handlerInstalled.await() }

            // When
            withTimeout(1_000) { playback.cancelAndJoin() }

            // Then
            assertTrue(playback.isCancelled)
            assertEquals(
                listOf(
                    "handler:closed-before-detach=true",
                    "handler:null",
                    "close",
                    "listener:clear",
                    "autoReconnect:true",
                    "connectTimeout:5000",
                    "handler:null",
                ),
                fixture.events.dropWhile { it != "handler:set" }.drop(1),
                "cancellation must invalidate the handler, detach it, close the connection and restore the manager",
            )
        }

    @Test
    fun `Given a loss while frames are pending When playing Then connection lost wins over the tone timeout`() =
        runBlocking {
            // Given: a 20s tone timeout and a send loop that never completes.
            val fixture = Fixture(drainFrames = false, toneTimeoutMillis = 20_000L)
            val playback = async(Dispatchers.IO) {
                fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1))
            }
            withTimeout(2_000) { fixture.handlerInstalled.await() }

            // When: the connection is lost while the frame completion is still pending.
            val listener = requireNotNull(fixture.listener)
            listener.onStatusChange(ConnectionStatus.AUDIO_REGION_CHANGE)
            val result = withTimeout(1_000) { playback.await() }

            // Then: the terminal signal wins promptly; the tone timeout must not be reported.
            assertEquals(VoicePlaybackResult.Failed(VoiceFailureReason.CONNECTION_LOST), result)
            assertTrue(fixture.events.contains("close"))
        }

    @Test
    fun `Given an incomplete playback When cleanup also fails Then preserve the playback failure`() = runBlocking {
        // Given
        val fixture = Fixture(failClose = true)

        // When
        val result = fixture.port().play(
            fixture.context,
            FakeFrameSource(actualFrames = 2, expectedFrames = 5),
        )

        // Then: a cleanup failure must not replace the primary playback failure.
        assertEquals(VoicePlaybackResult.Failed(VoiceFailureReason.INCOMPLETE_PLAYBACK), result)
        assertTrue(fixture.events.contains("close"))
    }

    @Test
    fun `Given a connect timeout When cleanup also fails Then preserve the connect failure`() = runBlocking {
        // Given: the connection never becomes connected, and clearing the sender also fails.
        val fixture = Fixture(failClearHandler = true)
        every { fixture.audioManager.openAudioConnection(any()) } answers {
            fixture.events += "open"
        }

        // When
        val result = fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1))

        // Then: the primary connect failure is preserved over the secondary cleanup failure.
        assertEquals(VoicePlaybackResult.Failed(VoiceFailureReason.CONNECT_TIMEOUT), result)
        assertTrue(fixture.events.contains("close"))
        assertTrue(fixture.events.contains("listener:clear"))
    }

    @Test
    fun `Given a completed playback When closing the connection fails Then report an unconfirmed disconnect`() =
        runBlocking {
            // Given
            val fixture = Fixture(failClose = true)

            // When
            val result = fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1))

            // Then: playback completed, but cleanup failed, so this is never a clean success.
            assertEquals(VoicePlaybackResult.Failed(VoiceFailureReason.DISCONNECT_UNCONFIRMED), result)
            assertTrue(fixture.events.contains("close"))
            assertTrue(fixture.events.contains("listener:clear"))
        }

    @Test
    fun `Given a completed playback When restoring the manager fails Then report a cleanup failure`() = runBlocking {
        // Given: the disconnect is confirmed, but the audio-manager restore fails.
        val fixture = Fixture(failRestoreListener = true)

        // When
        val result = fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1))

        // Then
        assertEquals(VoicePlaybackResult.Failed(VoiceFailureReason.CLEANUP_FAILED), result)
        assertTrue(fixture.events.contains("close"))
        assertFalse(fixture.events.contains("listener:clear"))
    }

    @Test
    fun `Given a failed connection restore When the playback is incomplete Then preserve the playback failure`() =
        runBlocking {
            // Given: the disconnect is confirmed, but the listener restore fails.
            val fixture = Fixture(failRestoreListener = true)

            // When
            val result = fixture.port().play(
                fixture.context,
                FakeFrameSource(actualFrames = 2, expectedFrames = 5),
            )

            // Then: the primary playback failure wins over the secondary cleanup failure.
            assertEquals(VoicePlaybackResult.Failed(VoiceFailureReason.INCOMPLETE_PLAYBACK), result)
            assertTrue(fixture.events.contains("close"))
            assertFalse(fixture.events.contains("listener:clear"))
        }

    @Test
    fun `Given a completed playback When a cleanup step throws an Error Then rethrow it and still run the remaining steps`() {
        // Given
        val fatal = AssertionError("cannot clear the voice send handler")
        val fixture = Fixture(clearHandlerFailure = fatal)

        // When
        val thrown = assertThrows(AssertionError::class.java) {
            runBlocking { fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1)) }
        }

        // Then: a fatal cleanup failure is never converted into an ordinary result.
        assertEquals(fatal.message, thrown.message)
        assertTrue(fixture.events.contains("close"), "the connection must still be closed")
        assertTrue(fixture.events.contains("listener:clear"), "the listener must still be restored")
        assertTrue(fixture.events.contains("autoReconnect:true"), "auto-reconnect must still be restored")
        assertTrue(fixture.events.contains("connectTimeout:5000"), "the connect timeout must still be restored")
    }

    @Test
    fun `Given a completed playback When a cleanup step throws a cancellation Then rethrow it as a cancellation`() {
        // Given
        val fatal = CancellationException("voice cleanup cancelled")
        val fixture = Fixture(clearHandlerFailure = fatal)

        // When
        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking { fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1)) }
        }

        // Then
        assertEquals(fatal.message, thrown.message)
        assertTrue(fixture.events.contains("close"), "the connection must still be closed")
        assertTrue(fixture.events.contains("listener:clear"), "the listener must still be restored")
        assertTrue(fixture.events.contains("autoReconnect:true"), "auto-reconnect must still be restored")
        assertTrue(fixture.events.contains("connectTimeout:5000"), "the connect timeout must still be restored")
    }

    @Test
    fun `Given a connection loss When cleanup throws an Error Then the Error wins over the playback failure`() =
        runBlocking {
            // Given: the connection is lost after connecting, so a non-fatal primary failure exists.
            val fatal = AssertionError("cannot clear the voice send handler")
            val fixture = Fixture(clearHandlerFailure = fatal)
            every { fixture.audioManager.openAudioConnection(any()) } answers {
                fixture.events += "open"
                fixture.connected = true
                fixture.listener?.onStatusChange(ConnectionStatus.CONNECTED)
                fixture.listener?.onStatusChange(ConnectionStatus.AUDIO_REGION_CHANGE)
            }

            // When
            val thrown = assertThrows(AssertionError::class.java) {
                runBlocking { fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1)) }
            }

            // Then: the fatal cleanup failure wins, so the ordinary playback failure is never returned.
            // The suppressed primary is attached by the production policy, but kotlinx.coroutines
            // stack-trace recovery creates a copy of the thrown throwable at the suspend boundary and
            // does not carry suppressed exceptions, so only the winning failure is observable here.
            assertEquals(fatal.message, thrown.message)
            assertTrue(fixture.events.contains("close"))
            assertTrue(fixture.events.contains("listener:clear"))
        }

    @Test
    fun `Given a cancellation primary failure When cleanup throws an Error Then the cancellation keeps precedence`() =
        runBlocking {
            // Given: the cancellation comes from the gateway call, not from cancelling the caller's
            // job, so the primary failure must still be the one that is rethrown.
            val primaryCancellation = CancellationException("voice playback cancelled")
            val fatal = AssertionError("cannot clear the voice send handler")
            val fixture = Fixture(clearHandlerFailure = fatal)
            every { fixture.audioManager.openAudioConnection(any()) } answers {
                fixture.events += "open"
                throw primaryCancellation
            }

            // When
            val thrown = assertThrows(CancellationException::class.java) {
                runBlocking { fixture.port().play(fixture.context, FakeFrameSource(actualFrames = 1)) }
            }

            // Then: the cancellation is rethrown (never converted into a result) instead of the fatal
            // cleanup failure, and every independent cleanup step was still attempted.
            assertEquals(primaryCancellation.message, thrown.message)
            assertTrue(fixture.events.contains("close"), "the connection must still be closed")
            assertTrue(fixture.events.contains("listener:clear"), "the listener must still be restored")
            assertTrue(fixture.events.contains("autoReconnect:true"), "auto-reconnect must still be restored")
            assertTrue(fixture.events.contains("connectTimeout:5000"), "the connect timeout must still be restored")
        }

    private class Fixture(
        drainFrames: Boolean = true,
        failClearHandler: Boolean = false,
        failClose: Boolean = false,
        failRestoreListener: Boolean = false,
        private val connectTimeoutMillis: Long = 100L,
        private val daveSettleMillis: Long = 0L,
        private val toneTimeoutMillis: Long = 100L,
        private val flushMillis: Long = 0L,
        private val disconnectTimeoutMillis: Long = 50L,
        private val selfVoiceStatePollMillis: Long = 5L,
        private val clearHandlerFailure: Throwable? = null,
    ) {
        val events = mutableListOf<String>()
        val jda: JDA = mockk()
        val guild: Guild = mockk()
        val audioManager: AudioManager = mockk()
        val member: Member = mockk()
        val voiceState: GuildVoiceState = mockk()
        val voiceChannel: VoiceChannel = mockk(null, false, AudioChannelUnion::class)
        val selfMember: SelfMember = mockk()
        val opened = CompletableDeferred<Unit>()
        val handlerInstalled = CompletableDeferred<Unit>()

        @Volatile
        var listener: ConnectionListener? = null

        @Volatile
        var connected: Boolean = false

        /**
         * The handler installed by the port, captured so cleanup order can be checked without
         * adding production hooks. [JdaAudioSendHandler.closed] is private, so it is read
         * reflectively at the exact moment [AudioManager.setSendingHandler] is asked to detach.
         */
        @Volatile
        private var installedHandler: JdaAudioSendHandler? = null
        private var detachObserved = false
        private val handlerClosedField = JdaAudioSendHandler::class.java
            .getDeclaredField("closed")
            .apply { isAccessible = true }

        val context = CommandContext(
            commandName = "jda-voice-walkthrough",
            guildId = GuildId(100uL),
            channelId = ChannelId(200uL),
            userId = UserId(300uL),
            locale = null,
        )

        fun port(): JdaVoiceSessionPort = JdaVoiceSessionPort(
            jda = jda,
            connectTimeoutMillis = connectTimeoutMillis,
            daveSettleMillis = daveSettleMillis,
            toneTimeoutMillis = toneTimeoutMillis,
            flushMillis = flushMillis,
            disconnectTimeoutMillis = disconnectTimeoutMillis,
            selfVoiceStatePollMillis = selfVoiceStatePollMillis,
        )

        init {
            every { jda.getGuildById(100L) } returns guild
            every { guild.idLong } returns 100L
            every { guild.getMemberById(300L) } returns member
            every { member.voiceState } returns voiceState
            every { voiceState.channel } returns (voiceChannel as AudioChannelUnion)
            every { voiceChannel.guild } returns guild
            every { guild.audioManager } returns audioManager
            every { guild.selfMember } returns selfMember
            every { selfMember.voiceState } returns null

            every { audioManager.connectTimeout } returns 5_000L
            every { audioManager.isAutoReconnect } returns true
            every { audioManager.connectionListener } returns null
            every { audioManager.sendingHandler } returns null
            every { audioManager.isConnected } answers { connected }
            every { audioManager.connectionStatus } answers {
                if (connected) ConnectionStatus.CONNECTED else ConnectionStatus.NOT_CONNECTED
            }

            every { audioManager.setConnectionListener(any()) } answers {
                val newListener = firstArg<ConnectionListener?>()
                if (newListener == null && failRestoreListener) {
                    throw IllegalStateException("cannot restore the listener")
                }
                listener = newListener
                events += if (newListener == null) "listener:clear" else "listener:set"
            }
            every { audioManager.setAutoReconnect(any()) } answers {
                events += "autoReconnect:${firstArg<Boolean>()}"
            }
            every { audioManager.setConnectTimeout(any()) } answers {
                events += "connectTimeout:${firstArg<Long>()}"
            }
            every { audioManager.openAudioConnection(any()) } answers {
                events += "open"
                connected = true
                listener?.onStatusChange(ConnectionStatus.CONNECTED)
                opened.complete(Unit)
            }
            every { audioManager.closeAudioConnection() } answers {
                events += "close"
                if (failClose) {
                    throw IllegalStateException("cannot close the connection")
                }
                connected = false
                listener?.onStatusChange(ConnectionStatus.NOT_CONNECTED)
            }
            every { audioManager.setSendingHandler(any()) } answers {
                val handler = firstArg<AudioSendHandler?>()
                if (handler == null) {
                    // Cleanup must invalidate the active handler before detaching it.
                    val active = installedHandler
                    if (active != null && !detachObserved) {
                        events += "handler:closed-before-detach=${handlerClosedField.getBoolean(active)}"
                        detachObserved = true
                    }
                    events += "handler:null"
                    if (failClearHandler) {
                        throw IllegalStateException("cannot clear the sender")
                    }
                    clearHandlerFailure?.let { throw it }
                } else {
                    events += "handler:set"
                    if (handler is JdaAudioSendHandler) {
                        installedHandler = handler
                    }
                    handlerInstalled.complete(Unit)
                    if (drainFrames) {
                        while (handler.canProvide()) {
                            handler.provide20MsAudio()
                        }
                    }
                }
            }
        }
    }

    private class FakeFrameSource(
        private val actualFrames: Int,
        private val expectedFrames: Int = actualFrames,
    ) : AudioFrameSource {
        val nextFrameCalls = AtomicInteger(0)
        val closeCalls = AtomicInteger(0)

        override val format: AudioFrameFormat = AudioFrameFormat.OPUS_20MS
        override val expectedFrameCount: Int = expectedFrames

        override fun nextFrame(): AudioFrame? {
            val index = nextFrameCalls.getAndIncrement()
            return if (index < actualFrames) {
                AudioFrame.of(AudioFrameFormat.OPUS_20MS, opus20msPacket())
            } else {
                null
            }
        }

        override fun close() {
            closeCalls.incrementAndGet()
        }
    }
}

/**
 * Builds a minimal well-formed 20 ms Opus packet: config 15 (CELT 20 ms) with code 0
 * (a single frame). The optional bytes are arbitrary frame data, not an encoding.
 */
private fun opus20msPacket(vararg frameBytes: Int): ByteArray =
    ByteArray(frameBytes.size + 1).also { packet ->
        packet[0] = 0x78
        frameBytes.forEachIndexed { index, value -> packet[index + 1] = value.toByte() }
    }
