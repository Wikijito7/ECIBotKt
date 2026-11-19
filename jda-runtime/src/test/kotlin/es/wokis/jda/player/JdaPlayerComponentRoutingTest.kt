package es.wokis.jda.player

import es.wokis.commands.neutral.ComponentContext
import es.wokis.commands.neutral.ComponentHandler
import es.wokis.commands.neutral.ComponentResponder
import es.wokis.commands.player.PlayerActionsPort
import es.wokis.commands.player.PlayerQueueSnapshot
import es.wokis.commands.player.QueueMessageViewFactory
import es.wokis.commands.queue.QueuePaginationHandler
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
import es.wokis.player.engine.model.PlayerSnapshot
import es.wokis.player.engine.model.PlayerTrack
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The bootstrap routes the six player buttons to [JdaPlayerButtonHandler] and the two queue arrows
 * to the commands-module pagination handler, so each click reaches exactly one of them.
 */
class JdaPlayerComponentRoutingTest {

    @Test
    fun `Given the player button ids When inspected Then they are exactly the six Kord player controls`() {
        assertEquals(
            listOf(
                ComponentId.PLAYER_PAUSE,
                ComponentId.PLAYER_RESUME,
                ComponentId.PLAYER_SKIP,
                ComponentId.PLAYER_SHUFFLE,
                ComponentId.PLAYER_RECONNECT,
                ComponentId.PLAYER_DISCONNECT,
            ),
            PLAYER_BUTTON_IDS,
        )
    }

    @Test
    fun `Given the queue pagination ids When inspected Then they are exactly the two queue arrows`() {
        assertEquals(listOf(ComponentId.QUEUE_PREVIOUS, ComponentId.QUEUE_NEXT), QUEUE_PAGINATION_IDS)
    }

    @Test
    fun `Given both routed id sets When compared Then they never overlap`() {
        assertTrue(PLAYER_BUTTON_IDS.none { it in QUEUE_PAGINATION_IDS })
        assertFalse(ComponentId.PLAYER_PAUSE in QUEUE_PAGINATION_IDS)
        assertEquals(8, (PLAYER_BUTTON_IDS + QUEUE_PAGINATION_IDS).distinct().size)
    }

    @Test
    fun `Given the bootstrap handlers When a queue arrow is clicked Then the pagination handler edits the page`() {
        val fixture = RoutingFixture()

        try {
            fixture.router.onButtonInteraction(fixture.event("queue_next::1"))

            coVerify(exactly = 1) { fixture.actions.snapshot(any()) }
            coVerify(exactly = 1) { fixture.responder.deferEdit() }
            val edited = slot<MessageView>()
            coVerify(exactly = 1) { fixture.responder.edit(capture(edited)) }
            assertEquals("Page 2 out of 2", edited.captured.embed?.footer)
            assertTrue(fixture.playerCalls().isEmpty(), "a queue arrow must never reach a player button handler")
        } finally {
            fixture.router.close()
        }
    }

    @Test
    fun `Given the bootstrap handlers When a player button is clicked Then no pagination handler runs`() {
        val fixture = RoutingFixture()

        try {
            fixture.router.onButtonInteraction(fixture.event("player_pause"))

            assertEquals(listOf(ComponentId.PLAYER_PAUSE), fixture.playerCalls())
            coVerify(exactly = 0) { fixture.actions.snapshot(any()) }
            coVerify(exactly = 0) { fixture.responder.deferEdit() }
        } finally {
            fixture.router.close()
        }
    }

    @Test
    fun `Given a payload queue arrow When clicked Then the payload suffix never changes the route`() {
        val fixture = RoutingFixture()

        try {
            fixture.router.onButtonInteraction(fixture.event("queue_previous::2"))

            coVerify(exactly = 1) { fixture.actions.snapshot(any()) }
            assertTrue(fixture.playerCalls().isEmpty())
        } finally {
            fixture.router.close()
        }
    }

    private class RoutingFixture {
        val actions: PlayerActionsPort = mockk()
        val responder: ComponentResponder = mockk()
        private val localization = GuildLocalizationService(
            getGuildLocaleUseCase = GetGuildLocaleUseCase(EmptyLocaleRepository()),
            catalog = LocalizationCatalog.load(),
        )
        private val handlers = PLAYER_BUTTON_IDS.map { id -> RecordingHandler(id) }
        val router: JdaComponentRouter

        init {
            coEvery { actions.snapshot(any()) } returns PlayerQueueSnapshot(
                guildName = "Guild",
                snapshot = PlayerSnapshot(
                    queue = listOf(track("a".repeat(600)), track("b".repeat(600))),
                ),
            )
            coJustRun { responder.deferEdit() }
            coJustRun { responder.edit(any()) }
            router = JdaComponentRouter(
                handlers = handlers + QUEUE_PAGINATION_IDS.map { id ->
                    QueuePaginationHandler(id, actions, QueueMessageViewFactory(localization))
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

    private companion object {
        fun track(displayName: String): PlayerTrack = PlayerTrack(
            identifier = displayName,
            title = displayName,
            author = "Artist",
            customName = displayName,
        )
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
