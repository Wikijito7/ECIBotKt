package es.wokis.commands.player

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.CommandResponse
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.PlayerMessageKeys

/**
 * Resolves the guild and voice preconditions Kord enforces through `getOrCreateLavaPlayerService`.
 *
 * Replies the matching localized error and returns null when the caller cannot act, so command
 * handlers only continue with a usable actor.
 */
internal suspend fun CommandResponder.requireVoiceActor(
    context: CommandContext,
    actions: PlayerActionsPort,
    localizationService: GuildLocalizationService,
): PlayerActor? {
    val guildId = context.guildId
    if (guildId == null) {
        respondLocalized(localizationService, PlayerMessageKeys.ERROR_NO_GUILD, context)
        return null
    }
    val actor = PlayerActor(
        guildId = guildId,
        userId = context.userId,
        channelId = context.channelId,
        guildLocale = context.guildLocale,
    )
    if (!actions.isVoiceAvailable(actor)) {
        respondLocalized(localizationService, PlayerMessageKeys.ERROR_NO_VOICE_CHANNEL, context)
        return null
    }
    return actor
}

/** Replies [key] as a public text response resolved through the invoking guild's locale. */
internal suspend fun CommandResponder.respondLocalized(
    localizationService: GuildLocalizationService,
    key: String,
    context: CommandContext,
) {
    respond(CommandResponse(content = localizationService.message(key, context)))
}

/** Replies [key] formatted with [arguments], resolved through the invoking guild's locale. */
internal suspend fun CommandResponder.respondLocalizedFormat(
    localizationService: GuildLocalizationService,
    key: String,
    context: CommandContext,
    vararg arguments: Any,
) {
    respond(CommandResponse(content = localizationService.message(key, context, *arguments)))
}

/** Resolves [key] for the invoking guild; [arguments] are applied only when a format string is used. */
internal suspend fun GuildLocalizationService.message(
    key: String,
    context: CommandContext,
    vararg arguments: Any,
): String = if (arguments.isEmpty()) {
    getString(key = key, guildId = context.guildId, guildDefaultLocale = context.guildLocale)
} else {
    getStringFormat(key, context.guildId, context.guildLocale, *arguments)
}
