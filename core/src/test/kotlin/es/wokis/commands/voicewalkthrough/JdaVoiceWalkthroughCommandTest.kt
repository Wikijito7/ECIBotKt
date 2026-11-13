package es.wokis.commands.voicewalkthrough

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.CommandResponse
import es.wokis.commands.neutral.GuildCommandRegistrar
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import es.wokis.voice.AudioFrame
import es.wokis.voice.AudioFrameFormat
import es.wokis.voice.AudioFrameSource
import es.wokis.voice.VoiceFailureReason
import es.wokis.voice.VoicePlaybackResult
import es.wokis.voice.VoiceRequestRejection
import es.wokis.voice.VoiceSessionPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

class JdaVoiceWalkthroughCommandTest {

    private val context = CommandContext(
        commandName = JdaVoiceWalkthroughCommand.COMMAND_NAME,
        guildId = GuildId(100uL),
        channelId = ChannelId(200uL),
        userId = UserId(300uL),
        locale = null,
    )

    @Test
    fun `Given a guild context When handled Then defer ephemerally before playing and respond ephemerally`() = runTest {
        // Given
        val events = mutableListOf<String>()
        val responder = RecordingResponder(events)
        val source = TrackingFrameSource(events)
        val session = FakeVoiceSession(events) { VoicePlaybackResult.Completed(75, 75) }
        val command = JdaVoiceWalkthroughCommand(session, { source })

        // When
        command.handle(context, responder)

        // Then
        assertEquals(listOf(true), responder.defers)
        assertEquals(1, session.calls.get())
        assertSame(source, session.source)
        assertEquals(listOf("defer", "play", "respond", "close"), events)
        assertEquals(
            listOf(
                CommandResponse(
                    "Voice walkthrough OK | handed 75/75 frames to JDA's send loop " +
                        "(delivery not confirmed)",
                    ephemeral = true,
                ),
            ),
            responder.responses,
        )
    }

    @Test
    fun `Given the tone cannot be loaded When handled Then respond with the tone error and skip the session`() = runTest {
        // Given
        val events = mutableListOf<String>()
        val responder = RecordingResponder(events)
        val session = FakeVoiceSession(events) { VoicePlaybackResult.Completed(75, 75) }
        val command = JdaVoiceWalkthroughCommand(session) { throw IllegalStateException("missing tone") }

        // When
        command.handle(context, responder)

        // Then
        assertEquals(listOf(true), responder.defers)
        assertEquals(0, session.calls.get())
        assertEquals(
            listOf(CommandResponse("The walkthrough tone could not be loaded.", ephemeral = true)),
            responder.responses,
        )
    }

    @Test
    fun `Given the source factory is cancelled When handled Then rethrow and do not respond`() = runTest {
        // Given
        val events = mutableListOf<String>()
        val responder = RecordingResponder(events)
        val session = FakeVoiceSession(events) { VoicePlaybackResult.Completed(75, 75) }
        val command = JdaVoiceWalkthroughCommand(session) { throw CancellationException("cancelled") }

        // When
        val thrown = runCatching { command.handle(context, responder) }.exceptionOrNull()

        // Then
        assertTrue(thrown is CancellationException, "expected the cancellation to be rethrown, got $thrown")
        assertEquals("cancelled", thrown?.message)
        assertEquals(0, session.calls.get())
        assertTrue(responder.responses.isEmpty())
    }

    @Test
    fun `Given the session throws When handled Then map to internal error and still close the source`() = runTest {
        // Given
        val events = mutableListOf<String>()
        val responder = RecordingResponder(events)
        val source = TrackingFrameSource(events)
        val session = FakeVoiceSession(events) { throw IllegalStateException("gateway boom") }
        val command = JdaVoiceWalkthroughCommand(session, { source })

        // When
        command.handle(context, responder)

        // Then
        assertEquals(listOf("defer", "play", "respond", "close"), events)
        assertEquals(
            listOf(CommandResponse("The voice walkthrough failed.", ephemeral = true)),
            responder.responses,
        )
        assertEquals(1, source.closeCalls.get())
    }

