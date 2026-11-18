package es.wokis.jda.player

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.player.PlayerCommandDefinitions
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.locale.LocaleRepository
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.LocalizationCatalog
import es.wokis.localization.PlayerMessageKeys
import es.wokis.message.ComponentId
import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.model.PlayerSnapshot
import es.wokis.player.engine.model.PlayerTrack
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.GuildVoiceState
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import kotlin.test.assertFailsWith

class JdaPlayerCommandControllerTest {

    private val expected = GuildLocalizationService(
        getGuildLocaleUseCase = GetGuildLocaleUseCase(EmptyLocaleRepository()),
        catalog = LocalizationCatalog.load(),
    )

    @Test
    fun `Given a voice member When play runs Then acknowledge the search and hand the load to the coordinator`() = runTest {
        val fixture = Fixture()
        val order = mutableListOf<String>()
        every { fixture.coordinator.recordCommand(any(), any(), any(), any()) } answers { order += "record" }
        every { fixture.coordinator.startPlayback(any(), any()) } answers { order += "start" }

        val message = fixture.controller.play(context(), "  track  ")

        // Then: `/play` acknowledges immediately; the coordinator owns loading, connecting and replies.
        assertEquals(english(PlayerMessageKeys.SEARCHING_SONG), message)
        assertEquals(listOf("record", "start"), order)
        verify(exactly = 1) {
            fixture.coordinator.recordCommand(ChannelId(43uL), GuildId(GUILD_ID.toULong()), null, "General")
        }
        verify(exactly = 1) { fixture.coordinator.startPlayback(fixture.channel, "track") }
        coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
        coVerify(exactly = 0) { fixture.runtime.connect(any(), any()) }
    }

    @Test
    fun `Given a play whose outcome the coordinator reports later When play runs Then reply with the search acknowledgment`() =
        runTest {
            // Timeouts, rejected loads, no matches and voice failures are coordinator messages:
            // see JdaPlayerMessageCoordinatorTest.
            val inputs = listOf("track", "missing", "unknown song")

            inputs.forEach { input ->
                val fixture = Fixture()

                val message = fixture.controller.play(context(), input)

                assertEquals(english(PlayerMessageKeys.SEARCHING_SONG), message)
                fixture.assertDelegated(input)
            }
        }

    @Test
    fun `Given an empty input When play runs Then reject it before resolving the guild`() = runTest {
        val fixture = Fixture()

        val message = fixture.controller.play(context(), "   ")

        assertEquals(
            english(PlayerMessageKeys.ERROR_NO_CONTENT_PROVIDED, PlayerCommandDefinitions.PLAY_OPTION_INPUT),
            message,
        )
        verify(exactly = 0) { fixture.runtime.engineFor(any()) }
        verify(exactly = 0) { fixture.runtime.voiceAdapterFor(any()) }
        verify(exactly = 0) { fixture.runtime.messagesFor(any()) }
    }

    @Test
    fun `Given a direct message or an unknown guild When play runs Then reject it safely`() = runTest {
        val fixture = Fixture()

        val directMessage = fixture.controller.play(context(guildId = null), "track")
        every { fixture.jda.getGuildById(GUILD_ID) } returns null
        val unknownGuild = fixture.controller.play(context(), "track")

        assertEquals(english(PlayerMessageKeys.ERROR_NO_GUILD), directMessage)
        assertEquals(english(PlayerMessageKeys.ERROR_NO_GUILD), unknownGuild)
        coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
        verify(exactly = 0) { fixture.runtime.messagesFor(any()) }
    }

