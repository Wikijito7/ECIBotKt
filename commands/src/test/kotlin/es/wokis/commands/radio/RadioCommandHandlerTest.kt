package es.wokis.commands.radio

import es.wokis.commands.neutral.CommandHandler
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.CommandResponse
import es.wokis.commands.player.strictResponder
import es.wokis.domain.locale.LocaleCode
import es.wokis.localization.RadioMessageKeys
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RadioCommandHandlerTest {

    private val play: CommandHandler = recordingHandler()
    private val list: CommandHandler = recordingHandler()
    private val searchByName: CommandHandler = recordingHandler()
    private val searchByCountryCode: CommandHandler = recordingHandler()
    private val random: CommandHandler = recordingHandler()
    private val countryCodes: CommandHandler = recordingHandler()
    private val localization = RadioLocalizationFixture()
    private val handler = RadioCommandHandler(
        play = play,
        list = list,
        searchByName = searchByName,
        searchByCountryCode = searchByCountryCode,
        random = random,
        countryCodes = countryCodes,
        localizationService = localization.service,
    )

    @Test
    fun `Given the radio router When inspected Then it owns Kord's group command name`() {
        assertEquals("radio", handler.name)
    }

    @Test
    fun `Given each top level leaf When routed Then delegate to exactly that handler`() = runTest {
        val cases = listOf(
            "play" to play,
            "list" to list,
            "random" to random,
            "countrycodes" to countryCodes,
        )

        cases.forEach { (subcommand, expected) ->
            val responder = strictResponder()

            handler.handle(radioContext(subcommand = subcommand), responder)

            coVerify(exactly = 1) { expected.handle(any(), responder) }
            coVerify(exactly = 0) { responder.respond(any()) }
        }
    }

    @Test
    fun `Given a search leaf When routed Then delegate to the matching search handler`() = runTest {
        handler.handle(radioContext(subcommandGroup = "search", subcommand = "name"), strictResponder())
        handler.handle(radioContext(subcommandGroup = "search", subcommand = "countrycode"), strictResponder())

        coVerify(exactly = 1) { searchByName.handle(any(), any()) }
        coVerify(exactly = 1) { searchByCountryCode.handle(any(), any()) }
        coVerify(exactly = 0) { play.handle(any(), any()) }
    }

    @Test
    fun `Given an unknown leaf When routed Then answer Kord's unknown message and delegate nothing`() = runTest {
        val responder = strictResponder()

        handler.handle(radioContext(subcommand = "bogus"), responder)

        assertUnknownAnswered(responder)
    }

    @Test
    fun `Given an unknown group When routed Then answer the unknown message and delegate nothing`() = runTest {
        val responder = strictResponder()

        handler.handle(radioContext(subcommandGroup = "bogus", subcommand = "name"), responder)

        assertUnknownAnswered(responder)
    }

    @Test
    fun `Given a search group without a leaf When routed Then answer the unknown message`() = runTest {
        val responder = strictResponder()

        handler.handle(radioContext(subcommandGroup = "search"), responder)

        assertUnknownAnswered(responder)
    }

    @Test
    fun `Given a plain invocation without segments When routed Then answer the unknown message`() = runTest {
        val responder = strictResponder()

        handler.handle(radioContext(), responder)

        assertUnknownAnswered(responder)
    }

    @Test
    fun `Given a guild locale When the leaf is unknown Then answer that locale`() = runTest {
        val responder = strictResponder()
        val spanish = LocaleCode("es-ES")

        handler.handle(radioContext(subcommand = "bogus", guildLocale = spanish), responder)

        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.spanish(RadioMessageKeys.RADIO_UNKNOWN_MESSAGE)))
        }
    }

    private fun assertUnknownAnswered(responder: CommandResponder) {
        coVerify(exactly = 1) {
            responder.respond(CommandResponse(localization.english(RadioMessageKeys.RADIO_UNKNOWN_MESSAGE)))
        }
        listOf(play, list, searchByName, searchByCountryCode, random, countryCodes).forEach { leaf ->
            coVerify(exactly = 0) { leaf.handle(any(), any()) }
        }
    }

    private fun recordingHandler(): CommandHandler = mockk<CommandHandler>().also { delegate ->
        coEvery { delegate.handle(any(), any()) } returns Unit
    }
}