    @Test
    fun `Given the session is cancelled When handled Then rethrow and still close the source`() = runTest {
        // Given
        val events = mutableListOf<String>()
        val responder = RecordingResponder(events)
        val source = TrackingFrameSource(events)
        val session = FakeVoiceSession(events) { throw CancellationException("cancelled") }
        val command = JdaVoiceWalkthroughCommand(session, { source })

        // When
        val thrown = runCatching { command.handle(context, responder) }.exceptionOrNull()

        // Then
        assertTrue(thrown is CancellationException, "expected the cancellation to be rethrown, got $thrown")
        assertEquals("cancelled", thrown?.message)
        assertEquals(1, source.closeCalls.get())
        assertTrue(responder.responses.isEmpty())
        assertEquals(listOf("defer", "play", "close"), events)
    }

    @Test
    fun `Given every rejection reason When handled Then respond with the documented ephemeral text`() = runTest {
        // Given
        val expected = mapOf(
            VoiceRequestRejection.DIRECT_MESSAGE to
                "This command must be used in a guild voice channel.",
            VoiceRequestRejection.USER_NOT_IN_VOICE to
                "Join a regular voice channel first, then try again.",
            VoiceRequestRejection.STAGE_CHANNEL to
                "Stage channels are not supported; use a regular voice channel.",
            VoiceRequestRejection.UNSUPPORTED_CHANNEL to
                "That channel type is not supported; use a regular voice channel.",
            VoiceRequestRejection.OTHER_GUILD to
                "Join a voice channel in this guild.",
            VoiceRequestRejection.PLAYBACK_IN_PROGRESS to
                "A voice walkthrough is already running. Wait for it to finish.",
            VoiceRequestRejection.VOICE_SESSION_ALREADY_ACTIVE to
                "The bot already has an active voice session in this guild. Wait for it to end and try again.",
        )

        // Every reason must be covered, so a new enum constant cannot ship without copy.
        assertEquals(
            VoiceRequestRejection.entries.toSet(),
            expected.keys,
            "every rejection reason must have a documented message",
        )

        expected.forEach { (reason, message) ->
            // Given
            val events = mutableListOf<String>()
            val responder = RecordingResponder(events)
            val source = TrackingFrameSource(events)
            val command = JdaVoiceWalkthroughCommand(
                FakeVoiceSession(events) { VoicePlaybackResult.Rejected(reason) },
                { source },
            )

            // When
            command.handle(context, responder)

            // Then
            assertEquals(
                listOf(CommandResponse(message, ephemeral = true)),
                responder.responses,
                "unexpected mapping for $reason",
            )
            assertEquals(1, source.closeCalls.get(), "the source must be closed for $reason")
        }
    }

    @Test
    fun `Given every failure reason When handled Then respond with the documented ephemeral text`() = runTest {
        // Given
        val expected = mapOf(
            VoiceFailureReason.MISSING_PERMISSION to
                "The bot is missing permission to join or speak in that voice channel.",
            VoiceFailureReason.CONNECT_TIMEOUT to "Timed out waiting for the voice connection.",
            VoiceFailureReason.CONNECTION_LOST to
                "The voice connection was lost before playback completed.",
            VoiceFailureReason.TONE_TIMEOUT to "Timed out waiting for the tone to finish.",
            VoiceFailureReason.INCOMPLETE_PLAYBACK to
                "Not all tone frames were handed to the voice connection.",
            VoiceFailureReason.DISCONNECT_UNCONFIRMED to "The voice connection did not close cleanly.",
            VoiceFailureReason.CLEANUP_FAILED to
                "The voice session could not be cleaned up completely after the walkthrough.",
            VoiceFailureReason.INTERNAL_ERROR to "The voice walkthrough failed.",
        )

        // Every reason must be covered, so a new enum constant cannot ship without copy.
        assertEquals(
            VoiceFailureReason.entries.toSet(),
            expected.keys,
            "every failure reason must have a documented message",
        )

        expected.forEach { (reason, message) ->
            // Given
            val events = mutableListOf<String>()
            val responder = RecordingResponder(events)
            val source = TrackingFrameSource(events)
            val command = JdaVoiceWalkthroughCommand(
                FakeVoiceSession(events) { VoicePlaybackResult.Failed(reason) },
                { source },
            )

            // When
            command.handle(context, responder)

            // Then
            assertEquals(
                listOf(CommandResponse(message, ephemeral = true)),
                responder.responses,
                "unexpected mapping for $reason",
            )
            assertEquals(1, source.closeCalls.get(), "the source must be closed for $reason")
        }
    }

