package es.wokis.commands.disconnect

import es.wokis.commands.neutral.CommandResponse
import es.wokis.commands.player.PlayerActionsPort
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

class DisconnectCommandHandlerTest {

    private val actions: PlayerActionsPort = mockk()
    private val localization = PlayerLocalizationFixture()
    private val handler = DisconnectCommandHandler(actions, localization.service)

    @Test
    fun `Given the disconnect handler When inspected Then it owns Kord's command name`() {
        assertEquals("disconnect", handler.name)
    }

    @Test
    fun `Given a direct message When disconnect runs Then reply no guild and touch nothing`() = runTest {
        val responder = strictResponder()

        handler.handle(commandContext(guildId = null), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.ERROR_NO_GUILD)))
        }
        coVerify(exactly = 0) { actions.isVoiceAvailable(any()) }
        coVerify(exactly = 0) { actions.stop(any()) }
        coVerify(exactly = 0) { actions.disconnect(any()) }
    }

    @Test
    fun `Given a caller outside voice When disconnect runs Then reply no voice channel and keep the session`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns false

        handler.handle(commandContext(), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.ERROR_NO_VOICE_CHANNEL)))
        }
        coVerify(exactly = 0) { actions.stop(any()) }
        coVerify(exactly = 0) { actions.disconnect(any()) }
    }

    @Test
    fun `Given a voice caller When disconnect runs Then stop clear and disconnect before confirming`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { actions.stop(playerActor()) } returns true
        coEvery { actions.disconnect(playerActor()) } returns Unit

        handler.handle(commandContext(), responder)

        coVerifyOrder {
            actions.stop(playerActor())
            actions.disconnect(playerActor())
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.DISCONNECT_COMMAND_RESPONSE)))
        }
        coVerify(exactly = 1) { actions.stop(playerActor()) }
        coVerify(exactly = 1) { actions.disconnect(playerActor()) }
    }

    @Test
    fun `Given an idle player When disconnect runs Then still disconnect the voice connection`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { actions.stop(playerActor()) } returns false
        coEvery { actions.disconnect(playerActor()) } returns Unit

        handler.handle(commandContext(), responder)

        coVerify(exactly = 1) { actions.stop(playerActor()) }
        coVerify(exactly = 1) { actions.disconnect(playerActor()) }
        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.DISCONNECT_COMMAND_RESPONSE)))
        }
    }
}
