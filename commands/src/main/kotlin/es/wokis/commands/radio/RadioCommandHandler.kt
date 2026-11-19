package es.wokis.commands.radio

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandHandler
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.CommandResponse
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.RadioMessageKeys

/**
 * Neutral `/radio` group router, mirroring Kord's `RadioGroupCommand` dispatch.
 *
 * The JDA adapter resolves the top-level command once; this handler selects the leaf from
 * [CommandContext.subcommandGroup] and [CommandContext.subcommand] and delegates. An unknown leaf
 * answers Kord's `radio_unknown_message` instead of leaving the interaction unanswered.
 */
class RadioCommandHandler(
    private val play: CommandHandler,
    private val list: CommandHandler,
    private val searchByName: CommandHandler,
    private val searchByCountryCode: CommandHandler,
    private val random: CommandHandler,
    private val countryCodes: CommandHandler,
    private val localizationService: GuildLocalizationService,
) : CommandHandler {

    override val name: String = RadioCommandDefinitions.RADIO_COMMAND_NAME

    override suspend fun handle(context: CommandContext, responder: CommandResponder) {
        val handler = resolve(context)
        if (handler == null) {
            responder.respond(
                CommandResponse(
                    content = localizationService.getString(
                        key = RadioMessageKeys.RADIO_UNKNOWN_MESSAGE,
                        guildId = context.guildId,
                        guildDefaultLocale = context.guildLocale,
                    ),
                ),
            )
            return
        }
        handler.handle(context, responder)
    }

    private fun resolve(context: CommandContext): CommandHandler? {
        val group = context.subcommandGroup
        return when {
            group == RadioCommandDefinitions.SEARCH_COMMAND_NAME -> resolveSearch(context.subcommand)
            group != null -> null
            else -> resolveTopLevel(context.subcommand)
        }
    }

    private fun resolveSearch(subcommand: String?): CommandHandler? = when (subcommand) {
        RadioCommandDefinitions.SEARCH_NAME_COMMAND_NAME -> searchByName
        RadioCommandDefinitions.SEARCH_COUNTRY_CODE_COMMAND_NAME -> searchByCountryCode
        else -> null
    }

    private fun resolveTopLevel(subcommand: String?): CommandHandler? = when (subcommand) {
        RadioCommandDefinitions.PLAY_COMMAND_NAME -> play
        RadioCommandDefinitions.LIST_COMMAND_NAME -> list
        RadioCommandDefinitions.RANDOM_COMMAND_NAME -> random
        RadioCommandDefinitions.COUNTRY_CODES_COMMAND_NAME -> countryCodes
        else -> null
    }
}
