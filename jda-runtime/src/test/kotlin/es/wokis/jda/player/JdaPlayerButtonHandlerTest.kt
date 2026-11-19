package es.wokis.jda.player

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.ComponentContext
import es.wokis.commands.neutral.ComponentResponder
import es.wokis.commands.player.PlayerCommandDefinitions
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import es.wokis.message.ComponentId
import es.wokis.message.EmbedView
import es.wokis.message.MessageView
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class JdaPlayerButtonHandlerTest {

    @Test
    fun `Given each player button When handled Then delegate once and edit the freshly rendered view`() = runTest {
        PLAYER_BUTTON_IDS.forEach { id ->
            val fixture = Fixture(id)

            fixture.handler.handle(context(id), fixture.responder)

            assertEquals(listOf("defer", "action", "render", "edit"), fixture.events, id.name)
            coVerify(exactly = 1) { fixture.controller.applyButton(any(), id) }
            coVerify(exactly = 1) { fixture.controller.applyButton(any(), any()) }
            coVerify(exactly = 1) { fixture.coordinator.playerView(GUILD_LOCALE) }
            val edited = slot<MessageView>()
            coVerify(exactly = 1) { fixture.responder.edit(capture(edited)) }
            assertSame(fixture.view, edited.captured)
        }
    }

    @Test
    fun `Given a guild component When handled Then forward the neutral context as a player command context`() = runTest {
        val fixture = Fixture(ComponentId.PLAYER_PAUSE)
        val captured = slot<CommandContext>()

        fixture.handler.handle(context(ComponentId.PLAYER_PAUSE), fixture.responder)

        coVerify(exactly = 1) { fixture.controller.applyButton(capture(captured), ComponentId.PLAYER_PAUSE) }
        val commandContext = captured.captured
        assertEquals(PlayerCommandDefinitions.PLAYER_COMMAND_NAME, commandContext.commandName)
        assertEquals(GuildId(GUILD_ID.toULong()), commandContext.guildId)
        assertEquals(ChannelId(43uL), commandContext.channelId)
        assertEquals(UserId(44uL), commandContext.userId)
        assertEquals(GUILD_LOCALE, commandContext.locale)
        assertEquals(GUILD_LOCALE, commandContext.guildLocale)
        assertEquals(USER_LOCALE, commandContext.userLocale)
    }

    @Test
    fun `Given a missing guild When handled Then ignore the button without acknowledging`() = runTest {
        val fixture = Fixture(ComponentId.PLAYER_PAUSE)
        every { fixture.jda.getGuildById(any<Long>()) } returns null

        fixture.handler.handle(context(ComponentId.PLAYER_PAUSE), fixture.responder)

        assertEquals(emptyList<String>(), fixture.events)
        coVerify(exactly = 0) { fixture.controller.applyButton(any(), any()) }
        coVerify(exactly = 0) { fixture.responder.deferEdit() }
        coVerify(exactly = 0) { fixture.responder.edit(any()) }
    }

    @Test
    fun `Given a failing controller action When handled Then still edit the current player view`() = runTest {
        val fixture = Fixture(ComponentId.PLAYER_PAUSE)
        coEvery { fixture.controller.applyButton(any(), ComponentId.PLAYER_PAUSE) } throws
            IllegalStateException("playback failed")

        fixture.handler.handle(context(ComponentId.PLAYER_PAUSE), fixture.responder)

        assertEquals(listOf("defer", "render", "edit"), fixture.events)
        coVerify(exactly = 1) { fixture.responder.edit(fixture.view) }
    }

    @Test
    fun `Given a direct message context When handled Then ignore the button before the guild lookup`() = runTest {
        val fixture = Fixture(ComponentId.PLAYER_PAUSE)
        val directMessage = context(ComponentId.PLAYER_PAUSE).copy(guildId = null)

        fixture.handler.handle(directMessage, fixture.responder)

        assertEquals(emptyList<String>(), fixture.events)
        verify(exactly = 0) { fixture.jda.getGuildById(any<Long>()) }
    }

    private fun context(componentId: ComponentId): ComponentContext = ComponentContext(
        componentId = componentId,
        guildId = GuildId(GUILD_ID.toULong()),
        channelId = ChannelId(43uL),
        userId = UserId(44uL),
        locale = GUILD_LOCALE,
        guildLocale = GUILD_LOCALE,
        userLocale = USER_LOCALE,
    )

    private inner class Fixture(id: ComponentId) {
        val jda: JDA = mockk()
        val guild: Guild = mockk()
        val runtime: JdaPlayerRuntime = mockk()
        val controller: JdaPlayerCommandController = mockk()
        val coordinator: JdaPlayerMessageCoordinator = mockk()
        val responder: ComponentResponder = mockk()
        val view = MessageView(embed = EmbedView(title = "Player"))
        val events = mutableListOf<String>()
        val handler = JdaPlayerButtonHandler(id, jda, runtime, controller)

        init {
            every { guild.idLong } returns GUILD_ID
            every { jda.getGuildById(GUILD_ID) } returns guild
            every { runtime.messagesFor(guild) } returns coordinator
            coEvery { coordinator.playerView(GUILD_LOCALE) } answers {
                events += "render"
                view
            }
            coEvery { controller.applyButton(any(), id) } answers { events += "action" }
            coEvery { responder.deferEdit() } answers { events += "defer" }
            coEvery { responder.edit(any()) } answers { events += "edit" }
        }
    }

    private companion object {
        private const val GUILD_ID = 42L
        private val GUILD_LOCALE = LocaleCode("es-ES")
        private val USER_LOCALE = LocaleCode("de")
    }
}
