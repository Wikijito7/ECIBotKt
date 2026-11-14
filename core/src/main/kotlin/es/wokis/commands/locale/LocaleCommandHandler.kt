package es.wokis.commands.locale

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.neutral.CommandHandler
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.CommandResponse
import es.wokis.commands.neutral.StringOptionDefinition
import es.wokis.domain.locale.DiscordLocaleCatalog
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.locale.SetGuildLocaleUseCase
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.LocaleMessageKeys

/**
 * Neutral, guild-scoped `/locale` command.
 *
 * It preserves the legacy command behaviour: the invoking guild is mandatory, input is required,
 * `reset` is case-insensitive, input is matched against canonical tags with exact case, and the
 * locale is persisted before the confirmation is sent. The adapter decides only how the command
 * is registered and how responses are delivered.
 */
class LocaleCommandHandler(
    private val setGuildLocaleUseCase: SetGuildLocaleUseCase,
    private val localizationService: GuildLocalizationService,
) : CommandHandler {

    override val name: String = COMMAND_NAME

    override suspend fun handle(context: CommandContext, responder: CommandResponder) {
        val guildId = context.guildId
        if (guildId == null) {
            responder.respond(
                CommandResponse(
                    content = localizationService.getString(
                        key = LocaleMessageKeys.ERROR_NO_GUILD,
                        guildDefaultLocale = context.guildLocale,
                    ),
                ),
            )
            return
        }

        val input = context.stringOptions[OPTION_NAME]
        if (input.isNullOrEmpty()) {
            responder.respond(
                CommandResponse(
                    content = localizationService.getStringFormat(
                        key = LocaleMessageKeys.ERROR_NO_CONTENT_PROVIDED,
                        guildId = guildId,
                        guildDefaultLocale = context.guildLocale,
                        arguments = arrayOf(OPTION_NAME),
                    ),
                ),
            )
            return
        }

        if (DiscordLocaleCatalog.isReset(input)) {
            setGuildLocaleUseCase.removeLocale(guildId)
            responder.respond(
                CommandResponse(
                    content = localizationService.getString(
                        key = LocaleMessageKeys.LOCALE_COMMAND_RESET_SUCCESS,
                        guildId = guildId,
                        guildDefaultLocale = context.guildLocale,
                    ),
                ),
            )
            return
        }

        val selectedLocale = DiscordLocaleCatalog.find(input)
        if (selectedLocale == null) {
            responder.respond(
                CommandResponse(
                    content = localizationService.getString(
                        key = LocaleMessageKeys.LOCALE_COMMAND_INVALID_LOCALE,
                        guildId = guildId,
                        guildDefaultLocale = context.guildLocale,
                    ),
                ),
            )
            return
        }

        setGuildLocaleUseCase(guildId, selectedLocale)
        responder.respond(
            CommandResponse(
                content = localizationService.getStringFormat(
                    key = LocaleMessageKeys.LOCALE_COMMAND_SUCCESS,
                    guildId = guildId,
                    guildDefaultLocale = context.guildLocale,
                    arguments = arrayOf(input),
                ),
            ),
        )
    }

    companion object {
        const val COMMAND_NAME: String = "locale"
        const val OPTION_NAME: String = "locale"

        /**
         * Guild-scoped definition used by the JDA adapter. The option is required and
         * autocomplete-driven, and the command is restricted to administrators by default.
         */
        fun definition(
            description: String,
            optionDescription: String,
            descriptionLocalizations: Map<LocaleCode, String> = emptyMap(),
            optionDescriptionLocalizations: Map<LocaleCode, String> = emptyMap(),
        ): CommandDefinition = CommandDefinition(
            name = COMMAND_NAME,
            description = description,
            adminOnly = true,
            stringOption = StringOptionDefinition(
                name = OPTION_NAME,
                description = optionDescription,
                required = true,
                autocomplete = true,
                descriptionLocalizations = optionDescriptionLocalizations,
            ),
            descriptionLocalizations = descriptionLocalizations,
        )
    }
}
