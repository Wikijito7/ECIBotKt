package es.wokis.commands.sound

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

class SoundCommandHandlerTest {

    private val actions: AudioContentActionsPort = mockk()
    private val soundsCatalog: SoundsCatalogPort = mockk()
    private val localization = PlayerLocalizationFixture()
    private val handler = SoundCommandHandler(actions, soundsCatalog, localization.service)

    @Test
    fun `Given the sound handler When inspected Then it owns Kord's command name`() {
        assertEquals(PlayerCommandDefinitions.SOUND_COMMAND_NAME, handler.name)
        assertEquals("sound", handler.name)
    }

    @Test
    fun `Given a missing or blank name When sound runs Then reply the required field error before the voice guard`() = runTest {
        listOf(emptyMap(), mapOf(PlayerCommandDefinitions.SOUND_OPTION_NAME to "")).forEach { options ->
            val responder = strictResponder()

            handler.handle(commandContext(options = options), responder)

            coVerify(exactly = 1) {
                responder.respond(
                    CommandResponse(
                        localization.englishFormat(PlayerMessageKeys.ERROR_NO_CONTENT_PROVIDED, "name"),
                    ),
                )
            }
            coVerify(exactly = 0) { actions.isVoiceAvailable(any()) }
            coVerify(exactly = 0) { soundsCatalog.soundExists(any()) }
        }
    }

    @Test
    fun `Given a direct message When sound runs Then reply no guild before any voice or catalog access`() = runTest {
        val responder = strictResponder()

        handler.handle(commandContext(guildId = null, options = soundOptions()), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.ERROR_NO_GUILD)))
        }
        coVerify(exactly = 0) { actions.isVoiceAvailable(any()) }
        coVerify(exactly = 0) { soundsCatalog.soundExists(any()) }
    }

    @Test
    fun `Given a caller outside voice When sound runs Then reply no voice channel without probing the catalog`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns false

        handler.handle(commandContext(options = soundOptions()), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.ERROR_NO_VOICE_CHANNEL)))
        }
        coVerify(exactly = 0) { soundsCatalog.soundExists(any()) }
        coVerify(exactly = 0) { actions.playSound(any(), any()) }
    }

    @Test
    fun `Given an unknown sound When sound runs Then reply no matches and never play`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { soundsCatalog.soundExists("rain") } returns false

        handler.handle(commandContext(options = soundOptions("rain")), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.englishFormat(PlayerMessageKeys.NO_MATCHES, "rain")))
        }
        coVerify(exactly = 0) { actions.playSound(any(), any()) }
    }

    @Test
    fun `Given an existing sound When sound runs Then confirm searching and play it once after the reply`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coJustRun { actions.playSound(any(), any()) }
        coEvery { soundsCatalog.soundExists("rain") } returns true

        handler.handle(commandContext(options = soundOptions("rain")), responder)

        coVerify(exactly = 1) { soundsCatalog.soundExists("rain") }
        coVerify(exactly = 1) { actions.playSound(playerActor(), "rain") }
        coVerifyOrder {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.SEARCHING_SONG)))
            actions.playSound(playerActor(), "rain")
        }
    }

    @Test
    fun `Given an existing sound When sound runs Then select it by its exact case`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coJustRun { actions.playSound(any(), any()) }
        coEvery { soundsCatalog.soundExists("Thunder") } returns true

        handler.handle(commandContext(options = soundOptions("Thunder")), responder)

        coVerify(exactly = 1) { soundsCatalog.soundExists("Thunder") }
        coVerify(exactly = 1) { actions.playSound(playerActor(), "Thunder") }
    }

    @Test
    fun `Given a guild default locale When sound runs Then reply that locale and forward it to the actor`() = runTest {
        val responder = strictResponder()
        val spanish = LocaleCode("es-ES")
        val actor = playerActor(guildLocale = spanish)
        coEvery { actions.isVoiceAvailable(actor) } returns true
        coJustRun { actions.playSound(any(), any()) }
        coEvery { soundsCatalog.soundExists("rain") } returns true

        handler.handle(commandContext(guildLocale = spanish, options = soundOptions("rain")), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.spanish(PlayerMessageKeys.SEARCHING_SONG)))
        }
        coVerify(exactly = 1) { actions.playSound(actor, "rain") }
    }

    private fun soundOptions(name: String = "rain"): Map<String, String> =
        mapOf(PlayerCommandDefinitions.SOUND_OPTION_NAME to name)
}