    @Test
    fun `Given a non member or a member outside voice When play runs Then reject without loading`() = runTest {
        val fixture = Fixture()
        every { fixture.guild.getMemberById(USER_ID) } returns null
        val nonMember = fixture.controller.play(context(), "track")

        every { fixture.guild.getMemberById(USER_ID) } returns fixture.member
        every { fixture.voiceState.channel } returns mockk<AudioChannelUnion>()
        val outsideVoice = fixture.controller.play(context(), "track")

        assertEquals(english(PlayerMessageKeys.ERROR_NO_VOICE_CHANNEL), nonMember)
        assertEquals(english(PlayerMessageKeys.ERROR_NO_VOICE_CHANNEL), outsideVoice)
        coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
        verify(exactly = 0) { fixture.runtime.messagesFor(any()) }
    }

    @Test
    fun `Given an out of range user id When play runs Then fail without echoing the invalid id`() = runTest {
        // Given: a user id that cannot fit JDA's signed 64-bit range.
        val fixture = Fixture()
        val oversizedUserId = UserId(ULong.MAX_VALUE)

        val error = assertFailsWith<IllegalArgumentException> {
            fixture.controller.play(context(userId = oversizedUserId), "track")
        }

        // Then: the failure stays generic and never echoes the rejected snowflake.
        val message = error.message.orEmpty()
        assertEquals("User id does not fit in JDA's signed 64-bit id range", message)
        assertFalse(
            message.contains(oversizedUserId.value.toString()),
            "the failure message echoed the invalid user id: $message",
        )
        coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
        coVerify(exactly = 0) { fixture.runtime.connect(any(), any()) }
    }

    @Test
    fun `Given an unsupported action When control runs Then reject it before touching the guild`() = runTest {
        val fixture = Fixture()

        val message = fixture.controller.control(context(), "DANCE")

        assertEquals(
            english(PlayerMessageKeys.PLAYER_UNKNOWN_ACTION, JdaPlayerAction.names.joinToString(", ")),
            message,
        )
        verify(exactly = 0) { fixture.runtime.engineFor(any()) }
        verify(exactly = 0) { fixture.jda.getGuildById(any<Long>()) }
    }

    @Test
    fun `Given a blank action When control runs Then show the player view and never report an unknown action`() =
        runTest {
            val fixture = Fixture()

            val message = fixture.controller.control(context(), "   ")

            // Kord parity: `/player` without an action only sets the player view up.
            assertEquals(fixture.ensureReply, message)
            assertNotEquals(
                english(PlayerMessageKeys.PLAYER_UNKNOWN_ACTION, JdaPlayerAction.names.joinToString(", ")),
                message,
            )
            verify(exactly = 1) {
                fixture.coordinator.recordCommand(
                    ChannelId(43uL),
                    GuildId(GUILD_ID.toULong()),
                    null,
                    "General",
                )
            }
            coVerify(exactly = 1) { fixture.coordinator.ensurePlayerView() }
            verify(exactly = 0) { fixture.runtime.engineFor(any()) }
            verify(exactly = 0) { fixture.coordinator.startPlayback(any(), any()) }
        }

    @Test
    fun `Given a missing action outside voice When control runs Then show the player view without a voice channel name`() =
        runTest {
            val fixture = Fixture()
            every { fixture.voiceState.channel } returns null

            val message = fixture.controller.control(context(), "")

            assertEquals(fixture.ensureReply, message)
            verify(exactly = 1) {
                fixture.coordinator.recordCommand(
                    ChannelId(43uL),
                    GuildId(GUILD_ID.toULong()),
                    null,
                    null,
                )
            }
            coVerify(exactly = 1) { fixture.coordinator.ensurePlayerView() }
            verify(exactly = 0) { fixture.runtime.engineFor(any()) }
        }

    @Test
    fun `Given a direct message or an unknown guild When no action runs Then reject it before the player view`() =
        runTest {
            val fixture = Fixture()

            val directMessage = fixture.controller.control(context(guildId = null), "")
            every { fixture.jda.getGuildById(GUILD_ID) } returns null
            val unknownGuild = fixture.controller.control(context(), "")

            assertEquals(english(PlayerMessageKeys.ERROR_NO_GUILD), directMessage)
            assertEquals(english(PlayerMessageKeys.ERROR_NO_GUILD), unknownGuild)
            verify(exactly = 0) { fixture.runtime.messagesFor(any()) }
            coVerify(exactly = 0) { fixture.coordinator.ensurePlayerView() }
        }

