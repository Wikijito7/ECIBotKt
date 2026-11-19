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

class RadioRandomCommandHandlerTest {

    private val catalog: RadioCatalogPort = mockk()
    private val actions: AudioContentActionsPort = mockk()
    private val localization = RadioLocalizationFixture()
    private val handler = RadioRandomCommandHandler(catalog, actions, localization.service)

    @Test
    fun `Given the random handler When inspected Then it owns Kord's subcommand name`() {
        assertEquals("random", handler.name)
    }

    @Test
    fun `Given a direct message When random runs Then reply no guild before the catalog or the player`() = runTest {
        val responder = strictResponder()

        handler.handle(radioContext(guildId = null), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.ERROR_NO_GUILD)))
        }
        coVerify(exactly = 0) { actions.isVoiceAvailable(any()) }
        coVerify(exactly = 0) { catalog.randomStation() }
    }

    @Test
    fun `Given a caller outside voice When random runs Then reply no voice channel without a lookup`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns false

        handler.handle(radioContext(), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(PlayerMessageKeys.ERROR_NO_VOICE_CHANNEL)))
        }
        coVerify(exactly = 0) { catalog.randomStation() }
    }

    @Test
    fun `Given a random station When random runs Then answer searching and play it exactly once`() = runTest {
        val station = radioStation("Rock FM")
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { catalog.randomStation() } returns station
        coJustRun { actions.playRadio(any(), any(), any(), any()) }

        handler.handle(radioContext(), responder)

        coVerifyOrder {
            responder.respond(CommandResponse(localization.english(RadioMessageKeys.RADIO_RANDOM_SEARCHING)))
            actions.playRadio(
                actor = playerActor(),
                radioName = "Rock FM",
                streamUrl = station.streamUrl,
                faviconUrl = station.faviconUrl,
            )
        }
        coVerify(exactly = 1) { actions.playRadio(any(), any(), any(), any()) }
    }

    @Test
    fun `Given no random station When random runs Then reply the error and never play`() = runTest {
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { catalog.randomStation() } returns null

        handler.handle(radioContext(), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(RadioMessageKeys.RADIO_RANDOM_ERROR)))
        }
        coVerify(exactly = 0) { actions.playRadio(any(), any(), any(), any()) }
    }

    @Test
    fun `Given a guild default locale When random runs Then reply that locale and forward it to the actor`() = runTest {
        val spanish = LocaleCode("es-ES")
        val station = radioStation("Rock FM")
        val responder = strictResponder()
        coEvery { actions.isVoiceAvailable(playerActor(guildLocale = spanish)) } returns true
        coEvery { catalog.randomStation() } returns station
        coJustRun { actions.playRadio(any(), any(), any(), any()) }

        handler.handle(radioContext(guildLocale = spanish), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.spanish(RadioMessageKeys.RADIO_RANDOM_SEARCHING)))
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
}
