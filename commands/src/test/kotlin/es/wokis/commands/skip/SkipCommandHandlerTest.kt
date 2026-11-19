package es.wokis.commands.skip

import es.wokis.commands.neutral.CommandResponse
import es.wokis.commands.player.PlayerActionsPort
import es.wokis.commands.player.PlayerLocalizationFixture
import es.wokis.commands.player.commandContext
import es.wokis.commands.player.playerActor
import es.wokis.commands.player.strictResponder
import es.wokis.domain.locale.LocaleCode
import es.wokis.localization.PlayerMessageKeys
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SkipCommandHandlerTest {

    private val actions: PlayerActionsPort = mockk()
    private val localization = PlayerLocalizationFixture()
    private val handler = SkipCommandHandler(actions, localization.service)

    @Test
    fun `Given the skip handler When inspected Then it owns Kord's command name`() {
        assertEquals("skip", handler.name)
    }

    @Test
    fun `Given a direct message When skip runs Then reply no guild before any player access`() = runTest {
        val responder = strictResponder()

        handler.handle(commandContext(guildId = null), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.ERROR_NO_GUILD)))
        }
        coVerify(exactly = 0) { actions.isVoiceAvailable(any()) }
        coVerify(exactly = 0) { actions.skip(any()) }
    }

    @Test
    fun `Given a caller outside voice When skip runs Then reply no voice channel and never skip`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns false

        handler.handle(commandContext(), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.ERROR_NO_VOICE_CHANNEL)))
        }
        coVerify(exactly = 1) { actions.isVoiceAvailable(playerActor()) }
        coVerify(exactly = 0) { actions.skip(any()) }
    }

    @Test
    fun `Given a voice caller When skip runs Then skip once and confirm unconditionally`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { actions.skip(playerActor()) } returns true

        handler.handle(commandContext(), responder)

        coVerify(exactly = 1) { actions.skip(playerActor()) }
        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.SKIP_COMMAND_RESPONSE)))
        }
    }

    @Test
    fun `Given nothing is playing When skip runs Then still confirm like Kord`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { actions.skip(playerActor()) } returns false

        handler.handle(commandContext(), responder)

        coVerify(exactly = 1) { actions.skip(playerActor()) }
        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.SKIP_COMMAND_RESPONSE)))
        }
    }

    @Test
    fun `Given a guild default locale When skip runs Then reply that locale and forward it to the actor`() = runTest {
        val responder = strictResponder()
        val spanish = LocaleCode("es-ES")
        val actor = playerActor(guildLocale = spanish)
        coEvery { actions.isVoiceAvailable(actor) } returns true
        coEvery { actions.skip(actor) } returns true

        handler.handle(commandContext(guildLocale = spanish), responder)

        coVerify(exactly = 1) { actions.isVoiceAvailable(actor) }
        coVerify(exactly = 1) { actions.skip(actor) }
        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.spanish(PlayerMessageKeys.SKIP_COMMAND_RESPONSE)))
        }
    }
}