    @Test
    fun `Given a direct message or an unknown guild When control runs Then reject it safely`() = runTest {
        val fixture = Fixture()

        val directMessage = fixture.controller.control(context(guildId = null), "queue")
        every { fixture.jda.getGuildById(GUILD_ID) } returns null
        val unknownGuild = fixture.controller.control(context(), "pause")

        assertEquals(english(PlayerMessageKeys.ERROR_NO_GUILD), directMessage)
        assertEquals(english(PlayerMessageKeys.ERROR_NO_GUILD), unknownGuild)
        verify(exactly = 0) { fixture.runtime.engineFor(any()) }
    }

    @Test
    fun `Given an active queue When control asks for it Then describe the current track and pending items`() = runTest {
        val fixture = Fixture()
        every { fixture.engine.snapshot() } returns PlayerSnapshot(
            current = track("current", "Song"),
            queue = listOf(track("one", "One"), track("two", "Two")),
            paused = false,
            hasActiveTrack = true,
        )

        val message = fixture.controller.control(context(), "queue")

        val currentLabel = english(PlayerMessageKeys.PLAYER_CURRENT_TRACK)
        val queueLabel = english(PlayerMessageKeys.PLAYER_SERVER_QUEUE)
        assertEquals(
            "$currentLabel: Artist - Song | $queueLabel: 2\n1. Artist - One\n2. Artist - Two",
            message,
        )
    }

    @Test
    fun `Given a queue longer than the page When control asks for it Then list ten tracks and the remainder`() =
        runTest {
            val fixture = Fixture()
            every { fixture.engine.snapshot() } returns PlayerSnapshot(
                current = track("current", "Song"),
                queue = (1..12).map { index -> track("id$index", "Track $index") },
                hasActiveTrack = true,
            )

            val message = fixture.controller.control(context(), "queue")

            val currentLabel = english(PlayerMessageKeys.PLAYER_CURRENT_TRACK)
            val queueLabel = english(PlayerMessageKeys.PLAYER_SERVER_QUEUE)
            val remainingLabel = english(PlayerMessageKeys.PLAYER_QUEUE_TRACKS_REMAINING, 2)
            val listed = (1..10).joinToString(separator = "\n") { index -> "$index. Artist - Track $index" }
            assertEquals(
                "$currentLabel: Artist - Song | $queueLabel: 12\n$listed\n$remainingLabel",
                message,
            )
        }

    @Test
    fun `Given an empty queue When control asks for it Then report the empty queue`() = runTest {
        val fixture = Fixture()
        every { fixture.engine.snapshot() } returns PlayerSnapshot()

        val message = fixture.controller.control(context(), "queue")

        assertEquals(english(PlayerMessageKeys.PLAYER_SERVER_QUEUE_EMPTY), message)
    }

    @Test
    fun `Given transport actions When control runs Then pause resume skip and stop delegate exactly`() = runTest {
        // Given: an active track so the controller-level guard lets the transport commands through.
        val fixture = Fixture()
        every { fixture.engine.snapshot() } returns activeSnapshot()
        coEvery { fixture.engine.pause() } returns true
        coEvery { fixture.engine.resume() } returns true
        coEvery { fixture.engine.skip() } returns true
        coJustRun { fixture.engine.stop() }

        val paused = fixture.controller.control(context(), "pause")
        val resumed = fixture.controller.control(context(), "resume")
        val skipped = fixture.controller.control(context(), "skip")
        val stopped = fixture.controller.control(context(), "stop")

        assertEquals(english(PlayerMessageKeys.PLAYER_PAUSED), paused)
        assertEquals(english(PlayerMessageKeys.PLAYER_RESUMED), resumed)
        assertEquals(english(PlayerMessageKeys.SKIP_COMMAND_RESPONSE), skipped)
        assertEquals(english(PlayerMessageKeys.PLAYER_STOPPED), stopped)
        coVerify(exactly = 1) { fixture.engine.pause() }
        coVerify(exactly = 1) { fixture.engine.resume() }
        coVerify(exactly = 1) { fixture.engine.skip() }
        coVerify(exactly = 1) { fixture.engine.stop() }
    }

