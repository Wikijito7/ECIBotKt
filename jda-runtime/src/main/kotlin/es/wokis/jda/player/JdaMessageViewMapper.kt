package es.wokis.jda.player

import es.wokis.message.ActionRowView
import es.wokis.message.ButtonStyle
import es.wokis.message.ButtonView
import es.wokis.message.EmbedView
import es.wokis.message.MessageView
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.utils.messages.AbstractMessageBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder
import net.dv8tion.jda.api.utils.messages.MessageEditData

/** Maps the neutral message vocabulary to JDA message payloads. */
object JdaMessageViewMapper {

    fun toCreateData(view: MessageView): MessageCreateData =
        MessageCreateBuilder().apply { applyView(view) }.build()

    fun toEditData(view: MessageView): MessageEditData =
        MessageEditBuilder().apply { applyView(view) }.build()

    fun toEmbed(view: EmbedView): MessageEmbed {
        val builder = EmbedBuilder()
            .setColor(view.color)
            .apply { view.title?.let(::setTitle) }
            .apply { view.description?.let(::setDescription) }
            .apply { view.footer?.let(::setFooter) }
            .apply { view.thumbnailUrl?.let(::setThumbnail) }
        view.fields.forEach { builder.addField(it.name, it.value, it.inline) }
        return builder.build()
    }

    fun toActionRow(row: ActionRowView): ActionRow = ActionRow.of(row.buttons.map(::toButton))

    fun toButton(button: ButtonView): Button {
        val plain = Button.of(button.style.toJdaStyle(), button.componentId.value, button.label)
        val base = button.emoji?.let { plain.withEmoji(Emoji.fromUnicode(it)) } ?: plain
        return base.withDisabled(button.disabled)
    }

    private fun AbstractMessageBuilder<*, *>.applyView(view: MessageView) {
        view.content?.takeIf(String::isNotEmpty)?.let(::setContent)
        view.embed?.let { setEmbeds(listOf(toEmbed(it))) }
        view.rows.takeIf(List<ActionRowView>::isNotEmpty)?.let { setComponents(it.map(::toActionRow)) }
    }
}

private fun ButtonStyle.toJdaStyle(): net.dv8tion.jda.api.components.buttons.ButtonStyle =
    when (this) {
        ButtonStyle.PRIMARY -> net.dv8tion.jda.api.components.buttons.ButtonStyle.PRIMARY
        ButtonStyle.SECONDARY -> net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY
        ButtonStyle.SUCCESS -> net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS
        ButtonStyle.DANGER -> net.dv8tion.jda.api.components.buttons.ButtonStyle.DANGER
    }
