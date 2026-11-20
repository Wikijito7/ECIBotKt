package es.wokis.commands.player

import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.locale.LocaleRepository
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.LocalizationCatalog
import es.wokis.localization.PlayerMessageKeys
import es.wokis.player.PlayerMessagePermanentFailure
import es.wokis.player.engine.event.PlayerEvent
import es.wokis.player.engine.model.PlayerSnapshot
import es.wokis.player.engine.model.PlayerTrack
import io.mockk.coVerify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Embed conflation, failure classification and content-connect contract of
 * [PlayerMessageCoordinator].
 *
 * These cases pin the Kord-parity behavior behind the stress fixes: a burst of embed updates
 * collapses into one trailing edit that renders the live engine snapshot, chat announcements stay
 * per-event and independent from embed backpressure, only permanent edit failures drop the tracked
 * message, and a content command's connect is non-fatal and cancels a pending leave.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerMessageCoordinatorResilienceTest {

    @Test
    fun `Given an in-flight embed edit When a burst of updates arrives Then coalesce into one trailing edit with the live snapshot`() =
        resilienceTest { fixture ->
            fixture.snapshotValue = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand()
            fixture.ensurePlayerView()
            advanceUntilIdle()
            val editsBeforeBurst = fixture.editedViews.size
            val gate = CompletableDeferred<Unit>()
            fixture.viewCreationGate = { gate.await() }

            // A burst with distinct payloads: none of them may win over the engine's live state.
            repeat(5) { index ->
                fixture.snapshotValue = PlayerSnapshot(current = track(title = "Event $index"), hasActiveTrack = true)
                fixture.emit(PlayerEvent.QueueChanged(fixture.snapshotValue))
            }
            advanceUntilIdle()
            assertEquals(
                editsBeforeBurst,
                fixture.editedViews.size,
                "the in-flight edit must hold the window while the burst collapses into one signal",
            )

            val live = PlayerSnapshot(current = track(title = "Live"), hasActiveTrack = true)
            fixture.snapshotValue = live
            fixture.viewCreationGate = null
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                editsBeforeBurst + 2,
                fixture.editedViews.size,
                "one in-flight edit plus exactly one trailing edit for the whole burst",
            )
            assertEquals(live, fixture.viewSnapshots.last(), "the trailing edit must render the live snapshot")
        }

    @Test
    fun `Given a stalled embed edit When queue announcements arrive Then every announcement is sent and none is conflated`() =
        resilienceTest { fixture ->
            fixture.snapshotValue = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand()
            fixture.ensurePlayerView()
            advanceUntilIdle()
            val editsBeforeBurst = fixture.editedViews.size
            val textsBeforeBurst = fixture.sentTexts.size
            val gate = CompletableDeferred<Unit>()
            fixture.viewCreationGate = { gate.await() }

            // Hold one embed edit in flight, then announce three queue changes.
            fixture.snapshotValue = PlayerSnapshot(current = track(title = "Edited"), hasActiveTrack = true)
            fixture.emit(PlayerEvent.QueueChanged(fixture.snapshotValue))
            advanceUntilIdle()
            listOf("One", "Two", "Three").forEach { title ->
                fixture.emit(PlayerEvent.TrackQueued(track(title = title), addToFront = false, uri = null))
            }
            advanceUntilIdle()

            assertEquals(
                textsBeforeBurst + 3,
                fixture.sentTexts.size,
                "chat announcements must stay per-event while the embed edit is stalled",
            )
            assertEquals(
                listOf(
                    "Added Artist - One to the queue",
                    "Added Artist - Two to the queue",
                    "Added Artist - Three to the queue",
                ),
                fixture.sentTexts.takeLast(3),
                "the announcements must not be merged or reordered",
            )
            assertEquals(
                editsBeforeBurst,
                fixture.editedViews.size,
                "the stalled embed must not consume more than one signal",
            )

            fixture.viewCreationGate = null
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(
                editsBeforeBurst + 1,
                fixture.editedViews.size,
                "only the single embed signal renders; the chat events never enqueue an embed edit",
            )
        }

    @Test
    fun `Given a transient embed failure Then keep the ids and retry on the next coalesced signal`() =
        resilienceTest { fixture ->
            fixture.snapshotValue = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand()
            fixture.ensurePlayerView()
            advanceUntilIdle()
            val editsBeforeFailure = fixture.editedViews.size
            fixture.viewEditFailure = IllegalStateException("429 Too Many Requests")

            fixture.snapshotValue = PlayerSnapshot(current = track(title = "Retry"), hasActiveTrack = true)
            fixture.emit(PlayerEvent.QueueChanged(fixture.snapshotValue))
            advanceUntilIdle()

            assertEquals(
                fixture.playerChannelId,
                fixture.currentPlayerChannelId(),
                "a retryable failure must keep the player channel id",
            )
            assertEquals(
                fixture.messageId,
                fixture.currentPlayerMessageId(),
                "a retryable failure must keep the player message id",
            )
            assertEquals(editsBeforeFailure, fixture.editedViews.size, "the failed attempt never reaches the port")

            fixture.viewEditFailure = null
            fixture.snapshotValue = PlayerSnapshot(current = track(title = "Retried"), hasActiveTrack = true)
            fixture.emit(PlayerEvent.QueueChanged(fixture.snapshotValue))
            advanceUntilIdle()

            assertEquals(
                editsBeforeFailure + 1,
                fixture.editedViews.size,
                "the next coalesced signal must retry the edit with the same handle",
            )
        }

    @Test
    fun `Given a permanent embed failure Then drop the ids and rebuild them on the next player command`() =
        resilienceTest { fixture ->
            fixture.snapshotValue = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand()
            fixture.ensurePlayerView()
            advanceUntilIdle()
            fixture.viewEditFailure = PlayerMessagePermanentFailure("Unknown Message")

            fixture.snapshotValue = PlayerSnapshot(current = track(title = "Gone"), hasActiveTrack = true)
            fixture.emit(PlayerEvent.QueueChanged(fixture.snapshotValue))
            advanceUntilIdle()

            assertNull(fixture.currentPlayerChannelId(), "an unknown message must drop the channel id")
            assertNull(fixture.currentPlayerMessageId(), "an unknown message must drop the message id")

            fixture.viewEditFailure = null
            val reply = fixture.ensurePlayerView()
            advanceUntilIdle()

            assertEquals(english(PlayerMessageKeys.PLAYER_CHANNEL_CREATED, PLAYER_CHANNEL_ID), reply)
            assertEquals(fixture.playerChannelId, fixture.currentPlayerChannelId())
            assertEquals(fixture.messageId, fixture.currentPlayerMessageId())
        }

    @Test
    fun `Given a deleted player channel When the embed update runs Then drop the ids and rebuild on the next player command`() =
        resilienceTest { fixture ->
            fixture.snapshotValue = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand()
            fixture.ensurePlayerView()
            advanceUntilIdle()
            val announcements = fixture.sentTexts.toList()

            // The adapter classifies the null channel on edit as permanent with a value-free message;
            // the coordinator only sees the neutral type, so this stands in for that classification.
            fixture.viewEditFailure = PlayerMessagePermanentFailure(
                "The requested player text channel is not available to the bot",
            )
            fixture.snapshotValue = PlayerSnapshot(current = track(title = "Gone"), hasActiveTrack = true)
            fixture.emit(PlayerEvent.QueueChanged(fixture.snapshotValue))
            advanceUntilIdle()

            assertNull(fixture.currentPlayerChannelId(), "a deleted player channel must drop the channel id")
            assertNull(fixture.currentPlayerMessageId(), "a deleted player channel must drop the message id")
            assertEquals(announcements, fixture.sentTexts, "a permanent loss is logged, never chatted")

            // The chat loop is a separate coroutine and must keep serving events after the drop.
            fixture.emit(PlayerEvent.NoMatches("after the channel vanished"))
            advanceUntilIdle()
            assertEquals(
                announcements + listOf("No matches found for after the channel vanished"),
                fixture.sentTexts,
            )

            fixture.viewEditFailure = null
            val reply = fixture.ensurePlayerView()
            advanceUntilIdle()

            assertEquals(english(PlayerMessageKeys.PLAYER_CHANNEL_CREATED, PLAYER_CHANNEL_ID), reply)
            assertEquals(fixture.playerChannelId, fixture.currentPlayerChannelId())
            assertEquals(fixture.messageId, fixture.currentPlayerMessageId())
        }

    @Test
    fun `Given a cancelled embed collector When the restart delay elapses Then embed updates resume and chat was unaffected`() =
        resilienceTest { fixture ->
            fixture.snapshotValue = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand()
            fixture.ensurePlayerView()
            advanceUntilIdle()
            val editsBeforeRetirement = fixture.editedViews.size
            var attempts = 0
            fixture.viewCreationGate = { attempts++ }
            fixture.viewCreationFailure = CancellationException("collector retired once")

            fixture.snapshotValue = PlayerSnapshot(current = track(title = "Cancelled"), hasActiveTrack = true)
            fixture.emit(PlayerEvent.QueueChanged(fixture.snapshotValue))
            runCurrent()

            assertEquals(1, attempts, "the cancelled render must run once and retire the collector")
            assertEquals(editsBeforeRetirement, fixture.editedViews.size, "a cancellation must not reach the port")

            // The per-event chat loop is supervised separately, so it keeps announcing meanwhile.
            fixture.viewCreationFailure = null
            fixture.emit(PlayerEvent.NoMatches("chat survives"))
            runCurrent()
            assertEquals(listOf("No matches found for chat survives"), fixture.sentTexts)

            // The retired collector parks the next signal and renders nothing during the delay.
            fixture.snapshotValue = PlayerSnapshot(current = track(title = "Parked"), hasActiveTrack = true)
            fixture.emit(PlayerEvent.QueueChanged(fixture.snapshotValue))
            runCurrent()
            advanceTimeBy(EMBED_COLLECTOR_RESTART_DELAY_MILLIS - 1)
            runCurrent()

            assertEquals(1, attempts, "no restart may run before the documented delay elapses")
            assertEquals(
                editsBeforeRetirement,
                fixture.editedViews.size,
                "the parked signal must not render while the collector is retired",
            )

            // Crossing the boundary by exactly one millisecond runs the single delayed restart.
            val live = PlayerSnapshot(current = track(title = "Restarted"), hasActiveTrack = true)
            fixture.snapshotValue = live
            advanceTimeBy(1L)
            runCurrent()

            assertEquals(2, attempts, "the restart must consume the parked signal exactly once")
            assertEquals(
                editsBeforeRetirement + 1,
                fixture.editedViews.size,
                "embed updates must resume after the restart",
            )
            assertEquals(live, fixture.viewSnapshots.last(), "the resumed edit must render the live snapshot")
            assertEquals(fixture.playerChannelId, fixture.currentPlayerChannelId(), "a cancellation keeps the handle")
        }

    @Test
    fun `Given a port that always cancels When signals keep arriving Then restarts stay bounded and chat keeps flowing`() =
        resilienceTest { fixture ->
            fixture.snapshotValue = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand()
            fixture.ensurePlayerView()
            advanceUntilIdle()
            var attempts = 0
            fixture.viewCreationGate = { attempts++ }
            fixture.viewCreationFailure = CancellationException("port always cancels")

            fixture.emit(PlayerEvent.QueueChanged(fixture.snapshotValue))
            runCurrent()
            assertEquals(1, attempts)

            repeat(3) { index ->
                // A signal arriving during the delay is parked: landing one millisecond before the
                // boundary must not produce an attempt.
                fixture.emit(PlayerEvent.QueueChanged(fixture.snapshotValue))
                runCurrent()
                assertEquals(index + 1, attempts, "a retired collector must never retry eagerly")

                fixture.emit(PlayerEvent.NoMatches("chat $index"))
                runCurrent()
                advanceTimeBy(EMBED_COLLECTOR_RESTART_DELAY_MILLIS - 1)
                runCurrent()
                assertEquals(
                    index + 1,
                    attempts,
                    "a persistently cancelling port must stay bounded to one attempt per restart window",
                )

                // Crossing the boundary by exactly one millisecond runs the single delayed restart.
                advanceTimeBy(1L)
                runCurrent()
                assertEquals(index + 2, attempts, "exactly one restarted attempt per delay")
            }

            assertEquals(
                listOf(
                    "No matches found for chat 0",
                    "No matches found for chat 1",
                    "No matches found for chat 2",
                ),
                fixture.sentTexts,
                "chat must keep flowing while the embed collector keeps failing",
            )
            assertEquals(fixture.playerChannelId, fixture.currentPlayerChannelId())
            assertEquals(fixture.messageId, fixture.currentPlayerMessageId())
        }

    @Test
    fun `Given a retired collector When the coordinator closes Then no restart renders another embed`() =
        resilienceTest { fixture ->
            fixture.snapshotValue = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand()
            fixture.ensurePlayerView()
            advanceUntilIdle()
            val editsBeforeClose = fixture.editedViews.size
            var attempts = 0
            fixture.viewCreationGate = { attempts++ }
            fixture.viewCreationFailure = CancellationException("collector retired")

            fixture.emit(PlayerEvent.QueueChanged(fixture.snapshotValue))
            runCurrent()
            assertEquals(1, attempts)

            // close() sets the stop flag before it closes the signal, so the loop must not restart.
            fixture.close()
            fixture.emit(PlayerEvent.QueueChanged(fixture.snapshotValue))
            advanceTimeBy(EMBED_COLLECTOR_RESTART_DELAY_MILLIS * 2)
            runCurrent()

            assertEquals(1, attempts, "a closed coordinator must never restart the embed collector")
            assertEquals(editsBeforeClose, fixture.editedViews.size, "no embed may render after close")
        }

    @Test
    fun `Given a scheduled leave When a content command connects Then cancel the leave and record the channel`() =
        resilienceTest { fixture ->
            fixture.snapshotValue = PlayerSnapshot()
            fixture.recordCommand()
            fixture.emit(PlayerEvent.TrackEnded(queueEmpty = true))
            runCurrent()

            fixture.coordinator.connectVoice(fixture.target)
            advanceTimeBy(10_001L)
            runCurrent()

            assertEquals(0, fixture.leaveCalls, "a content connect must cancel the pending leave")
            assertEquals(listOf(fixture.target), fixture.eventsPort.connected)
        }

    @Test
    fun `Given a content connect Then the next now playing resolves the connected channel name`() =
        resilienceTest { fixture ->
            fixture.snapshotValue = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand(channelName = "Stale name")

            fixture.coordinator.connectVoice(fixture.target)
            fixture.emit(PlayerEvent.TrackStarted(track()))
            advanceUntilIdle()

            assertEquals("🎶 Playing Artist - Song in General", fixture.sentTexts.single())
            coVerify(exactly = 1) { fixture.voiceTargets.channelName(ChannelId(VOICE_CHANNEL_ID.toULong())) }
        }

    @Test
    fun `Given a refused content connect Then announce the refusal without throwing`() =
        resilienceTest { fixture ->
            fixture.recordCommand()
            fixture.eventsPort.connectResult = false

            fixture.coordinator.connectVoice(fixture.target)
            advanceUntilIdle()

            assertEquals(listOf(english(PlayerMessageKeys.PLAYER_VOICE_SESSION_REFUSED)), fixture.sentTexts)
        }

    @Test
    fun `Given a content connect that throws Then surface the safe failure without throwing`() =
        resilienceTest { fixture ->
            fixture.recordCommand()
            fixture.eventsPort.connectFailure = IllegalStateException("gateway boom")

            fixture.coordinator.connectVoice(fixture.target)
            advanceUntilIdle()

            assertEquals(listOf(english(PlayerMessageKeys.PLAYER_VOICE_CONNECTION_FAILED)), fixture.sentTexts)
        }

    private fun resilienceTest(block: suspend TestScope.(PlayerMessageCoordinatorFixture) -> Unit) = runTest {
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
        private const val PLAYER_CHANNEL_ID = 77L
        private const val VOICE_CHANNEL_ID = 99L

        /** Mirrors the coordinator's private restart delay; the loop must wait exactly this long. */
        private const val EMBED_COLLECTOR_RESTART_DELAY_MILLIS = 1_000L

        private val expected = GuildLocalizationService(
            getGuildLocaleUseCase = GetGuildLocaleUseCase(EmptyLocaleRepository()),
            catalog = LocalizationCatalog.load(),
        )
    }
}