    @Test
    fun `Given an active track whose pause the engine refuses When control runs Then never report playback as paused`() =
        runTest {
            // Given: a track is active, so the guard passes, but the engine rejects the transition.
            val fixture = Fixture()
            coEvery { fixture.engine.pause() } returns false
            every { fixture.engine.snapshot() } returns activeSnapshot()

            val message = fixture.controller.control(context(), "pause")

            // Then: the reply may not claim the pause succeeded, and no voice side effect may imply it.
            assertNotEquals(
                english(PlayerMessageKeys.PLAYER_PAUSED),
                message,
                "a refused pause must not be reported as a successful pause",
            )
            assertTrue(message.isNotBlank(), "a refused pause must still produce a user-facing reply")
            coVerify(exactly = 1) { fixture.engine.pause() }
            coVerify(exactly = 0) { fixture.engine.resume() }
            coVerify(exactly = 0) { fixture.engine.stop() }
            coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
            coVerify(exactly = 0) { fixture.runtime.connect(any(), any()) }
            coVerify(exactly = 0) { fixture.runtime.disconnect(any()) }
            verify(exactly = 0) { fixture.runtime.voiceAdapterFor(any()) }
        }

    @Test
    fun `Given an active track whose resume the engine refuses When control runs Then never report playback as resumed`() =
        runTest {
            // Given: a track is active, so the guard passes, but the engine rejects the transition.
            val fixture = Fixture()
            coEvery { fixture.engine.resume() } returns false
            every { fixture.engine.snapshot() } returns activeSnapshot()

            val message = fixture.controller.control(context(), "resume")

            // Then: the reply may not claim the resume succeeded, and no voice side effect may imply it.
            assertNotEquals(
                english(PlayerMessageKeys.PLAYER_RESUMED),
                message,
                "a refused resume must not be reported as a successful resume",
            )
            assertTrue(message.isNotBlank(), "a refused resume must still produce a user-facing reply")
            coVerify(exactly = 1) { fixture.engine.resume() }
            coVerify(exactly = 0) { fixture.engine.pause() }
            coVerify(exactly = 0) { fixture.engine.stop() }
            coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
            coVerify(exactly = 0) { fixture.runtime.connect(any(), any()) }
            coVerify(exactly = 0) { fixture.runtime.disconnect(any()) }
            verify(exactly = 0) { fixture.runtime.voiceAdapterFor(any()) }
        }

    @Test
    fun `Given an idle player When control pauses Then answer with no active playback and never transition the engine`() =
        runTest {
            // Given: no current track, even though the engine would accept a pause.
            val fixture = Fixture()
            every { fixture.engine.snapshot() } returns PlayerSnapshot()
            coEvery { fixture.engine.pause() } returns true

            val message = fixture.controller.control(context(), "pause")

            // Then: the guard rejects the transition before the engine and never claims success.
            assertEquals(english(PlayerMessageKeys.ERROR_NO_TRACK_PLAYING), message)
            assertNotEquals(english(PlayerMessageKeys.PLAYER_PAUSED), message)
            assertNotEquals(english(PlayerMessageKeys.PLAYER_PLAYBACK_STATE_UNCHANGED), message)
            assertTrue(message.isNotBlank(), "an idle pause must still produce a user-facing reply")
            verify(exactly = 1) { fixture.engine.snapshot() }
            coVerify(exactly = 0) { fixture.engine.pause() }
            coVerify(exactly = 0) { fixture.engine.resume() }
            coVerify(exactly = 0) { fixture.engine.skip() }
            coVerify(exactly = 0) { fixture.engine.stop() }
            coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
            coVerify(exactly = 0) { fixture.runtime.connect(any(), any()) }
            coVerify(exactly = 0) { fixture.runtime.disconnect(any()) }
            verify(exactly = 0) { fixture.runtime.voiceAdapterFor(any()) }
        }

