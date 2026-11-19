package es.wokis.commands.neutral

import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import es.wokis.message.ComponentId

/** Gateway-neutral description of one component interaction. */
data class ComponentContext(
    val componentId: ComponentId,
    val guildId: GuildId?,
    val channelId: ChannelId,
    val userId: UserId,
    val locale: LocaleCode? = null,
    val guildLocale: LocaleCode? = null,
    val userLocale: LocaleCode? = null,
)
