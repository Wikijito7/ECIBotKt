package es.wokis.commands.neutral

import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId

/**
 * Gateway-neutral description of one command invocation.
 *
 * [guildId] is nullable because direct messages have no guild. [guildLocale] is the Discord guild
 * default, [userLocale] the client's locale, and [locale] the legacy resolved value (guild locale
 * when available, user locale otherwise), so a user preference is never persisted as the guild
 * default. [stringOptions] keeps narrow commands like `/locale` free of gateway types.
 *
 * [subcommandGroup] and [subcommand] describe a group-command invocation (`/radio search name`
 * maps to group `search` and subcommand `name`). Both are null for a plain command, so existing
 * commands and adapters are unaffected.
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
    val subcommandGroup: String? = null,
    val subcommand: String? = null,
)