    @Test
    fun `Given an idle player When control resumes Then answer with no active playback and never transition the engine`() =
        runTest {
            // Given: no current track, even though the engine would accept a resume.
            val fixture = Fixture()
            every { fixture.engine.snapshot() } returns PlayerSnapshot()
            coEvery { fixture.engine.resume() } returns true

            val message = fixture.controller.control(context(), "resume")

            // Then: the guard rejects the transition before the engine and never claims success.
            assertEquals(english(PlayerMessageKeys.ERROR_NO_TRACK_PLAYING), message)
            assertNotEquals(english(PlayerMessageKeys.PLAYER_RESUMED), message)
            assertNotEquals(english(PlayerMessageKeys.PLAYER_PLAYBACK_STATE_UNCHANGED), message)
            assertTrue(message.isNotBlank(), "an idle resume must still produce a user-facing reply")
            verify(exactly = 1) { fixture.engine.snapshot() }
            coVerify(exactly = 0) { fixture.engine.resume() }
            coVerify(exactly = 0) { fixture.engine.pause() }
            coVerify(exactly = 0) { fixture.engine.skip() }
            coVerify(exactly = 0) { fixture.engine.stop() }
            coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
            coVerify(exactly = 0) { fixture.runtime.connect(any(), any()) }
            coVerify(exactly = 0) { fixture.runtime.disconnect(any()) }
            verify(exactly = 0) { fixture.runtime.voiceAdapterFor(any()) }
        }

