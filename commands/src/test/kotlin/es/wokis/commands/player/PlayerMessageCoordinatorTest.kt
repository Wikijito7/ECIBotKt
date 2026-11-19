package es.wokis.commands.player

import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.locale.LocaleRepository
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.LocalizationCatalog
import es.wokis.localization.PlayerMessageKeys
import es.wokis.player.engine.event.PlayerEvent
import es.wokis.player.engine.model.PlayerSnapshot
import es.wokis.player.engine.model.PlayerTrack
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Chat and player-embed contract of [PlayerMessageCoordinator].
 *
 * Everything gateway-specific is behind the neutral ports, so these cases assert the same
 * Kord-parity behavior the adapter used to provide: `now playing` only while no embed exists,
 * edit-only events, the `#player` setup outcomes, the idle leave timer and the sanitized failures.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerMessageCoordinatorTest {

    @Test
    fun `Given every track queued variant When handled Then emit the matching queue key`() = coordinatorTest { fixture ->
        val track = track()
        val url = "https://music.example/audio"
        fixture.recordCommand()

        fixture.emit(PlayerEvent.TrackQueued(track, addToFront = false, uri = null))
        fixture.emit(PlayerEvent.TrackQueued(track, addToFront = true, uri = null))
        fixture.emit(PlayerEvent.TrackQueued(track, addToFront = false, uri = url))
        fixture.emit(PlayerEvent.TrackQueued(track, addToFront = true, uri = url))
        advanceUntilIdle()

        assertEquals(
            listOf(
                "Added Artist - Song to the queue",
                "Added Artist - Song to the next position in queue",
                "Added [Artist - Song](https://music.example/audio) to the queue",
                "Added [Artist - Song](https://music.example/audio) to the next position in queue",
            ),
            fixture.sentTexts,
        )
    }

    @Test
    fun `Given a playlist When queued Then send the found list and edit it into the added songs text`() =
        coordinatorTest { fixture ->
            fixture.recordCommand()

            fixture.emit(PlayerEvent.PlaylistQueued("My Mix", trackCount = 5, playlistUrl = null, addToFront = false))
            fixture.emit(
                PlayerEvent.PlaylistQueued(
                    "Next Mix",
                    trackCount = 2,
                    playlistUrl = "https://music.example/list",
                    addToFront = true,
                ),
            )
            advanceUntilIdle()

            assertEquals(
                listOf(
                    "Found track list My Mix with 5 tracks",
                    "Found track list Next Mix with 2 tracks",
                ),
                fixture.sentTexts,
            )
            assertEquals(
                listOf(
                    "Added My Mix (5 tracks) to the queue",
                    "Added [Next Mix](https://music.example/list) (2 tracks) to the next position in queue",
                ),
                fixture.editedTexts,
            )
            assertEquals(
                List(2) { fixture.commandChannelId },
                fixture.editedChannels,
                "the playlist edit stays in the command channel",
            )
            assertEquals(List(2) { fixture.messageId }, fixture.editedMessageIds)
        }

    @Test
    fun `Given no matches When handled Then echo only the event input`() = coordinatorTest { fixture ->
        fixture.recordCommand()

        fixture.emit(PlayerEvent.NoMatches("unknown song"))
        advanceUntilIdle()

        assertEquals(listOf("No matches found for unknown song"), fixture.sentTexts)
    }

    @Test
    fun `Given a load failure carrying a synthetic secret When handled Then never echo provider text`() =
        coordinatorTest { fixture ->
            val secret = "provider-secret-sentinel"
            val credentialUrl = "https://user:$secret@stream.example.invalid/audio?token=$secret"
            fixture.recordCommand()

            fixture.emit(PlayerEvent.LoadFailed(credentialUrl))
            advanceUntilIdle()

            val message = fixture.sentTexts.single()
            assertEquals(
                english(PlayerMessageKeys.LOAD_FAILED, english(PlayerMessageKeys.PLAYER_LOAD_REJECTED)),
                message,
            )
            assertFalse(message.contains(secret), "the reply leaked the provider secret: $message")
            assertFalse(message.contains("stream.example.invalid"), "the reply leaked the provider endpoint")
            assertFalse(message.contains("https://"), "the reply leaked the provider URL")
        }

    @Test
    fun `Given a playback failure When handled Then report the playback category only`() = coordinatorTest { fixture ->
        fixture.recordCommand()

        fixture.emit(PlayerEvent.PlaybackFailed("stream decoder exploded"))
        advanceUntilIdle()

        val message = fixture.sentTexts.single()
        assertEquals(
            english(PlayerMessageKeys.LOAD_FAILED, english(PlayerMessageKeys.PLAYER_PLAYBACK_FAILED)),
            message,
        )
        assertFalse(message.contains("stream decoder exploded"))
    }

    @Test
    fun `Given a fresh guild When the first track starts Then announce now playing and never touch the player channel`() =
        coordinatorTest { fixture ->
            fixture.snapshotValue = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand(channelName = "General")

            fixture.emit(PlayerEvent.TrackStarted(track()))
            advanceUntilIdle()

            assertEquals(
                listOf("🎶 Playing Artist - Song in General"),
                fixture.sentTexts,
            )
            coVerify(exactly = 0) { fixture.channelPort.setUp(any()) }
            coVerify(exactly = 0) { fixture.viewFactory.createPlayerView(any(), any(), any(), any()) }
            assertTrue(
                fixture.sentViews.isEmpty(),
                "only /player may create the embed; a track start must not touch #player",
            )
            assertTrue(fixture.editedViews.isEmpty())
        }

    @Test
    fun `Given an existing player embed When another track starts Then edit it without announcing again`() =
        coordinatorTest { fixture ->
            fixture.snapshotValue = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand()
            fixture.ensurePlayerView()
            advanceUntilIdle()
            val announcements = fixture.sentTexts.toList()

            fixture.snapshotValue = PlayerSnapshot(current = track(title = "Next"), hasActiveTrack = true)
            fixture.emit(PlayerEvent.TrackStarted(track(title = "Next")))
            advanceUntilIdle()

            assertEquals(announcements, fixture.sentTexts, "now playing is announced only once")
            assertEquals(1, fixture.editedViews.size, "the second start must edit the existing embed")
            assertFalse(
                fixture.sentTexts.any { it.contains("Playing Artist - Next") },
                "an existing embed must not re-announce now playing",
            )
        }

    @Test
    fun `Given a renamed target channel When the track starts Then announce the name resolved at track start`() =
        coordinatorTest { fixture ->
            fixture.playback.value = PlayerPlaybackState(revision = 1, activeTrack = true)
            fixture.snapshotValue = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand(channelName = "Stale name")

            fixture.startPlayback("track")
            fixture.emit(PlayerEvent.TrackStarted(track()))
            advanceUntilIdle()

            assertEquals(
                "🎶 Playing Artist - Song in General",
                fixture.sentTexts.first(),
            )
            coVerify(exactly = 1) { fixture.voiceTargets.channelName(ChannelId(VOICE_CHANNEL_ID.toULong())) }
        }

    @Test
    fun `Given an existing player message When later events arrive Then edit it instead of creating another`() =
        coordinatorTest { fixture ->
            fixture.snapshotValue = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand()
            fixture.ensurePlayerView()
            advanceUntilIdle()

            fixture.snapshotValue = PlayerSnapshot(current = track(title = "Next"), hasActiveTrack = true)
            fixture.emit(PlayerEvent.QueueChanged(fixture.snapshotValue))
            fixture.emit(PlayerEvent.TrackEnded(queueEmpty = false))
            advanceUntilIdle()

            coVerify(exactly = 1) { fixture.channelPort.setUp(fixture.view) }
            assertEquals(2, fixture.editedViews.size, "later events must edit the same embed")
            assertEquals(List(2) { fixture.playerChannelId }, fixture.editedChannels)
            assertEquals(List(2) { fixture.messageId }, fixture.editedMessageIds)
        }

    @Test
    fun `Given a stopped player When handled Then edit the embed with the empty state`() = coordinatorTest { fixture ->
        fixture.snapshotValue = PlayerSnapshot(current = track(), hasActiveTrack = true)
        fixture.recordCommand()
        fixture.ensurePlayerView()
        advanceUntilIdle()

        fixture.snapshotValue = PlayerSnapshot()
        fixture.emit(PlayerEvent.Stopped)
        advanceUntilIdle()

        assertEquals(PlayerSnapshot(), fixture.viewSnapshots.last())
        assertEquals(1, fixture.editedViews.size)
    }

    @Test
    fun `Given an idle empty queue When no action runs Then create the player channel with the idle embed`() =
        coordinatorTest { fixture ->
            fixture.recordCommand()

            val reply = fixture.ensurePlayerView()
            advanceUntilIdle()

            assertEquals(english(PlayerMessageKeys.PLAYER_CHANNEL_CREATED, PLAYER_CHANNEL_ID), reply)
            assertEquals(listOf(PlayerSnapshot()), fixture.viewSnapshots)
            coVerify(exactly = 1) { fixture.channelPort.setUp(fixture.view) }
            assertTrue(
                fixture.sentTexts.isEmpty(),
                "the status line is the command reply, not a second chat message",
            )
        }

    @Test
    fun `Given an active track When no action runs Then create the player channel with the current snapshot`() =
        coordinatorTest { fixture ->
            val snapshot = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.snapshotValue = snapshot
            fixture.recordCommand()

            val reply = fixture.ensurePlayerView()
            advanceUntilIdle()

            assertEquals(english(PlayerMessageKeys.PLAYER_CHANNEL_CREATED, PLAYER_CHANNEL_ID), reply)
            assertEquals(listOf(snapshot), fixture.viewSnapshots)
            coVerify(exactly = 1) { fixture.channelPort.setUp(fixture.view) }
        }

    @Test
    fun `Given a reused player channel When no action runs Then reply the existing channel status`() =
        coordinatorTest { fixture ->
            fixture.channelSetup = PlayerChannelSetup.Reused(
                channelId = fixture.playerChannelId,
                messageId = fixture.messageId,
            )
            fixture.snapshotValue = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand()

            val reply = fixture.ensurePlayerView()
            advanceUntilIdle()

            assertEquals(english(PlayerMessageKeys.PLAYER_SHOWN_ON_EXISTING_CHANNEL, PLAYER_CHANNEL_ID), reply)
            assertTrue(
                fixture.sentTexts.isEmpty(),
                "the status line is the command reply, not a second chat message",
            )
            coVerify(exactly = 1) { fixture.channelPort.setUp(fixture.view) }
        }

    @Test
    fun `Given a player channel that cannot be created When no action runs Then fall back to the invoking channel`() =
        coordinatorTest { fixture ->
            fixture.channelSetup = PlayerChannelSetup.Failed
            fixture.snapshotValue = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand()

            val reply = fixture.ensurePlayerView()
            advanceUntilIdle()

            val fallback = fixture.sentViews.single()
            assertEquals(english(PlayerMessageKeys.PLAYER_CHANNEL_CREATION_FAILED), reply)
            assertEquals(english(PlayerMessageKeys.PLAYER_CHANNEL_CREATION_FAILED), fallback.content)
            assertEquals(fixture.view.embed, fallback.embed)
            assertEquals(fixture.commandChannelId, fixture.sentChannelIds.single())
            assertTrue(fixture.editedViews.isEmpty())
        }

    @Test
    fun `Given a channel port that throws When no action runs Then fall back without leaking the failure`() =
        coordinatorTest { fixture ->
            coEvery { fixture.channelPort.setUp(any()) } throws IllegalStateException("rest boom")
            fixture.recordCommand()

            val reply = fixture.ensurePlayerView()
            advanceUntilIdle()

            assertEquals(english(PlayerMessageKeys.PLAYER_CHANNEL_CREATION_FAILED), reply)
            val fallback = fixture.sentViews.single()
            assertEquals(
                english(PlayerMessageKeys.PLAYER_CHANNEL_CREATION_FAILED),
                fallback.content,
                "a failed view construction falls back to a plain warning in the invoking channel",
            )
            assertEquals(null, fallback.embed, "no stale embed may be reused after the failure")
            assertEquals(fixture.commandChannelId, fixture.sentChannelIds.single())
        }

    @Test
    fun `Given a recorded voice channel When a command records no name Then keep the previous name`() =
        coordinatorTest { fixture ->
            fixture.snapshotValue = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand(channelName = "General")
            fixture.coordinator.recordCommand(
                channelId = fixture.commandChannelId,
                guildId = GuildId(GUILD_ID.toULong()),
                guildLocale = null,
            )

            fixture.emit(PlayerEvent.TrackStarted(track()))
            advanceUntilIdle()

            assertEquals("🎶 Playing Artist - Song in General", fixture.sentTexts.single())
        }

    @Test
    fun `Given a live player embed When the embed update is cancelled Then the cancellation escapes and keeps the ids`() =
        coordinatorTest { fixture ->
            fixture.snapshotValue = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand()
            fixture.ensurePlayerView()
            advanceUntilIdle()
            val announcements = fixture.sentTexts.toList()

            fixture.viewCreationFailure = CancellationException("coordinator cancelled")
            fixture.snapshotValue = PlayerSnapshot(current = track(title = "Next"), hasActiveTrack = true)
            fixture.emit(PlayerEvent.QueueChanged(fixture.snapshotValue))
            advanceUntilIdle()
            // The rethrow cancels the serial event loop, so nothing queued after the cancellation is handled.
            fixture.emit(PlayerEvent.NoMatches("after the cancellation"))
            advanceUntilIdle()

            assertEquals(announcements, fixture.sentTexts, "a cancellation must not be announced")
            assertTrue(fixture.editedViews.isEmpty(), "the cancelled update must never reach the port")
            coVerify(exactly = 1) { fixture.channelPort.setUp(any()) }
            assertEquals(
                fixture.playerChannelId,
                fixture.currentPlayerChannelId(),
                "a cancellation must keep the player channel id instead of resetting it like a failure",
            )
            assertEquals(
                fixture.messageId,
                fixture.currentPlayerMessageId(),
                "a cancellation must keep the player message id instead of resetting it like a failure",
            )
        }

    @Test
    fun `Given an idle queue When the leave timer expires Then leave once`() = coordinatorTest { fixture ->
        fixture.snapshotValue = PlayerSnapshot()
        fixture.recordCommand()

        fixture.emit(PlayerEvent.TrackEnded(queueEmpty = true))
        advanceTimeBy(9_999L)
        runCurrent()
        assertEquals(0, fixture.leaveCalls, "the leave timer must wait the full delay")

        advanceTimeBy(2L)
        runCurrent()

        assertEquals(1, fixture.leaveCalls)
    }

    @Test
    fun `Given a track that starts within the leave window When the timer expires Then stay connected`() =
        coordinatorTest { fixture ->
            fixture.snapshotValue = PlayerSnapshot()
            fixture.recordCommand()
            fixture.emit(PlayerEvent.TrackEnded(queueEmpty = true))
            runCurrent()

            fixture.snapshotValue = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.emit(PlayerEvent.TrackStarted(track()))
            advanceTimeBy(10_001L)
            runCurrent()

            assertEquals(0, fixture.leaveCalls)
        }

    @Test
    fun `Given a queued track playlist started track or stop When the leave timer was scheduled Then cancel it`() =
        coordinatorTest { fixture ->
            fixture.snapshotValue = PlayerSnapshot()
            fixture.recordCommand()
            val cancellingEvents = listOf(
                PlayerEvent.TrackQueued(track(), addToFront = false, uri = null),
                PlayerEvent.PlaylistQueued("Mix", trackCount = 1, playlistUrl = null, addToFront = false),
                PlayerEvent.TrackStarted(track()),
                PlayerEvent.Stopped,
            )

            cancellingEvents.forEach { event ->
                fixture.emit(PlayerEvent.TrackEnded(queueEmpty = true))
                runCurrent()
                fixture.emit(event)
                advanceTimeBy(10_001L)
                runCurrent()
            }

            assertEquals(0, fixture.leaveCalls, "every player event must cancel the leave timer")
        }

    @Test
    fun `Given a new play When the leave timer was scheduled Then cancel it`() = coordinatorTest { fixture ->
        fixture.snapshotValue = PlayerSnapshot()
        fixture.recordCommand()
        fixture.emit(PlayerEvent.TrackEnded(queueEmpty = true))
        runCurrent()

        fixture.startPlayback("next")
        advanceTimeBy(10_001L)
        runCurrent()

        assertEquals(0, fixture.leaveCalls, "a new play must cancel the leave timer")
    }

    @Test
    fun `Given a leave timer being installed When a track is queued immediately Then the leave never fires`() =
        coordinatorTest { fixture ->
            fixture.snapshotValue = PlayerSnapshot()
            fixture.recordCommand()

            // No virtual-time gap: the queue event arrives while the timer job is being installed.
            fixture.emit(PlayerEvent.TrackEnded(queueEmpty = true))
            fixture.emit(PlayerEvent.TrackQueued(track(), addToFront = false, uri = null))
            advanceTimeBy(10_001L)
            runCurrent()

            assertEquals(
                0,
                fixture.leaveCalls,
                "a timer reference installed before launch must be cancellable by the next event",
            )
        }

    @Test
    fun `Given a non-idle snapshot or queue When the leave timer expires Then re-check before leaving`() =
        coordinatorTest { fixture ->
            fixture.recordCommand()
            fixture.snapshotValue = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.emit(PlayerEvent.TrackEnded(queueEmpty = true))
            advanceTimeBy(10_001L)
            runCurrent()

            fixture.snapshotValue = PlayerSnapshot(queue = listOf(track()))
            fixture.emit(PlayerEvent.TrackEnded(queueEmpty = true))
            advanceTimeBy(10_001L)
            runCurrent()

            assertEquals(0, fixture.leaveCalls, "an active track or a queued track must keep the bot connected")
        }

    @Test
    fun `Given a load that never starts When playback is requested Then announce the timeout`() = coordinatorTest { fixture ->
        fixture.recordCommand()

        fixture.startPlayback("track")
        advanceUntilIdle()

        assertEquals(listOf(english(PlayerMessageKeys.PLAYER_LOAD_TIMEOUT)), fixture.sentTexts)
        assertTrue(fixture.eventsPort.connected.isEmpty(), "a timed-out load must never connect to voice")
    }

    @Test
    fun `Given a rejected load When playback is requested Then leave the event message and never time out`() =
        coordinatorTest { fixture ->
            coEvery { fixture.engine.loadAndPlay("bad") } coAnswers {
                fixture.playback.value = PlayerPlaybackState(revision = 1, loadFailed = true)
                fixture.eventFlow.tryEmit(PlayerEvent.LoadFailed("provider raw text"))
            }
            fixture.recordCommand()

            fixture.startPlayback("bad")
            advanceUntilIdle()

            val message = fixture.sentTexts.single()
            assertEquals(
                english(PlayerMessageKeys.LOAD_FAILED, english(PlayerMessageKeys.PLAYER_LOAD_REJECTED)),
                message,
            )
            assertFalse(fixture.sentTexts.contains(english(PlayerMessageKeys.PLAYER_LOAD_TIMEOUT)))
            assertTrue(fixture.eventsPort.connected.isEmpty())
        }

    @Test
    fun `Given a started track whose voice session is refused When connecting Then announce the refusal`() =
        coordinatorTest { fixture ->
            coEvery { fixture.engine.loadAndPlay("track") } coAnswers {
                fixture.playback.value = PlayerPlaybackState(revision = 1, activeTrack = true)
            }
            fixture.eventsPort.connectResult = false
            fixture.recordCommand()

            fixture.startPlayback("track")
            advanceUntilIdle()

            assertEquals(
                listOf(english(PlayerMessageKeys.PLAYER_VOICE_SESSION_REFUSED)),
                fixture.sentTexts,
            )
            assertEquals(listOf(fixture.target), fixture.eventsPort.connected)
        }

    @Test
    fun `Given a started track whose connection throws When connecting Then announce the safe failure`() =
        coordinatorTest { fixture ->
            coEvery { fixture.engine.loadAndPlay("track") } coAnswers {
                fixture.playback.value = PlayerPlaybackState(revision = 1, activeTrack = true)
            }
            fixture.eventsPort.connectFailure = IllegalStateException("gateway boom")
            fixture.recordCommand()

            fixture.startPlayback("track")
            advanceUntilIdle()

            assertEquals(
                listOf(english(PlayerMessageKeys.PLAYER_VOICE_CONNECTION_FAILED)),
                fixture.sentTexts,
            )
        }

    @Test
    fun `Given an already active track When a new load is requested Then connect without waiting for a start`() =
        coordinatorTest { fixture ->
            fixture.playback.value = PlayerPlaybackState(revision = 3, activeTrack = true)
            fixture.recordCommand()

            fixture.startPlayback("track")
            advanceUntilIdle()

            assertEquals(listOf(fixture.target), fixture.eventsPort.connected)
            assertTrue(fixture.sentTexts.isEmpty())
        }

    private fun coordinatorTest(block: suspend TestScope.(PlayerMessageCoordinatorFixture) -> Unit) = runTest {
        val fixture = PlayerMessageCoordinatorFixture(this, expected)
        runCurrent()
        try {
            block(fixture)
        } finally {
            fixture.close()
        }
    }

    private suspend fun english(key: String, vararg arguments: Any): String =
        if (arguments.isEmpty()) {
            expected.getString(key = key)
        } else {
            expected.getStringFormat(key = key, arguments = arguments)
        }

    private fun track(title: String = "Song"): PlayerTrack = PlayerTrack(
        identifier = title,
        title = title,
        author = "Artist",
    )

    private class EmptyLocaleRepository : LocaleRepository {

        override suspend fun getGuildLocale(guildId: GuildId): LocaleCode? = null

        override suspend fun setGuildLocale(guildId: GuildId, locale: LocaleCode) =
            error("the coordinator must not persist a guild locale")

        override suspend fun removeGuildLocale(guildId: GuildId) =
            error("the coordinator must not persist a guild locale")

        override suspend fun getAllGuildLocales(): Map<GuildId, LocaleCode> = emptyMap()
    }

    private companion object {
        private const val GUILD_ID = 42L
        private const val PLAYER_CHANNEL_ID = 77L
        private const val VOICE_CHANNEL_ID = 99L

        private val expected = GuildLocalizationService(
            getGuildLocaleUseCase = GetGuildLocaleUseCase(EmptyLocaleRepository()),
            catalog = LocalizationCatalog.load(),
        )
    }
}
