package es.wokis.commands.neutral

import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId

/**
 * Gateway-neutral description of one command invocation.
 *
 * [guildId] is nullable because direct messages have no guild. [locale] is the resolved
 * Discord locale for the invocation (guild locale when available, user locale otherwise)
 * or `null` when the adapter could not resolve a supported locale.
 */
data class CommandContext(
    val commandName: String,
    val guildId: GuildId?,
    val channelId: ChannelId,
    val userId: UserId,
    val locale: LocaleCode?,
)
