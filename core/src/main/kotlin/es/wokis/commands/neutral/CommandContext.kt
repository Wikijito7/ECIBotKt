package es.wokis.commands.neutral

import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId

/**
 * Gateway-neutral description of one command invocation.
 *
 * [guildId] is nullable because direct messages have no guild. [guildLocale] is the Discord guild
 * default, [userLocale] is the invoking client's locale, and [locale] is the legacy resolved value
 * (guild locale when available, user locale otherwise). Keeping the guild default separate from
 * the client locale means a user preference can never be persisted as the guild default.
 *
 * [stringOptions] carries the string option values of the invocation so narrow commands such as
 * `/locale` do not need a gateway type at the boundary.
 */
data class CommandContext(
    val commandName: String,
    val guildId: GuildId?,
    val channelId: ChannelId,
    val userId: UserId,
    val locale: LocaleCode?,
    val guildLocale: LocaleCode? = null,
    val userLocale: LocaleCode? = null,
    val stringOptions: Map<String, String> = emptyMap(),
)
