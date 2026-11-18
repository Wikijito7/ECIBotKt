package es.wokis.commands.tts

import es.wokis.commands.neutral.CommandResponse
import es.wokis.commands.player.AudioContentActionsPort
import es.wokis.commands.player.PlayerCommandDefinitions
import es.wokis.commands.player.PlayerLocalizationFixture
import es.wokis.commands.player.commandContext
import es.wokis.commands.player.playerActor
import es.wokis.commands.player.strictResponder
import es.wokis.domain.locale.LocaleCode
import es.wokis.localization.PlayerMessageKeys
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TtsCommandHandlerTest {

    private val actions: AudioContentActionsPort = mockk()
    private val localization = PlayerLocalizationFixture()
    private val handler = TtsCommandHandler(actions, localization.service)

    @Test
    fun `Given the tts handler When inspected Then it owns Kord's command name`() {
        assertEquals(PlayerCommandDefinitions.TTS_COMMAND_NAME, handler.name)
        assertEquals("tts", handler.name)
    }

    @Test
    fun `Given a missing or blank message When tts runs Then reply the required field error before the voice guard`() = runTest {
        listOf(emptyMap(), mapOf(PlayerCommandDefinitions.TTS_OPTION_MESSAGE to "")).forEach { options ->
            val responder = strictResponder()

            handler.handle(commandContext(options = options), responder)

            coVerify(exactly = 1) {
                responder.respond(
                    CommandResponse(
                        localization.englishFormat(PlayerMessageKeys.ERROR_NO_CONTENT_PROVIDED, "message"),
                    ),
                )
            }
            coVerify(exactly = 0) { actions.isVoiceAvailable(any()) }
            coVerify(exactly = 0) { actions.loadTts(any(), any()) }
        }
    }

    @Test
    fun `Given a direct message When tts runs Then reply no guild before any voice access`() = runTest {
        val responder = strictResponder()

        handler.handle(commandContext(guildId = null, options = messageOptions()), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.ERROR_NO_GUILD)))
        }
        coVerify(exactly = 0) { actions.isVoiceAvailable(any()) }
        coVerify(exactly = 0) { actions.loadTts(any(), any()) }
    }

    @Test
    fun `Given a caller outside voice When tts runs Then reply no voice channel and never load`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns false

        handler.handle(commandContext(options = messageOptions()), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.ERROR_NO_VOICE_CHANNEL)))
        }
        coVerify(exactly = 0) { actions.loadTts(any(), any()) }
    }

    @Test
    fun `Given a voice caller with text When tts runs Then confirm and queue the text after the reply`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coJustRun { actions.loadTts(any(), any()) }

        handler.handle(commandContext(options = messageOptions("hola mundo")), responder)

        coVerify(exactly = 1) { actions.loadTts(playerActor(), "hola mundo") }
        coVerifyOrder {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.TTS_COMMAND_RESPONSE)))
            actions.loadTts(playerActor(), "hola mundo")
        }
    }

    @Test
    fun `Given text with spaces and special characters When tts runs Then forward the text unencoded to the port`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coJustRun { actions.loadTts(any(), any()) }

        handler.handle(commandContext(options = messageOptions("hola mundo & adiós/¿qué?")), responder)

        coVerify(exactly = 1) { actions.loadTts(playerActor(), "hola mundo & adiós/¿qué?") }
    }

    @Test
    fun `Given a guild default locale When tts runs Then reply that locale and forward it to the actor`() = runTest {
        val responder = strictResponder()
        val spanish = LocaleCode("es-ES")
        val actor = playerActor(guildLocale = spanish)
        coEvery { actions.isVoiceAvailable(actor) } returns true
        coJustRun { actions.loadTts(any(), any()) }

        handler.handle(commandContext(guildLocale = spanish, options = messageOptions()), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.spanish(PlayerMessageKeys.TTS_COMMAND_RESPONSE)))
        }
        coVerify(exactly = 1) { actions.loadTts(actor, "hola mundo") }
    }

    private fun messageOptions(message: String = "hola mundo"): Map<String, String> =
        mapOf(PlayerCommandDefinitions.TTS_OPTION_MESSAGE to message)
}
