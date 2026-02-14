package es.wokis.commands.locale

import dev.kord.common.entity.Choice
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.optional.Optional
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.behavior.interaction.suggest
import dev.kord.core.entity.interaction.AutoCompleteInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import es.wokis.commands.Autocomplete
import es.wokis.commands.Command
import es.wokis.commands.CommandName
import es.wokis.data.locale.DISCORD_LOCALE_MAP
import es.wokis.data.locale.RESET_LOCALE_VALUE
import es.wokis.domain.locale.SetGuildLocaleUseCase
import es.wokis.exceptions.BotException
import es.wokis.localization.LocalizationKeys
import es.wokis.services.localization.LocalizationService

private const val ARGUMENT_LOCALE = "locale"

class LocaleCommand(
    private val localizationService: LocalizationService,
    private val setGuildLocaleUseCase: SetGuildLocaleUseCase
) : Command, Autocomplete {

    override fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        commandBuilder.apply {
            input(
                name = CommandName.Locale.commandName,
                description = localizationService.getLocalizations(LocalizationKeys.LOCALE_COMMAND_DESCRIPTION).values.first()
            ) {
                descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.LOCALE_COMMAND_DESCRIPTION)
                defaultMemberPermissions = Permissions(Permission.Administrator)
                string(
                    name = ARGUMENT_LOCALE,
                    description = localizationService.getLocalizations(LocalizationKeys.LOCALE_COMMAND_INPUT_DESCRIPTION).values.first()
                ) {
                    descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.LOCALE_COMMAND_INPUT_DESCRIPTION)
                    required = true
                    autocomplete = true
                }
            }
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val guildId = interaction.data.guildId.value
        val discordLocale = interaction.guildLocale

        if (guildId == null) {
            throw BotException.UserException.NotInGuildException()
        }

        val localeInput = interaction.command.strings[ARGUMENT_LOCALE]

        if (localeInput.isNullOrEmpty()) {
            throw BotException.UserException.NoContentProvidedException()
        }

        if (localeInput.equals(RESET_LOCALE_VALUE, ignoreCase = true)) {
            setGuildLocaleUseCase.removeLocale(guildId)
            response.respond {
                content = localizationService.getString(
                    key = LocalizationKeys.LOCALE_COMMAND_RESET_SUCCESS,
                    guildId = guildId,
                    discordLocale = discordLocale
                )
            }
            return
        }

        val selectedLocale = DISCORD_LOCALE_MAP[localeInput]

        if (selectedLocale == null) {
            response.respond {
                content = localizationService.getString(
                    key = LocalizationKeys.LOCALE_COMMAND_INVALID_LOCALE,
                    guildId = guildId,
                    discordLocale = discordLocale
                )
            }
            return
        }

        setGuildLocaleUseCase(guildId, selectedLocale)
        response.respond {
            content = localizationService.getStringFormat(
                key = LocalizationKeys.LOCALE_COMMAND_SUCCESS,
                guildId = guildId,
                discordLocale = discordLocale,
                arguments = arrayOf(localeInput)
            )
        }
    }

    override suspend fun onAutoComplete(interaction: AutoCompleteInteraction) {
        val input = interaction.command.strings[ARGUMENT_LOCALE].orEmpty().lowercase()

        val suggestions = if (input.isEmpty()) {
            DISCORD_LOCALE_MAP.entries
        } else {
            DISCORD_LOCALE_MAP.entries.filter { (code, _) ->
                code.lowercase().contains(input)
            }
        }
            .take(25)
            .map { (code, _) ->
                Choice.StringChoice(
                    name = code.take(100),
                    nameLocalizations = Optional.Missing(),
                    value = code
                )
            }

        interaction.suggest(suggestions)
    }
}
