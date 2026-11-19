package es.wokis.commands.reconnect

import es.wokis.commands.neutral.CommandResponse
import es.wokis.commands.player.PlayerActionsPort
import es.wokis.commands.player.PlayerLocalizationFixture
import es.wokis.commands.player.PlayerReconnectResult
import es.wokis.commands.player.commandContext
import es.wokis.commands.player.playerActor
import es.wokis.commands.player.strictResponder
import es.wokis.localization.PlayerMessageKeys
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReconnectCommandHandlerTest {

    private val actions: PlayerActionsPort = mockk()
    private val localization = PlayerLocalizationFixture()
    private val handler = ReconnectCommandHandler(actions, localization.service)

    @Test
    fun `Given the reconnect handler When inspected Then it owns Kord's command name`() {
        assertEquals("reconnect", handler.name)
    }

    @Test
    fun `Given a direct message When reconnect runs Then reply no guild and never reconnect`() = runTest {
        val responder = strictResponder()

        handler.handle(commandContext(guildId = null), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.ERROR_NO_GUILD)))
        }
        coVerify(exactly = 0) { actions.reconnect(any()) }
    }

    @Test
    fun `Given a caller outside voice When reconnect runs Then reply no voice channel and never reconnect`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns false

        handler.handle(commandContext(), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.ERROR_NO_VOICE_CHANNEL)))
        }
        coVerify(exactly = 0) { actions.reconnect(any()) }
    }

    @Test
    fun `Given a restored session When reconnect runs Then confirm the success key`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { actions.reconnect(playerActor()) } returns PlayerReconnectResult.RECONNECTED

        handler.handle(commandContext(), responder)

        coVerify(exactly = 1) { actions.reconnect(playerActor()) }
        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.RECONNECT_SUCCESS)))
        }
    }

    @Test
    fun `Given nothing to rebuild When reconnect runs Then reply the not connected key`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { actions.reconnect(playerActor()) } returns PlayerReconnectResult.NOT_CONNECTED

        handler.handle(commandContext(), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.RECONNECT_NOT_CONNECTED)))
        }
    }

    @Test
    fun `Given a failed attempt When reconnect runs Then reply the failure key`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { actions.reconnect(playerActor()) } returns PlayerReconnectResult.FAILED

        handler.handle(commandContext(), responder)

        coVerify(exactly = 1) { actions.reconnect(playerActor()) }
        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.PLAYER_RECONNECT_FAILED)))
        }
    }
}
