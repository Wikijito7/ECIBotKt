package es.wokis.commands.radio

import es.wokis.commands.neutral.CommandResponse
import es.wokis.commands.player.AudioContentActionsPort
import es.wokis.commands.player.playerActor
import es.wokis.commands.player.strictResponder
import es.wokis.domain.locale.LocaleCode
import es.wokis.localization.PlayerMessageKeys
import es.wokis.localization.RadioMessageKeys
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RadioPlayCommandHandlerTest {

    private val catalog: RadioCatalogPort = mockk()
    private val actions: AudioContentActionsPort = mockk()
    private val localization = RadioLocalizationFixture()
    private val handler = RadioPlayCommandHandler(catalog, actions, localization.service)

    @Test
    fun `Given the play handler When inspected Then it owns Kord's subcommand name`() {
        assertEquals("play", handler.name)
    }

    @Test
    fun `Given a direct message When play runs Then reply no guild before any voice or catalog access`() = runTest {
        val responder = strictResponder()

        handler.handle(radioContext(options = playOptions("Rock FM"), guildId = null), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.ERROR_NO_GUILD)))
        }
        coVerify(exactly = 0) { actions.isVoiceAvailable(any()) }
        coVerify(exactly = 0) { catalog.findStation(any()) }
    }

    @Test
    fun `Given a caller outside voice When play runs Then reply no voice channel without probing the catalog`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns false

        handler.handle(radioContext(options = playOptions("Rock FM")), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.ERROR_NO_VOICE_CHANNEL)))
        }
        coVerify(exactly = 0) { catalog.findStation(any()) }
        coVerify(exactly = 0) { actions.playRadio(any(), any(), any(), any()) }
    }

    @Test
    fun `Given a missing or empty radio name When play runs Then reply the required field error`() = runTest {
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true

        listOf(emptyMap(), playOptions("")).forEach { options ->
            val responder = strictResponder()

            handler.handle(radioContext(options = options), responder)

            coVerify(exactly = 1) {
                responder.respond(CommandResponse(localization.english(RadioMessageKeys.RADIO_PLAY_REQUIRED)))
            }
        }
        coVerify(exactly = 0) { catalog.findStation(any()) }
        coVerify(exactly = 0) { actions.playRadio(any(), any(), any(), any()) }
    }

    @Test
    fun `Given a found station When play runs Then confirm and play its stream exactly once`() = runTest {
        val station = radioStation("Rock FM")
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { catalog.findStation("Rock FM") } returns station
        coJustRun { actions.playRadio(any(), any(), any(), any()) }

        handler.handle(radioContext(options = playOptions("Rock FM")), responder)

        coVerify(exactly = 1) { catalog.findStation("Rock FM") }
        coVerify(exactly = 1) {
            actions.playRadio(
                actor = playerActor(),
                radioName = "Rock FM",
                streamUrl = station.streamUrl,
                faviconUrl = station.faviconUrl,
            )
        }
        coVerifyOrder {
            responder.respond(CommandResponse(localization.english(RadioMessageKeys.RADIO_PLAY_FOUND)))
            actions.playRadio(any(), any(), any(), any())
        }
    }

    @Test
    fun `Given an unknown station When play runs Then reply not found and never play`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { catalog.findStation("Rock FM") } returns null

        handler.handle(radioContext(options = playOptions("Rock FM")), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(RadioMessageKeys.RADIO_PLAY_NOT_FOUND)))
        }
        coVerify(exactly = 0) { actions.playRadio(any(), any(), any(), any()) }
    }

    @Test
    fun `Given a typed name When play runs Then forward it unchanged because the port applies Kord's quirk`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { catalog.findStation("Rock") } returns null

        handler.handle(radioContext(options = playOptions("Rock")), responder)

        coVerify(exactly = 1) { catalog.findStation("Rock") }
    }

    @Test
    fun `Given a guild default locale When play runs Then reply that locale and forward it to the actor`() = runTest {
        val spanish = LocaleCode("es-ES")
        val station = radioStation("Rock FM")
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor(guildLocale = spanish)) } returns true
        coEvery { catalog.findStation("Rock FM") } returns station
        coJustRun { actions.playRadio(any(), any(), any(), any()) }

        handler.handle(radioContext(options = playOptions("Rock FM"), guildLocale = spanish), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.spanish(RadioMessageKeys.RADIO_PLAY_FOUND)))
        }
        coVerify(exactly = 1) {
            actions.playRadio(
                actor = playerActor(guildLocale = spanish),
                radioName = "Rock FM",
                streamUrl = station.streamUrl,
                faviconUrl = station.faviconUrl,
            )
        }
    }

    private fun playOptions(radioName: String): Map<String, String> =
        mapOf(RadioCommandDefinitions.PLAY_OPTION_NAME to radioName)
}
