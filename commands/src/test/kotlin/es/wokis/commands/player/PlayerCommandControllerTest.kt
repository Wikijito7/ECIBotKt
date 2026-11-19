package es.wokis.commands.player

import es.wokis.commands.neutral.CommandContext
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Neutral contract of `/play` and `/player`.
 *
 * The controller owns no gateway type: every lookup and side effect is asserted against the strict
 * [PlayerActionsPort], [PlayerVoiceTargetPort] and [PlayerMessageCoordinators] mocks, so a change in
 * the adapter cannot silently change what the command replies.
 */
class PlayerCommandControllerTest {

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
            fixture.coordinator.recordCommand(
                ChannelId(CHANNEL_ID.toULong()),
                GuildId(GUILD_ID.toULong()),
                null,
                "General",
            )
        }
        verify(exactly = 1) { fixture.coordinator.startPlayback(fixture.target, "track") }
    }

    @Test
    fun `Given a play whose outcome the coordinator reports later When play runs Then reply with the search acknowledgment`() =
        runTest {
            // Timeouts, rejected loads, no matches and voice failures are coordinator messages:
            // see PlayerMessageCoordinatorTest.
            val inputs = listOf("track", "missing", "unknown song")

            inputs.forEach { input ->
                val fixture = Fixture()

                val message = fixture.controller.play(context(), input)

                assertEquals(english(PlayerMessageKeys.SEARCHING_SONG), message, input)
                fixture.assertDelegated(input)
            }
        }

    @Test
    fun `Given an empty input When play runs Then reject it before resolving the voice target`() = runTest {
        val fixture = Fixture()

        val message = fixture.controller.play(context(), "   ")

        assertEquals(
            english(PlayerMessageKeys.ERROR_NO_CONTENT_PROVIDED, PlayerCommandDefinitions.PLAY_OPTION_INPUT),
            message,
        )
        coVerify(exactly = 0) { fixture.voiceTargets.resolve(any()) }
        verify(exactly = 0) { fixture.coordinators.coordinatorFor(any()) }
        verify(exactly = 0) { fixture.coordinator.startPlayback(any(), any()) }
    }

    @Test
    fun `Given a direct message or an unknown guild When play runs Then reject it safely`() = runTest {
        val fixture = Fixture()

        val directMessage = fixture.controller.play(context(guildId = null), "track")
        coEvery { fixture.voiceTargets.resolve(any()) } returns
            PlayerVoiceTargetResult.Unavailable(PlayerVoiceUnavailableReason.NO_GUILD)
        val unknownGuild = fixture.controller.play(context(), "track")

        assertEquals(english(PlayerMessageKeys.ERROR_NO_GUILD), directMessage)
        assertEquals(english(PlayerMessageKeys.ERROR_NO_GUILD), unknownGuild)
        verify(exactly = 0) { fixture.coordinator.startPlayback(any(), any()) }
        verify(exactly = 0) { fixture.coordinator.recordCommand(any(), any(), any(), any()) }
    }

    @Test
    fun `Given a caller outside voice When play runs Then reject without handing the load over`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.voiceTargets.resolve(any()) } returns
            PlayerVoiceTargetResult.Unavailable(PlayerVoiceUnavailableReason.NO_VOICE_CHANNEL)

        val message = fixture.controller.play(context(), "track")

        assertEquals(english(PlayerMessageKeys.ERROR_NO_VOICE_CHANNEL), message)
        verify(exactly = 0) { fixture.coordinator.startPlayback(any(), any()) }
        verify(exactly = 0) { fixture.coordinator.recordCommand(any(), any(), any(), any()) }
    }

    @Test
    fun `Given an unsupported action When control runs Then reject it before touching the guild`() = runTest {
        val fixture = Fixture()

        val message = fixture.controller.control(context(), "DANCE")

        assertEquals(
            english(PlayerMessageKeys.PLAYER_UNKNOWN_ACTION, PlayerAction.names.joinToString(", ")),
            message,
        )
        coVerify(exactly = 0) { fixture.actions.isGuildAvailable(any()) }
        verify(exactly = 0) { fixture.coordinators.coordinatorFor(any()) }
        coVerify(exactly = 0) { fixture.actions.snapshot(any()) }
    }

    @Test
    fun `Given a blank action When control runs Then show the player view and never report an unknown action`() =
        runTest {
            val fixture = Fixture()

            val message = fixture.controller.control(context(), "   ")

            // Kord parity: `/player` without an action only sets the player view up.
            assertEquals(fixture.ensureReply, message)
            assertNotEquals(
                english(PlayerMessageKeys.PLAYER_UNKNOWN_ACTION, PlayerAction.names.joinToString(", ")),
                message,
            )
            verify(exactly = 1) {
                fixture.coordinator.recordCommand(
                    ChannelId(CHANNEL_ID.toULong()),
                    GuildId(GUILD_ID.toULong()),
                    null,
                    "General",
                )
            }
            coVerify(exactly = 1) { fixture.coordinator.ensurePlayerView() }
            verify(exactly = 0) { fixture.coordinator.startPlayback(any(), any()) }
            coVerify(exactly = 0) { fixture.actions.snapshot(any()) }
        }

    @Test
    fun `Given a missing action outside voice When control runs Then show the player view without a voice channel name`() =
        runTest {
            val fixture = Fixture()
            coEvery { fixture.voiceTargets.resolve(any()) } returns
                PlayerVoiceTargetResult.Unavailable(PlayerVoiceUnavailableReason.NO_VOICE_CHANNEL)

            val message = fixture.controller.control(context(), "")

            assertEquals(fixture.ensureReply, message)
            verify(exactly = 1) {
                fixture.coordinator.recordCommand(
                    ChannelId(CHANNEL_ID.toULong()),
                    GuildId(GUILD_ID.toULong()),
                    null,
                    null,
                )
            }
            coVerify(exactly = 1) { fixture.coordinator.ensurePlayerView() }
        }

    @Test
    fun `Given a direct message or an unknown guild When no action runs Then reject it before the player view`() =
        runTest {
            val fixture = Fixture()

            val directMessage = fixture.controller.control(context(guildId = null), "")
            coEvery { fixture.actions.isGuildAvailable(any()) } returns false
            val unknownGuild = fixture.controller.control(context(), "")

            assertEquals(english(PlayerMessageKeys.ERROR_NO_GUILD), directMessage)
            assertEquals(english(PlayerMessageKeys.ERROR_NO_GUILD), unknownGuild)
            coVerify(exactly = 0) { fixture.coordinator.ensurePlayerView() }
            verify(exactly = 0) { fixture.coordinator.recordCommand(any(), any(), any(), any()) }
        }

    @Test
    fun `Given a direct message or an unknown guild When control runs Then reject it safely`() = runTest {
        val fixture = Fixture()

        val directMessage = fixture.controller.control(context(guildId = null), "queue")
        coEvery { fixture.actions.isGuildAvailable(any()) } returns false
        val unknownGuild = fixture.controller.control(context(), "pause")

        assertEquals(english(PlayerMessageKeys.ERROR_NO_GUILD), directMessage)
        assertEquals(english(PlayerMessageKeys.ERROR_NO_GUILD), unknownGuild)
        coVerify(exactly = 0) { fixture.actions.snapshot(any()) }
        coVerify(exactly = 0) { fixture.actions.pause(any()) }
    }

    @Test
    fun `Given a reachable guild without a message coordinator When no action runs Then reject it safely`() = runTest {
        val fixture = Fixture()
        every { fixture.coordinators.coordinatorFor(any()) } returns null

        val shown = fixture.controller.control(context(), "")
        val played = fixture.controller.play(context(), "track")

        assertEquals(english(PlayerMessageKeys.ERROR_NO_GUILD), shown)
        assertEquals(english(PlayerMessageKeys.ERROR_NO_GUILD), played)
        coVerify(exactly = 0) { fixture.coordinator.ensurePlayerView() }
        verify(exactly = 0) { fixture.coordinator.startPlayback(any(), any()) }
    }

    @Test
    fun `Given an active queue When control asks for it Then describe the current track and pending items`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.actions.snapshot(fixture.actor) } returns queueState(
            PlayerSnapshot(
                current = track("current", "Song"),
                queue = listOf(track("one", "One"), track("two", "Two")),
                paused = false,
                hasActiveTrack = true,
            ),
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
            coEvery { fixture.actions.snapshot(fixture.actor) } returns queueState(
                PlayerSnapshot(
                    current = track("current", "Song"),
                    queue = (1..12).map { index -> track("id$index", "Track $index") },
                    hasActiveTrack = true,
                ),
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
        coEvery { fixture.actions.snapshot(fixture.actor) } returns queueState(PlayerSnapshot())

        val message = fixture.controller.control(context(), "queue")

        assertEquals(english(PlayerMessageKeys.PLAYER_SERVER_QUEUE_EMPTY), message)
    }

    @Test
    fun `Given an unreachable guild when the queue is read Then report no guild`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.actions.snapshot(fixture.actor) } returns null

        val message = fixture.controller.control(context(), "queue")

        assertEquals(english(PlayerMessageKeys.ERROR_NO_GUILD), message)
    }

    @Test
    fun `Given transport actions When control runs Then pause resume skip and stop delegate exactly`() = runTest {
        // Given: an active track so the controller-level guard lets the transport commands through.
        val fixture = Fixture()
        coEvery { fixture.actions.snapshot(fixture.actor) } returns queueState(activeSnapshot())
        coEvery { fixture.actions.pause(fixture.actor) } returns true
        coEvery { fixture.actions.resume(fixture.actor) } returns true
        coEvery { fixture.actions.skip(fixture.actor) } returns true
        coEvery { fixture.actions.stop(fixture.actor) } returns true

        val paused = fixture.controller.control(context(), "pause")
        val resumed = fixture.controller.control(context(), "resume")
        val skipped = fixture.controller.control(context(), "skip")
        val stopped = fixture.controller.control(context(), "stop")

        assertEquals(english(PlayerMessageKeys.PLAYER_PAUSED), paused)
        assertEquals(english(PlayerMessageKeys.PLAYER_RESUMED), resumed)
        assertEquals(english(PlayerMessageKeys.SKIP_COMMAND_RESPONSE), skipped)
        assertEquals(english(PlayerMessageKeys.PLAYER_STOPPED), stopped)
        coVerify(exactly = 1) { fixture.actions.pause(fixture.actor) }
        coVerify(exactly = 1) { fixture.actions.resume(fixture.actor) }
        coVerify(exactly = 1) { fixture.actions.skip(fixture.actor) }
        coVerify(exactly = 1) { fixture.actions.stop(fixture.actor) }
    }

    @Test
    fun `Given an active track whose pause the port refuses When control runs Then never report playback as paused`() =
        runTest {
            // Given: a track is active, so the guard passes, but the transport rejects the transition.
            val fixture = Fixture()
            coEvery { fixture.actions.pause(fixture.actor) } returns false
            coEvery { fixture.actions.snapshot(fixture.actor) } returns queueState(activeSnapshot())

            val message = fixture.controller.control(context(), "pause")

            // Then: the reply may not claim the pause succeeded, and no other action may run.
            assertEquals(english(PlayerMessageKeys.PLAYER_PLAYBACK_STATE_UNCHANGED), message)
            assertNotEquals(
                english(PlayerMessageKeys.PLAYER_PAUSED),
                message,
                "a refused pause must not be reported as a successful pause",
            )
            assertTrue(message.isNotBlank(), "a refused pause must still produce a user-facing reply")
            coVerify(exactly = 1) { fixture.actions.pause(fixture.actor) }
            coVerify(exactly = 0) { fixture.actions.resume(any()) }
            coVerify(exactly = 0) { fixture.actions.stop(any()) }
            coVerify(exactly = 0) { fixture.actions.disconnect(any()) }
            verify(exactly = 0) { fixture.coordinator.startPlayback(any(), any()) }
        }

    @Test
    fun `Given an active track whose resume the port refuses When control runs Then never report playback as resumed`() =
        runTest {
            // Given: a track is active, so the guard passes, but the transport rejects the transition.
            val fixture = Fixture()
            coEvery { fixture.actions.resume(fixture.actor) } returns false
            coEvery { fixture.actions.snapshot(fixture.actor) } returns queueState(activeSnapshot())

            val message = fixture.controller.control(context(), "resume")

            // Then: the reply may not claim the resume succeeded, and no other action may run.
            assertEquals(english(PlayerMessageKeys.PLAYER_PLAYBACK_STATE_UNCHANGED), message)
            assertNotEquals(
                english(PlayerMessageKeys.PLAYER_RESUMED),
                message,
                "a refused resume must not be reported as a successful resume",
            )
            assertTrue(message.isNotBlank(), "a refused resume must still produce a user-facing reply")
            coVerify(exactly = 1) { fixture.actions.resume(fixture.actor) }
            coVerify(exactly = 0) { fixture.actions.pause(any()) }
            coVerify(exactly = 0) { fixture.actions.stop(any()) }
            coVerify(exactly = 0) { fixture.actions.disconnect(any()) }
            verify(exactly = 0) { fixture.coordinator.startPlayback(any(), any()) }
        }

    @Test
    fun `Given an idle player When control pauses Then answer with no active playback and never transition`() =
        runTest {
            // Given: no current track, even though the transport would accept a pause.
            val fixture = Fixture()
            coEvery { fixture.actions.snapshot(fixture.actor) } returns queueState(PlayerSnapshot())
            coEvery { fixture.actions.pause(fixture.actor) } returns true

            val message = fixture.controller.control(context(), "pause")

            // Then: the guard rejects the transition before the transport and never claims success.
            assertEquals(english(PlayerMessageKeys.ERROR_NO_TRACK_PLAYING), message)
            assertNotEquals(english(PlayerMessageKeys.PLAYER_PAUSED), message)
            assertNotEquals(english(PlayerMessageKeys.PLAYER_PLAYBACK_STATE_UNCHANGED), message)
            coVerify(exactly = 0) { fixture.actions.pause(any()) }
            coVerify(exactly = 0) { fixture.actions.resume(any()) }
            coVerify(exactly = 0) { fixture.actions.skip(any()) }
            coVerify(exactly = 0) { fixture.actions.stop(any()) }
            coVerify(exactly = 0) { fixture.actions.disconnect(any()) }
        }

    @Test
    fun `Given an idle player When control resumes Then answer with no active playback and never transition`() =
        runTest {
            // Given: no current track, even though the transport would accept a resume.
            val fixture = Fixture()
            coEvery { fixture.actions.snapshot(fixture.actor) } returns queueState(PlayerSnapshot())
            coEvery { fixture.actions.resume(fixture.actor) } returns true

            val message = fixture.controller.control(context(), "resume")

            // Then: the guard rejects the transition before the transport and never claims success.
            assertEquals(english(PlayerMessageKeys.ERROR_NO_TRACK_PLAYING), message)
            assertNotEquals(english(PlayerMessageKeys.PLAYER_RESUMED), message)
            assertNotEquals(english(PlayerMessageKeys.PLAYER_PLAYBACK_STATE_UNCHANGED), message)
            coVerify(exactly = 0) { fixture.actions.resume(any()) }
            coVerify(exactly = 0) { fixture.actions.pause(any()) }
            coVerify(exactly = 0) { fixture.actions.skip(any()) }
            coVerify(exactly = 0) { fixture.actions.stop(any()) }
            coVerify(exactly = 0) { fixture.actions.disconnect(any()) }
        }

    @Test
    fun `Given an unreadable snapshot When control pauses Then report no guild and never transition`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.actions.snapshot(fixture.actor) } returns null

        val paused = fixture.controller.control(context(), "pause")
        val resumed = fixture.controller.control(context(), "resume")

        assertEquals(english(PlayerMessageKeys.ERROR_NO_GUILD), paused)
        assertEquals(english(PlayerMessageKeys.ERROR_NO_GUILD), resumed)
        coVerify(exactly = 0) { fixture.actions.pause(any()) }
        coVerify(exactly = 0) { fixture.actions.resume(any()) }
    }

    @Test
    fun `Given a skip with nothing playing When control runs Then report it`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.actions.skip(fixture.actor) } returns false

        val message = fixture.controller.control(context(), "skip")

        assertEquals(english(PlayerMessageKeys.ERROR_NO_TRACK_PLAYING), message)
    }

    @Test
    fun `Given a disconnect When control runs Then stop the transport and close the voice connection`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.actions.stop(fixture.actor) } returns true
        coJustRun { fixture.actions.disconnect(fixture.actor) }

        val message = fixture.controller.control(context(), "disconnect")

        assertEquals(english(PlayerMessageKeys.DISCONNECT_COMMAND_RESPONSE), message)
        coVerify(exactly = 1) { fixture.actions.stop(fixture.actor) }
        coVerify(exactly = 1) { fixture.actions.disconnect(fixture.actor) }
        verify(exactly = 0) { fixture.coordinator.startPlayback(any(), any()) }
    }

    @Test
    fun `Given an active track When reconnect runs Then use the dedicated reconnect path without stopping or clearing it`() =
        runTest {
            val fixture = Fixture()
            coEvery { fixture.actions.reconnect(fixture.actor) } returns PlayerReconnectResult.RECONNECTED
            coEvery { fixture.actions.snapshot(fixture.actor) } returns queueState(
                PlayerSnapshot(
                    current = track("current", "Song"),
                    queue = listOf(track("queued", "Queued")),
                    hasActiveTrack = true,
                ),
            )

            val message = fixture.controller.control(context(), "reconnect")

            assertEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_PRESERVED), message)
            coVerify(exactly = 1) { fixture.actions.reconnect(fixture.actor) }
            coVerify(exactly = 0) { fixture.actions.stop(any()) }
            coVerify(exactly = 0) { fixture.actions.disconnect(any()) }
            verify(exactly = 0) { fixture.coordinator.startPlayback(any(), any()) }
        }

    @Test
    fun `Given an idle player When reconnect runs Then report the reconnect without a track`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.actions.reconnect(fixture.actor) } returns PlayerReconnectResult.RECONNECTED
        coEvery { fixture.actions.snapshot(fixture.actor) } returns queueState(PlayerSnapshot())

        val message = fixture.controller.control(context(), "reconnect")

        assertEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_IDLE), message)
        coVerify(exactly = 1) { fixture.actions.reconnect(fixture.actor) }
    }

    @Test
    fun `Given an already disconnected player When reconnect runs Then report the reconnect-specific refusal`() =
        runTest {
            val fixture = Fixture()
            coEvery { fixture.actions.reconnect(fixture.actor) } returns PlayerReconnectResult.NOT_CONNECTED

            val message = fixture.controller.control(context(), "reconnect")

            assertEquals(english(PlayerMessageKeys.RECONNECT_NOT_CONNECTED), message)
            coVerify(exactly = 1) { fixture.actions.reconnect(fixture.actor) }
            coVerify(exactly = 0) { fixture.actions.stop(any()) }
        }

    @Test
    fun `Given a foreign voice session When reconnect runs Then report the reconnect-specific refusal`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.actions.reconnect(fixture.actor) } returns PlayerReconnectResult.FOREIGN_SESSION

        val message = fixture.controller.control(context(), "reconnect")

        assertEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_FOREIGN_SESSION), message)
        assertNotEquals(english(PlayerMessageKeys.RECONNECT_NOT_CONNECTED), message)
        coVerify(exactly = 1) { fixture.actions.reconnect(fixture.actor) }
        coVerify(exactly = 0) { fixture.actions.stop(any()) }
    }

    @Test
    fun `Given a disconnect that is not confirmed When reconnect runs Then report the timeout without false success`() =
        runTest {
            val fixture = Fixture()
            coEvery { fixture.actions.reconnect(fixture.actor) } returns PlayerReconnectResult.TIMED_OUT

            val message = fixture.controller.control(context(), "reconnect")

            assertEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_TIMEOUT), message)
            assertNotEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_PRESERVED), message)
            assertNotEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_IDLE), message)
            coVerify(exactly = 1) { fixture.actions.reconnect(fixture.actor) }
            coVerify(exactly = 0) { fixture.actions.stop(any()) }
        }

    @Test
    fun `Given a reconnect already pending When reconnect runs Then report the in-progress refusal without mutation`() =
        runTest {
            val fixture = Fixture()
            coEvery { fixture.actions.reconnect(fixture.actor) } returns PlayerReconnectResult.IN_PROGRESS

            val message = fixture.controller.control(context(), "reconnect")

            assertEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_IN_PROGRESS), message)
            assertNotEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_PRESERVED), message)
            assertNotEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_IDLE), message)
            coVerify(exactly = 1) { fixture.actions.reconnect(fixture.actor) }
            coVerify(exactly = 0) { fixture.actions.disconnect(any()) }
            coVerify(exactly = 0) { fixture.actions.stop(any()) }
            coVerify(exactly = 0) { fixture.actions.pause(any()) }
            coVerify(exactly = 0) { fixture.actions.resume(any()) }
            coVerify(exactly = 0) { fixture.actions.skip(any()) }
        }

    @Test
    fun `Given a generic failed reconnect When reconnect runs Then report the reconnect-specific failure`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.actions.reconnect(fixture.actor) } returns PlayerReconnectResult.FAILED

        val message = fixture.controller.control(context(), "reconnect")

        assertEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_FAILED), message)
        assertNotEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_FOREIGN_SESSION), message)
        assertNotEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_IN_PROGRESS), message)
        assertNotEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_TIMEOUT), message)
    }

    @Test
    fun `Given a reconnect exception When reconnect runs Then report the reconnect-specific failure`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.actions.reconnect(fixture.actor) } throws IllegalStateException("gateway boom")

        val message = fixture.controller.control(context(), "reconnect")

        assertEquals(english(PlayerMessageKeys.PLAYER_RECONNECT_FAILED), message)
        coVerify(exactly = 1) { fixture.actions.reconnect(fixture.actor) }
        coVerify(exactly = 0) { fixture.actions.stop(any()) }
    }

    @Test
    fun `Given a caller outside voice When reconnect runs Then reject before the transport`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.voiceTargets.resolve(any()) } returns
            PlayerVoiceTargetResult.Unavailable(PlayerVoiceUnavailableReason.NO_VOICE_CHANNEL)

        val message = fixture.controller.control(context(), "reconnect")

        assertEquals(english(PlayerMessageKeys.ERROR_NO_VOICE_CHANNEL), message)
        coVerify(exactly = 0) { fixture.actions.reconnect(any()) }
    }

    @Test
    fun `Given an active track When the pause button is applied Then pause once and touch nothing else`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.actions.snapshot(fixture.actor) } returns queueState(activeSnapshot())
        coEvery { fixture.actions.pause(fixture.actor) } returns true

        fixture.controller.applyButton(context(), ComponentId.PLAYER_PAUSE)

        coVerify(exactly = 1) { fixture.actions.pause(fixture.actor) }
        coVerify(exactly = 0) { fixture.actions.resume(any()) }
        coVerify(exactly = 0) { fixture.actions.skip(any()) }
        coVerify(exactly = 0) { fixture.actions.shuffle(any()) }
        coVerify(exactly = 0) { fixture.actions.stop(any()) }
    }

    @Test
    fun `Given a paused track When the resume button is applied Then resume once and touch nothing else`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.actions.snapshot(fixture.actor) } returns
            queueState(activeSnapshot().copy(paused = true))
        coEvery { fixture.actions.resume(fixture.actor) } returns true

        fixture.controller.applyButton(context(), ComponentId.PLAYER_RESUME)

        coVerify(exactly = 1) { fixture.actions.resume(fixture.actor) }
        coVerify(exactly = 0) { fixture.actions.pause(any()) }
        coVerify(exactly = 0) { fixture.actions.skip(any()) }
        coVerify(exactly = 0) { fixture.actions.shuffle(any()) }
        coVerify(exactly = 0) { fixture.actions.stop(any()) }
    }

    @Test
    fun `Given an active track When the skip button is applied Then skip once`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.actions.skip(fixture.actor) } returns true

        fixture.controller.applyButton(context(), ComponentId.PLAYER_SKIP)

        coVerify(exactly = 1) { fixture.actions.skip(fixture.actor) }
        coVerify(exactly = 0) { fixture.actions.pause(any()) }
        coVerify(exactly = 0) { fixture.actions.stop(any()) }
    }

    @Test
    fun `Given an active queue When the shuffle button is applied Then shuffle once`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.actions.shuffle(fixture.actor) } returns true

        fixture.controller.applyButton(context(), ComponentId.PLAYER_SHUFFLE)

        coVerify(exactly = 1) { fixture.actions.shuffle(fixture.actor) }
        coVerify(exactly = 0) { fixture.actions.skip(any()) }
        coVerify(exactly = 0) { fixture.actions.stop(any()) }
    }

    @Test
    fun `Given an active track When the disconnect button is applied Then stop and disconnect once`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.actions.stop(fixture.actor) } returns true
        coJustRun { fixture.actions.disconnect(fixture.actor) }

        fixture.controller.applyButton(context(), ComponentId.PLAYER_DISCONNECT)

        coVerify(exactly = 1) { fixture.actions.stop(fixture.actor) }
        coVerify(exactly = 1) { fixture.actions.disconnect(fixture.actor) }
        coVerify(exactly = 0) { fixture.actions.pause(any()) }
    }

    @Test
    fun `Given a voice member When the reconnect button is applied Then reconnect once`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.actions.reconnect(fixture.actor) } returns PlayerReconnectResult.RECONNECTED
        coEvery { fixture.actions.snapshot(fixture.actor) } returns queueState(activeSnapshot())

        fixture.controller.applyButton(context(), ComponentId.PLAYER_RECONNECT)

        coVerify(exactly = 1) { fixture.actions.reconnect(fixture.actor) }
        coVerify(exactly = 0) { fixture.actions.skip(any()) }
        coVerify(exactly = 0) { fixture.actions.disconnect(any()) }
    }

    @Test
    fun `Given a non player component When applied Then change no playback state`() = runTest {
        val fixture = Fixture()

        fixture.controller.applyButton(context(), ComponentId.QUEUE_NEXT)

        coVerify(exactly = 0) { fixture.actions.pause(any()) }
        coVerify(exactly = 0) { fixture.actions.resume(any()) }
        coVerify(exactly = 0) { fixture.actions.skip(any()) }
        coVerify(exactly = 0) { fixture.actions.shuffle(any()) }
        coVerify(exactly = 0) { fixture.actions.reconnect(any()) }
        coVerify(exactly = 0) { fixture.actions.disconnect(any()) }
    }

    @Test
    fun `Given an unknown guild When a button is applied Then ignore it silently`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.actions.isGuildAvailable(any()) } returns false

        fixture.controller.applyButton(context(), ComponentId.PLAYER_PAUSE)
        fixture.controller.applyButton(context(guildId = null), ComponentId.PLAYER_PAUSE)

        coVerify(exactly = 1) { fixture.actions.isGuildAvailable(fixture.actor) }
        coVerify(exactly = 0) { fixture.actions.pause(any()) }
    }

    private suspend fun english(key: String, vararg arguments: Any): String =
        if (arguments.isEmpty()) {
            expected.getString(key = key)
        } else {
            expected.getStringFormat(key = key, arguments = arguments)
        }

    private fun context(
        guildId: GuildId? = GuildId(GUILD_ID.toULong()),
    ): CommandContext = CommandContext(
        commandName = PlayerCommandDefinitions.PLAYER_COMMAND_NAME,
        guildId = guildId,
        channelId = ChannelId(CHANNEL_ID.toULong()),
        userId = UserId(USER_ID.toULong()),
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

    private fun queueState(snapshot: PlayerSnapshot): PlayerQueueSnapshot = PlayerQueueSnapshot(
        guildName = "Jungle",
        snapshot = snapshot,
    )

    private fun Fixture.assertDelegated(input: String) {
        verify(exactly = 1) { coordinator.recordCommand(any(), any(), any(), any()) }
        verify(exactly = 1) { coordinator.startPlayback(target, input) }
    }

    private inner class Fixture {
        val actions: PlayerActionsPort = mockk()
        val coordinator: PlayerMessageCoordinator = mockk()
        val coordinators: PlayerMessageCoordinators = mockk()
        val voiceTargets: PlayerVoiceTargetPort = mockk()
        val target = PlayerVoiceTarget(channelId = ChannelId(VOICE_CHANNEL_ID.toULong()), channelName = "General")
        val actor = PlayerActor(
            guildId = GuildId(GUILD_ID.toULong()),
            userId = UserId(USER_ID.toULong()),
            channelId = ChannelId(CHANNEL_ID.toULong()),
        )
        val ensureReply = "player view shown"
        val controller = PlayerCommandController(actions, coordinators, voiceTargets, expected)

        init {
            coEvery { actions.isGuildAvailable(any()) } returns true
            every { coordinators.coordinatorFor(any()) } returns coordinator
            coEvery { voiceTargets.resolve(any()) } returns PlayerVoiceTargetResult.Ready(target)
            coEvery { voiceTargets.channelName(any()) } returns "General"
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
        private const val CHANNEL_ID = 43L
        private const val USER_ID = 44L
        private const val VOICE_CHANNEL_ID = 99L
    }
}
