package es.wokis.commands.shuffle

import es.wokis.commands.neutral.CommandResponse
import es.wokis.commands.player.PlayerActionsPort
import es.wokis.commands.player.PlayerLocalizationFixture
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

class ShuffleCommandHandlerTest {

    private val actions: PlayerActionsPort = mockk()
    private val localization = PlayerLocalizationFixture()
    private val handler = ShuffleCommandHandler(actions, localization.service)

    @Test
    fun `Given the shuffle handler When inspected Then it owns Kord's command name`() {
        assertEquals("shuffle", handler.name)
    }

    @Test
    fun `Given a direct message When shuffle runs Then reply no guild before any player access`() = runTest {
        val responder = strictResponder()

        handler.handle(commandContext(guildId = null), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.ERROR_NO_GUILD)))
        }
        coVerify(exactly = 0) { actions.isVoiceAvailable(any()) }
        coVerify(exactly = 0) { actions.shuffle(any()) }
    }

    @Test
    fun `Given a caller outside voice When shuffle runs Then reply no voice channel and never shuffle`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns false

        handler.handle(commandContext(), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.ERROR_NO_VOICE_CHANNEL)))
        }
        coVerify(exactly = 0) { actions.shuffle(any()) }
    }

    @Test
    fun `Given a voice caller When shuffle runs Then shuffle once and confirm`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { actions.shuffle(playerActor()) } returns true

        handler.handle(commandContext(), responder)

        coVerify(exactly = 1) { actions.shuffle(playerActor()) }
        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.SHUFFLE_COMMAND_RESPONSE)))
        }
    }

    @Test
    fun `Given a queue that did not change When shuffle runs Then still confirm like Kord`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { actions.shuffle(playerActor()) } returns false

        handler.handle(commandContext(), responder)

        coVerify(exactly = 1) { actions.shuffle(playerActor()) }
        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.SHUFFLE_COMMAND_RESPONSE)))
        }
    }
}
