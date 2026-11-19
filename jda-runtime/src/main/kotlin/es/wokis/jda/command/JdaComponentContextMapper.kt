package es.wokis.jda.command

import es.wokis.commands.neutral.ComponentContext
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.UserId
import es.wokis.message.ComponentId
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent

/**
 * Maps a JDA button event to the neutral component context, splitting guild and client locales the
 * same way the slash mapper does.
 */
class JdaComponentContextMapper {

    fun map(event: ButtonInteractionEvent, componentId: ComponentId): ComponentContext {
        val guildLocale = event.guild?.locale.toLocaleCodeOrNull()
        val userLocale = event.userLocale.toLocaleCodeOrNull()

        return ComponentContext(
            componentId = componentId,
            guildId = event.guild?.idLong?.toGuildId(),
            channelId = ChannelId(event.channel.idLong.toULong()),
            userId = UserId(event.user.idLong.toULong()),
            locale = guildLocale ?: userLocale,
            guildLocale = guildLocale,
            userLocale = userLocale,
        )
    }
}