    @Test
    fun `Given the command definition When inspected Then expose the documented name and description`() {
        // Given
        val command = JdaVoiceWalkthroughCommand(
            FakeVoiceSession(mutableListOf()) { VoicePlaybackResult.Completed(1, 1) },
            { TrackingFrameSource(mutableListOf()) },
        )

        // Then
        assertEquals("jda-voice-walkthrough", command.name)
        assertEquals(JdaVoiceWalkthroughCommand.COMMAND_NAME, command.name)
        assertEquals(JdaVoiceWalkthroughCommand.COMMAND_DESCRIPTION, command.definition.description)
        assertTrue(command.definition.description.isNotBlank())
    }

    @Test
    fun `Given a guild id When registered Then use exactly that guild and no other destination`() = runTest {
        // Given
        val registrar = RecordingRegistrar()
        val command = JdaVoiceWalkthroughCommand(
            FakeVoiceSession(mutableListOf()) { VoicePlaybackResult.Completed(1, 1) },
            { TrackingFrameSource(mutableListOf()) },
        )
        val firstGuild = GuildId(111uL)
        val secondGuild = GuildId(222uL)

        // When
        command.registerIn(registrar, firstGuild)
        command.registerIn(registrar, secondGuild)

        // Then
        assertEquals(listOf(firstGuild, secondGuild), registrar.registrations.map { it.guildId })
        assertEquals(
            listOf(command.definition, command.definition),
            registrar.registrations.map { it.command },
        )
        assertEquals(
            CommandDefinition(JdaVoiceWalkthroughCommand.COMMAND_NAME, JdaVoiceWalkthroughCommand.COMMAND_DESCRIPTION),
            command.definition,
        )
    }

    private class RecordingResponder(
        private val events: MutableList<String>,
    ) : CommandResponder {
        val defers = mutableListOf<Boolean>()
        val responses = mutableListOf<CommandResponse>()

        override suspend fun defer(ephemeral: Boolean) {
            defers += ephemeral
            events += "defer"
        }

        override suspend fun respond(response: CommandResponse) {
            responses += response
            events += "respond"
        }
    }

    private class FakeVoiceSession(
        private val events: MutableList<String>,
        private val result: suspend (AudioFrameSource) -> VoicePlaybackResult,
    ) : VoiceSessionPort {
        val calls = AtomicInteger(0)
        var source: AudioFrameSource? = null

        override suspend fun play(context: CommandContext, source: AudioFrameSource): VoicePlaybackResult {
            calls.incrementAndGet()
            this.source = source
            events += "play"
            return result(source)
        }
    }

    private class TrackingFrameSource(
        private val events: MutableList<String>,
    ) : AudioFrameSource {
        val closeCalls = AtomicInteger(0)

        override val format: AudioFrameFormat = AudioFrameFormat.OPUS_20MS
        override val expectedFrameCount: Int = 1

        override fun nextFrame(): AudioFrame? = null

        override fun close() {
            closeCalls.incrementAndGet()
            events += "close"
        }
    }

    private data class Registration(val guildId: GuildId, val command: CommandDefinition)

    private class RecordingRegistrar : GuildCommandRegistrar {
        val registrations = mutableListOf<Registration>()

        override suspend fun register(guildId: GuildId, command: CommandDefinition) {
            registrations += Registration(guildId, command)
        }
    }
}
