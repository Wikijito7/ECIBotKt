package es.wokis.jda.player

import es.wokis.message.ActionRowView
import es.wokis.message.ButtonStyle
import es.wokis.message.ButtonView
import es.wokis.message.ComponentId
import es.wokis.message.EmbedFieldView
import es.wokis.message.EmbedView
import es.wokis.message.MessageView
import net.dv8tion.jda.api.components.actionrow.ActionRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import net.dv8tion.jda.api.components.buttons.ButtonStyle as JdaButtonStyle

class JdaMessageViewMapperTest {

    @Test
    fun `Given an embed view When mapped Then title description colour thumbnail fields and footer survive`() {
        val view = EmbedView(
            title = "Player",
            description = "Now playing",
            color = COLOR,
            thumbnailUrl = "https://example.invalid/thumb.png",
            fields = listOf(
                EmbedFieldView(name = "Inline", value = "one", inline = true),
                EmbedFieldView(name = "Block", value = "two", inline = false),
            ),
            footer = "Page 1 out of 2",
        )

        val embed = JdaMessageViewMapper.toEmbed(view)

        assertEquals("Player", embed.title)
        assertEquals("Now playing", embed.description)
        assertEquals(COLOR, embed.colorRaw)
        assertEquals("https://example.invalid/thumb.png", embed.thumbnail?.url)
        assertEquals("Page 1 out of 2", embed.footer?.text)
        assertEquals(2, embed.fields.size)
        assertEquals("Inline", embed.fields[0].name)
        assertEquals("one", embed.fields[0].value)
        assertTrue(embed.fields[0].isInline)
        assertEquals("Block", embed.fields[1].name)
        assertEquals("two", embed.fields[1].value)
        assertFalse(embed.fields[1].isInline)
    }

    @Test
    fun `Given an embed view with only a title When mapped Then every optional part stays absent`() {
        val embed = JdaMessageViewMapper.toEmbed(EmbedView(title = "Player"))

        assertEquals("Player", embed.title)
        assertNull(embed.description)
        assertNull(embed.thumbnail)
        assertNull(embed.footer)
        assertTrue(embed.fields.isEmpty())
        assertEquals(0, embed.colorRaw)
    }

    @Test
    fun `Given every neutral button style When mapped Then the JDA style and component id are preserved`() {
        val buttons = listOf(
            button(ComponentId.PLAYER_PAUSE, ButtonStyle.PRIMARY),
            button(ComponentId.PLAYER_SKIP, ButtonStyle.SECONDARY),
            button(ComponentId.PLAYER_RESUME, ButtonStyle.SUCCESS),
            button(ComponentId.PLAYER_DISCONNECT, ButtonStyle.DANGER),
        )

        val mapped = ActionRow.of(buttons.map(JdaMessageViewMapper::toButton)).buttons

        assertEquals(
            listOf("player_pause", "player_skip", "player_resume", "player_disconnect"),
            mapped.map { it.customId },
        )
        assertEquals(
            listOf(
                JdaButtonStyle.PRIMARY,
                JdaButtonStyle.SECONDARY,
                JdaButtonStyle.SUCCESS,
                JdaButtonStyle.DANGER,
            ),
            mapped.map { it.style },
        )
        assertEquals(listOf("Pause", "Skip", "Resume", "Disconnect"), mapped.map { it.label })
        assertTrue(mapped.none { it.isDisabled })
    }

    @Test
    fun `Given a disabled button with an emoji When mapped Then the flag and unicode emoji survive`() {
        val view = ButtonView(
            componentId = ComponentId.QUEUE_NEXT,
            label = "Next",
            emoji = "⏭️",
            style = ButtonStyle.SECONDARY,
            disabled = true,
        )

        val button = JdaMessageViewMapper.toButton(view)

        assertEquals("queue_next", button.customId)
        assertEquals("Next", button.label)
        assertEquals(JdaButtonStyle.SECONDARY, button.style)
        assertTrue(button.isDisabled)
        assertNotNull(button.emoji)
        assertEquals("⏭️", button.emoji?.name)
    }

    @Test
    fun `Given an enabled button without an emoji When mapped Then it is enabled and carries no emoji`() {
        val button = JdaMessageViewMapper.toButton(button(ComponentId.PLAYER_RECONNECT, ButtonStyle.SUCCESS))

        assertEquals("player_reconnect", button.customId)
        assertFalse(button.isDisabled)
        assertNull(button.emoji)
    }

    @Test
    fun `Given a full message view When mapped to create data Then text embed and rows are all present`() {
        val view = MessageView(
            content = "Header",
            embed = EmbedView(title = "Queue", description = "Two tracks"),
            rows = listOf(
                ActionRowView(
                    buttons = listOf(
                        button(ComponentId.QUEUE_PREVIOUS, ButtonStyle.SECONDARY),
                        button(ComponentId.QUEUE_NEXT, ButtonStyle.PRIMARY),
                    ),
                ),
            ),
        )

        val data = JdaMessageViewMapper.toCreateData(view)

        assertEquals("Header", data.content)
        assertEquals("Queue", data.embeds.single().title)
        val row = data.components.single() as ActionRow
        assertEquals(listOf("queue_previous", "queue_next"), row.buttons.map { it.customId })
    }

    @Test
    fun `Given a full message view When mapped to edit data Then text embed and rows are all present`() {
        val view = MessageView(
            content = "Header",
            embed = EmbedView(title = "Queue", description = "Two tracks"),
            rows = listOf(ActionRowView(buttons = listOf(button(ComponentId.PLAYER_SKIP, ButtonStyle.DANGER)))),
        )

        val data = JdaMessageViewMapper.toEditData(view)

        assertEquals("Header", data.content)
        assertEquals("Queue", data.embeds.single().title)
        val row = data.components.single() as ActionRow
        assertEquals(listOf("player_skip"), row.buttons.map { it.customId })
    }

    @Test
    fun `Given an empty content string When mapped with other parts Then no text is emitted`() {
        val embedView = MessageView(content = "", embed = EmbedView(title = "Queue"))
        val rowView = MessageView(
            content = "",
            rows = listOf(ActionRowView(buttons = listOf(button(ComponentId.PLAYER_PAUSE, ButtonStyle.PRIMARY)))),
        )

        val create = JdaMessageViewMapper.toCreateData(embedView)
        val edit = JdaMessageViewMapper.toEditData(rowView)

        assertTrue(create.content.isNullOrEmpty())
        assertTrue(edit.content.isNullOrEmpty())
        assertEquals("Queue", create.embeds.single().title)
        assertEquals(listOf("player_pause"), (edit.components.single() as ActionRow).buttons.map { it.customId })
    }

    @Test
    fun `Given a text-only view When mapped Then it carries the content and nothing else`() {
        val view = MessageView(content = "Plain text")

        val data = JdaMessageViewMapper.toCreateData(view)

        assertTrue(view.isTextOnly)
        assertEquals("Plain text", data.content)
        assertTrue(data.embeds.isEmpty())
        assertTrue(data.components.isEmpty())
    }

    private fun button(componentId: ComponentId, style: ButtonStyle): ButtonView = ButtonView(
        componentId = componentId,
        label = componentId.value.split('_').last().replaceFirstChar(Char::uppercase),
        style = style,
    )

    private companion object {
        private const val COLOR = 0x112233
    }
}
