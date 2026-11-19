package es.wokis.jda.player

import es.wokis.commands.neutral.ComponentContext
import es.wokis.commands.neutral.ComponentHandler
import es.wokis.commands.neutral.ComponentResponder
import es.wokis.commands.radio.RadioCatalogPort
import es.wokis.commands.radio.RadioCountryCodesPaginationHandler
import es.wokis.commands.radio.RadioMessageViewFactory
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.locale.LocaleRepository
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import es.wokis.jda.command.JdaComponentContextMapper
import es.wokis.jda.command.JdaComponentRouter
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.LocalizationCatalog
import es.wokis.message.ComponentId
import es.wokis.message.MessageView
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The radio arrows are declared by the neutral command surface and routed by the JDA component
 * router, including the country-code pair the accepted routing fix requires.
 */
class JdaRadioComponentRoutingTest {

    @Test
    fun `Given the radio id sets When inspected Then each pair is exactly Kord's two arrows`() {
        assertEquals(listOf(ComponentId.RADIO_LIST_PREVIOUS, ComponentId.RADIO_LIST_NEXT), RADIO_LIST_PAGINATION_IDS)
        assertEquals(
            listOf(ComponentId.RADIO_SEARCH_NAME_PREVIOUS, ComponentId.RADIO_SEARCH_NAME_NEXT),
            RADIO_SEARCH_NAME_PAGINATION_IDS,
        )
        assertEquals(
            listOf(ComponentId.RADIO_SEARCH_COUNTRY_CODE_PREVIOUS, ComponentId.RADIO_SEARCH_COUNTRY_CODE_NEXT),
            RADIO_SEARCH_COUNTRY_CODE_PAGINATION_IDS,
        )
        assertEquals(
            listOf(ComponentId.RADIO_COUNTRYCODES_PREVIOUS, ComponentId.RADIO_COUNTRYCODES_NEXT),
            RADIO_COUNTRY_CODES_PAGINATION_IDS,
        )
    }

    @Test
    fun `Given every routed id set When compared Then no radio arrow overlaps another component`() {
        val routed = PLAYER_BUTTON_IDS + QUEUE_PAGINATION_IDS + SOUNDS_PAGINATION_IDS +
            RADIO_LIST_PAGINATION_IDS + RADIO_SEARCH_NAME_PAGINATION_IDS +
            RADIO_SEARCH_COUNTRY_CODE_PAGINATION_IDS + RADIO_COUNTRY_CODES_PAGINATION_IDS

        assertEquals(18, routed.distinct().size)
        assertTrue(RADIO_LIST_PAGINATION_IDS.none { id -> id in PLAYER_BUTTON_IDS + QUEUE_PAGINATION_IDS })
        assertTrue(RADIO_SEARCH_COUNTRY_CODE_PAGINATION_IDS.none { id -> id in RADIO_SEARCH_NAME_PAGINATION_IDS })
    }

    @Test
    fun `Given the bootstrap radio handlers When a country arrow is clicked Then page two is edited`() = runTest {
        val fixture = RoutingFixture()

        try {
            fixture.router.onButtonInteraction(fixture.event("radio_countrycodes_next::1"))

            coVerify(exactly = 1) { fixture.responder.deferEdit() }
            val edited = slot<MessageView>()
            coVerify(exactly = 1) { fixture.responder.edit(capture(edited)) }
            assertEquals("Page 2 out of 2", edited.captured.embed?.footer)
            assertTrue(fixture.playerCalls().isEmpty(), "a radio arrow must never reach a player handler")
        } finally {
            fixture.router.close()
        }
    }

    @Test
    fun `Given the bootstrap radio handlers When a player button is clicked Then no radio pagination runs`() = runTest {
        val fixture = RoutingFixture()

        try {
            fixture.router.onButtonInteraction(fixture.event("player_pause"))

            assertEquals(listOf(ComponentId.PLAYER_PAUSE), fixture.playerCalls())
            coVerify(exactly = 0) { fixture.responder.deferEdit() }
        } finally {
            fixture.router.close()
        }
    }

    private class RoutingFixture {
        val catalog: RadioCatalogPort = mockk()
        val responder: ComponentResponder = mockk()
        private val localization = GuildLocalizationService(
            getGuildLocaleUseCase = GetGuildLocaleUseCase(EmptyLocaleRepository()),
            catalog = LocalizationCatalog.load(),
        )
        private val handlers = PLAYER_BUTTON_IDS.map { id -> RecordingHandler(id) }
        val router: JdaComponentRouter

        init {
            coEvery { catalog.countryCodes() } returns (1..101).map { index -> "C$index" }
            coJustRun { responder.deferEdit() }
            coJustRun { responder.edit(any()) }
            router = JdaComponentRouter(
                handlers = handlers + RADIO_COUNTRY_CODES_PAGINATION_IDS.map { id ->
                    RadioCountryCodesPaginationHandler(id, catalog, RadioMessageViewFactory(localization))
                },
                contextMapper = mapper(),
                responderFactory = { responder },
                scope = CoroutineScope(Dispatchers.Unconfined),
            )
        }

        fun playerCalls(): List<ComponentId> = handlers.filter { it.calls > 0 }.map { it.id }

        fun event(customId: String): ButtonInteractionEvent {
            val message: Message = mockk()
            every { message.embeds } returns listOf(mockk<MessageEmbed>())
            return mockk<ButtonInteractionEvent>().also { event ->
                every { event.componentId } returns customId
                every { event.message } returns message
            }
        }

        private fun mapper(): JdaComponentContextMapper = mockk<JdaComponentContextMapper>().also { mapper ->
            every { mapper.map(any(), any()) } answers {
                val event = firstArg<ButtonInteractionEvent>()
                ComponentContext(
                    componentId = secondArg(),
                    guildId = GuildId(42uL),
                    channelId = ChannelId(2uL),
                    userId = UserId(3uL),
                    payload = ComponentId.payloadFrom(event.componentId),
                )
            }
        }
    }

    private class RecordingHandler(override val id: ComponentId) : ComponentHandler {
        var calls = 0

        override suspend fun handle(context: ComponentContext, responder: ComponentResponder) {
            calls++
        }
    }

    private class EmptyLocaleRepository : LocaleRepository {
        override suspend fun getGuildLocale(guildId: GuildId): LocaleCode? = null

        override suspend fun setGuildLocale(guildId: GuildId, locale: LocaleCode) =
            error("routing tests must not persist")

        override suspend fun removeGuildLocale(guildId: GuildId) =
            error("routing tests must not persist")

        override suspend fun getAllGuildLocales(): Map<GuildId, LocaleCode> = emptyMap()
    }
}
