package es.wokis.commands.next

import es.wokis.commands.neutral.CommandResponse
import es.wokis.commands.player.NextTrackResult
import es.wokis.commands.player.PlayerActionsPort
import es.wokis.commands.player.PlayerCommandDefinitions
import es.wokis.commands.player.PlayerLocalizationFixture
import es.wokis.commands.player.commandContext
import es.wokis.commands.player.playerActor
import es.wokis.commands.player.strictResponder
import es.wokis.localization.PlayerMessageKeys
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NextCommandHandlerTest {

    private val actions: PlayerActionsPort = mockk()
    private val localization = PlayerLocalizationFixture()
    private val handler = NextCommandHandler(actions, localization.service)

    @Test
    fun `Given the next handler When inspected Then it owns Kord's command name`() {
        assertEquals("next", handler.name)
    }

    @Test
    fun `Given a blank track option When next runs Then reject it before the voice guard`() = runTest {
        val responder = strictResponder()

        handler.handle(commandContext(options = mapOf(PlayerCommandDefinitions.NEXT_OPTION_TRACK to "   ")), responder)

        coVerify(exactly = 1) {
            responder.respond(
                CommandResponse(
                    localization.englishFormat(
                        PlayerMessageKeys.ERROR_NO_CONTENT_PROVIDED,
                        PlayerCommandDefinitions.NEXT_OPTION_TRACK,
                    ),
                ),
            )
        }
        coVerify(exactly = 0) { actions.isVoiceAvailable(any()) }
        coVerify(exactly = 0) { actions.moveNext(any(), any()) }
        coVerify(exactly = 0) { actions.loadNext(any(), any()) }
    }

    @Test
    fun `Given an omitted track option When next runs Then reject it before the voice guard`() = runTest {
        val responder = strictResponder()

        handler.handle(commandContext(), responder)

        coVerify(exactly = 1) {
            responder.respond(
                CommandResponse(
                    localization.englishFormat(
                        PlayerMessageKeys.ERROR_NO_CONTENT_PROVIDED,
                        PlayerCommandDefinitions.NEXT_OPTION_TRACK,
                    ),
                ),
            )
        }
        coVerify(exactly = 0) { actions.isVoiceAvailable(any()) }
    }

    @Test
    fun `Given a caller outside voice When next runs Then reply no voice channel and load nothing`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns false

        handler.handle(commandContext(options = mapOf(PlayerCommandDefinitions.NEXT_OPTION_TRACK to "song")), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.ERROR_NO_VOICE_CHANNEL)))
        }
        coVerify(exactly = 0) { actions.moveNext(any(), any()) }
        coVerify(exactly = 0) { actions.loadNext(any(), any()) }
    }

    @Test
    fun `Given an https link When next runs Then acknowledge the search and load it next`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { actions.loadNext(playerActor(), "https://example.invalid/track") } returns Unit

        handler.handle(
            commandContext(
                options = mapOf(PlayerCommandDefinitions.NEXT_OPTION_TRACK to "https://example.invalid/track"),
            ),
            responder,
        )

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.SEARCHING_SONG)))
        }
        coVerify(exactly = 1) { actions.loadNext(playerActor(), "https://example.invalid/track") }
        coVerify(exactly = 0) { actions.moveNext(any(), any()) }
    }

    @Test
    fun `Given an http monochrome link When next runs Then load the Tidal rewrite next`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { actions.loadNext(playerActor(), "http://tidal.com/track/1") } returns Unit

        handler.handle(
            commandContext(
                options = mapOf(PlayerCommandDefinitions.NEXT_OPTION_TRACK to "http://monochrome.example/track/1"),
            ),
            responder,
        )

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.SEARCHING_SONG)))
        }
        coVerify(exactly = 1) { actions.loadNext(playerActor(), "http://tidal.com/track/1") }
    }

    @Test
    fun `Given an empty queue When next runs Then report the empty queue and load nothing`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { actions.moveNext(playerActor(), "song") } returns NextTrackResult.QueueEmpty

        handler.handle(commandContext(options = mapOf(PlayerCommandDefinitions.NEXT_OPTION_TRACK to "song")), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.NEXT_EMPTY_QUEUE)))
        }
        coVerify(exactly = 0) { actions.loadNext(any(), any()) }
    }

    @Test
    fun `Given a matching queued track When next runs Then confirm the moved track name`() = runTest {
        val responder = strictResponder()
        val movedTrack = "Artist - Song"
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { actions.moveNext(playerActor(), "song") } returns NextTrackResult.Moved(movedTrack)

        handler.handle(commandContext(options = mapOf(PlayerCommandDefinitions.NEXT_OPTION_TRACK to "song")), responder)

        coVerify(exactly = 1) {
            responder.respond(
                CommandResponse(localization.englishFormat(PlayerMessageKeys.NEXT_TRACK_MOVED, movedTrack)),
            )
        }
        coVerify(exactly = 0) { actions.loadNext(any(), any()) }
    }

    @Test
    fun `Given no queued match When next runs Then report the search fallback and load it next`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { actions.moveNext(playerActor(), "song") } returns NextTrackResult.NotInQueue
        coEvery { actions.loadNext(playerActor(), "song") } returns Unit

        handler.handle(commandContext(options = mapOf(PlayerCommandDefinitions.NEXT_OPTION_TRACK to "song")), responder)

        coVerifyOrder {
            responder.respond(
                CommandResponse(
                    localization.englishFormat(PlayerMessageKeys.NEXT_TRACK_NOT_IN_QUEUE_TRYING_SEARCH, "song"),
                ),
            )
            actions.loadNext(playerActor(), "song")
        }
    }

    @Test
    fun `Given a padded search term When next runs Then search the trimmed value`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { actions.moveNext(playerActor(), "artist song") } returns NextTrackResult.QueueEmpty

        handler.handle(
            commandContext(options = mapOf(PlayerCommandDefinitions.NEXT_OPTION_TRACK to "  artist song  ")),
            responder,
        )

        coVerify(exactly = 1) { actions.moveNext(playerActor(), "artist song") }
    }
}