    @Test
    fun `Given a skip with nothing playing When control runs Then report it`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.engine.skip() } returns false

        val message = fixture.controller.control(context(), "skip")

        assertEquals(english(PlayerMessageKeys.ERROR_NO_TRACK_PLAYING), message)
    }

    @Test
    fun `Given a disconnect When control runs Then stop the engine and close the voice connection`() = runTest {
        val fixture = Fixture()
        coJustRun { fixture.engine.stop() }
        coJustRun { fixture.runtime.disconnect(fixture.guild) }

        val message = fixture.controller.control(context(), "disconnect")

        assertEquals(english(PlayerMessageKeys.DISCONNECT_COMMAND_RESPONSE), message)
        coVerify(exactly = 1) { fixture.engine.stop() }
        coVerify(exactly = 1) { fixture.runtime.disconnect(fixture.guild) }
        coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
    }

    @Test
    fun `Given an active track When reconnect runs Then use the dedicated reconnect path without stopping or clearing it`() =
        runTest {
            val fixture = Fixture()
            coEvery { fixture.runtime.reconnect(fixture.guild, fixture.channel) } returns
                JdaPlayerReconnectResult.Reconnected
            every { fixture.engine.snapshot() } returns PlayerSnapshot(
                current = track("current", "Song"),
                queue = listOf(track("queued", "Queued")),
                hasActiveTrack = true,
            )

            val message = fixture.controller.control(context(), "reconnect")

            assertEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_PRESERVED), message)
            coVerify(exactly = 1) { fixture.runtime.reconnect(fixture.guild, fixture.channel) }
            coVerify(exactly = 0) { fixture.runtime.connect(any(), any()) }
            coVerify(exactly = 0) { fixture.engine.stop() }
            coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
            coVerify(exactly = 0) { fixture.runtime.disconnect(any()) }
        }

    @Test
    fun `Given an idle player When reconnect runs Then report the reconnect without a track`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.runtime.reconnect(fixture.guild, fixture.channel) } returns
            JdaPlayerReconnectResult.Reconnected
        every { fixture.engine.snapshot() } returns PlayerSnapshot()

        val message = fixture.controller.control(context(), "reconnect")

        assertEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_IDLE), message)
        coVerify(exactly = 1) { fixture.runtime.reconnect(fixture.guild, fixture.channel) }
        coVerify(exactly = 0) { fixture.runtime.connect(any(), any()) }
    }

    @Test
    fun `Given an already disconnected player When reconnect runs Then report the reconnect-specific refusal`() =
        runTest {
            val fixture = Fixture()
            coEvery { fixture.runtime.reconnect(fixture.guild, fixture.channel) } returns
                JdaPlayerReconnectResult.NotConnected

            val message = fixture.controller.control(context(), "reconnect")

            assertEquals(english(PlayerMessageKeys.RECONNECT_NOT_CONNECTED), message)
            coVerify(exactly = 1) { fixture.runtime.reconnect(fixture.guild, fixture.channel) }
            coVerify(exactly = 0) { fixture.runtime.connect(any(), any()) }
            coVerify(exactly = 0) { fixture.engine.stop() }
            coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
        }

    @Test
    fun `Given a foreign voice session When reconnect runs Then report the reconnect-specific refusal`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.runtime.reconnect(fixture.guild, fixture.channel) } returns
            JdaPlayerReconnectResult.ForeignSession

        val message = fixture.controller.control(context(), "reconnect")

        assertEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_FOREIGN_SESSION), message)
        coVerify(exactly = 1) { fixture.runtime.reconnect(fixture.guild, fixture.channel) }
        coVerify(exactly = 0) { fixture.runtime.connect(any(), any()) }
        coVerify(exactly = 0) { fixture.engine.stop() }
        coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
    }

    @Test
    fun `Given a disconnect that is not confirmed When reconnect runs Then report the timeout without false success`() =
        runTest {
            val fixture = Fixture()
            coEvery { fixture.runtime.reconnect(fixture.guild, fixture.channel) } returns
                JdaPlayerReconnectResult.TimedOut

            val message = fixture.controller.control(context(), "reconnect")

            assertEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_TIMEOUT), message)
            assertNotEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_PRESERVED), message)
            assertNotEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_IDLE), message)
            coVerify(exactly = 1) { fixture.runtime.reconnect(fixture.guild, fixture.channel) }
            coVerify(exactly = 0) { fixture.runtime.connect(any(), any()) }
            coVerify(exactly = 0) { fixture.engine.stop() }
            coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
        }

    @Test
    fun `Given a reconnect already pending When reconnect runs Then report the in-progress refusal without mutation`() =
        runTest {
            val fixture = Fixture()
            coEvery { fixture.runtime.reconnect(fixture.guild, fixture.channel) } returns
                JdaPlayerReconnectResult.InProgress

            val message = fixture.controller.control(context(), "reconnect")

            assertEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_IN_PROGRESS), message)
            assertNotEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_PRESERVED), message)
            assertNotEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_IDLE), message)
            coVerify(exactly = 1) { fixture.runtime.reconnect(fixture.guild, fixture.channel) }
            coVerify(exactly = 0) { fixture.runtime.connect(any(), any()) }
            coVerify(exactly = 0) { fixture.runtime.disconnect(any()) }
            coVerify(exactly = 0) { fixture.engine.stop() }
            coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
            coVerify(exactly = 0) { fixture.engine.pause() }
            coVerify(exactly = 0) { fixture.engine.resume() }
            coVerify(exactly = 0) { fixture.engine.skip() }
            verify(exactly = 0) { fixture.runtime.voiceAdapterFor(any()) }
        }

    @Test
    fun `Given a reconnect exception When reconnect runs Then report the reconnect-specific failure`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.runtime.reconnect(fixture.guild, fixture.channel) } throws
            IllegalStateException("gateway boom")

        val message = fixture.controller.control(context(), "reconnect")

        assertEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_FAILED), message)
        coVerify(exactly = 1) { fixture.runtime.reconnect(fixture.guild, fixture.channel) }
        coVerify(exactly = 0) { fixture.runtime.connect(any(), any()) }
        coVerify(exactly = 0) { fixture.engine.stop() }
        coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
    }

    @Test
    fun `Given an active track When the pause button is applied Then pause once and touch nothing else`() = runTest {
        val fixture = Fixture()
        every { fixture.engine.snapshot() } returns activeSnapshot()
        coEvery { fixture.engine.pause() } returns true

        fixture.controller.applyButton(context(), ComponentId.PLAYER_PAUSE)

        coVerify(exactly = 1) { fixture.engine.pause() }
        coVerify(exactly = 0) { fixture.engine.resume() }
        coVerify(exactly = 0) { fixture.engine.skip() }
        coVerify(exactly = 0) { fixture.engine.shuffle() }
        coVerify(exactly = 0) { fixture.engine.stop() }
    }

    @Test
    fun `Given a paused track When the resume button is applied Then resume once and touch nothing else`() = runTest {
        val fixture = Fixture()
        every { fixture.engine.snapshot() } returns activeSnapshot().copy(paused = true)
        coEvery { fixture.engine.resume() } returns true

        fixture.controller.applyButton(context(), ComponentId.PLAYER_RESUME)

        coVerify(exactly = 1) { fixture.engine.resume() }
        coVerify(exactly = 0) { fixture.engine.pause() }
        coVerify(exactly = 0) { fixture.engine.skip() }
        coVerify(exactly = 0) { fixture.engine.shuffle() }
        coVerify(exactly = 0) { fixture.engine.stop() }
    }

    @Test
    fun `Given an active track When the skip button is applied Then skip once`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.engine.skip() } returns true

        fixture.controller.applyButton(context(), ComponentId.PLAYER_SKIP)

        coVerify(exactly = 1) { fixture.engine.skip() }
        coVerify(exactly = 0) { fixture.engine.pause() }
        coVerify(exactly = 0) { fixture.engine.stop() }
    }

    @Test
    fun `Given an active queue When the shuffle button is applied Then shuffle once`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.engine.shuffle() } returns true

        fixture.controller.applyButton(context(), ComponentId.PLAYER_SHUFFLE)

        coVerify(exactly = 1) { fixture.engine.shuffle() }
        coVerify(exactly = 0) { fixture.engine.skip() }
        coVerify(exactly = 0) { fixture.engine.stop() }
    }

    @Test
    fun `Given an active track When the disconnect button is applied Then stop and disconnect once`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.engine.stop() } returns true
        coJustRun { fixture.runtime.disconnect(fixture.guild) }

        fixture.controller.applyButton(context(), ComponentId.PLAYER_DISCONNECT)

        coVerify(exactly = 1) { fixture.engine.stop() }
        coVerify(exactly = 1) { fixture.runtime.disconnect(fixture.guild) }
        coVerify(exactly = 0) { fixture.engine.pause() }
    }

    @Test
    fun `Given a voice member When the reconnect button is applied Then reconnect once`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.runtime.reconnect(fixture.guild, fixture.channel) } returns
            JdaPlayerReconnectResult.Reconnected
        every { fixture.engine.snapshot() } returns activeSnapshot()

        fixture.controller.applyButton(context(), ComponentId.PLAYER_RECONNECT)

        coVerify(exactly = 1) { fixture.runtime.reconnect(fixture.guild, fixture.channel) }
        coVerify(exactly = 0) { fixture.engine.skip() }
        coVerify(exactly = 0) { fixture.runtime.disconnect(any()) }
    }

    @Test
    fun `Given a non player component When applied Then change no playback state`() = runTest {
        val fixture = Fixture()

        fixture.controller.applyButton(context(), ComponentId.QUEUE_NEXT)

        verify(exactly = 0) { fixture.runtime.engineFor(any()) }
        coVerify(exactly = 0) { fixture.engine.pause() }
        coVerify(exactly = 0) { fixture.engine.skip() }
        coVerify(exactly = 0) { fixture.runtime.disconnect(any()) }
    }

    private suspend fun english(key: String, vararg arguments: Any): String =
        if (arguments.isEmpty()) {
            expected.getString(key = key)
        } else {
            expected.getStringFormat(key = key, arguments = arguments)
        }

    private fun context(
        guildId: GuildId? = GuildId(GUILD_ID.toULong()),
        userId: UserId = UserId(USER_ID.toULong()),
    ): CommandContext = CommandContext(
        commandName = JdaPlayerControlCommand.COMMAND_NAME,
        guildId = guildId,
        channelId = ChannelId(43uL),
        userId = userId,
        locale = LocaleCode("en-US"),
    )

    private fun track(identifier: String, title: String): PlayerTrack = PlayerTrack(
        identifier = identifier,
        title = title,
        author = "Artist",
    )

    private fun activeSnapshot(): PlayerSnapshot = PlayerSnapshot(
        current = track("current", "Song"),
        hasActiveTrack = true,
    )

    private fun voiceChannel(): AudioChannelUnion = Proxy.newProxyInstance(
        javaClass.classLoader,
        arrayOf(VoiceChannel::class.java, AudioChannelUnion::class.java),
    ) { proxy, method, arguments ->
        when (method.name) {
            "getIdLong" -> 42L
            "getName" -> "General"
            "toString" -> "test-voice-channel"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === arguments?.firstOrNull()
            else -> null
        }
    } as AudioChannelUnion

    private fun Fixture.assertDelegated(input: String) {
        verify(exactly = 1) { coordinator.recordCommand(any(), any(), any(), any()) }
        verify(exactly = 1) { coordinator.startPlayback(channel, input) }
        coVerify(exactly = 0) { engine.loadAndPlay(any()) }
        coVerify(exactly = 0) { runtime.connect(any(), any()) }
    }

    private inner class Fixture {
        val jda: JDA = mockk()
        val guild: Guild = mockk()
        val member: Member = mockk()
        val voiceState: GuildVoiceState = mockk()
        val runtime: JdaPlayerRuntime = mockk()
        val engine: PlayerEngine = mockk()
        val coordinator: JdaPlayerMessageCoordinator = mockk()
        val channelUnion: AudioChannelUnion = voiceChannel()
        val channel: VoiceChannel = channelUnion as VoiceChannel
        val ensureReply = "player view shown"
        val controller = JdaPlayerCommandController(jda, runtime, expected)

        init {
            every { jda.getGuildById(GUILD_ID) } returns guild
            every { guild.idLong } returns GUILD_ID
            every { guild.getMemberById(USER_ID) } returns member
            every { member.voiceState } returns voiceState
            every { voiceState.channel } returns channelUnion
            every { runtime.engineFor(guild) } returns engine
            every { runtime.messagesFor(guild) } returns coordinator
            justRun { coordinator.recordCommand(any(), any(), any(), any()) }
            justRun { coordinator.startPlayback(any(), any()) }
            coEvery { coordinator.ensurePlayerView() } returns ensureReply
        }
    }

    private class EmptyLocaleRepository : LocaleRepository {
        override suspend fun getGuildLocale(guildId: GuildId): LocaleCode? = null

        override suspend fun setGuildLocale(guildId: GuildId, locale: LocaleCode) =
            error("the controller must not persist a guild locale")

        override suspend fun removeGuildLocale(guildId: GuildId) =
            error("the controller must not persist a guild locale")

        override suspend fun getAllGuildLocales(): Map<GuildId, LocaleCode> = emptyMap()
    }

    private companion object {
        private const val GUILD_ID = 42L
        private const val USER_ID = 44L
    }
}
