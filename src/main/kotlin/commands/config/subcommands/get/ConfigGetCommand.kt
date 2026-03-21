package es.wokis.commands.config

import dev.kord.common.entity.Choice
import dev.kord.common.entity.optional.Optional
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.behavior.interaction.suggest
import dev.kord.core.entity.interaction.AutoCompleteInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import es.wokis.commands.Autocomplete
import es.wokis.commands.CommandName
import es.wokis.commands.SubCommand
import es.wokis.localization.LocalizationKeys
import es.wokis.services.config.ConfigService
import es.wokis.services.localization.LocalizationService

private const val ARGUMENT_SECTION = "section"

class ConfigGetCommand(
    private val configService: ConfigService,
    private val localizationService: LocalizationService
) : SubCommand, Autocomplete {

    private val validSections = listOf("database", "youtube", "deezer", "spotify", "tidal", "kokoro")

    override suspend fun onRegisterCommand(builder: GlobalChatInputCreateBuilder) {
        builder.apply {
            subCommand(CommandName.Config.Get.commandName, localizationService.getString(LocalizationKeys.CONFIG_GET_COMMAND_DESCRIPTION)) {
                descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.CONFIG_GET_SECTION_DESCRIPTION)
                string(ARGUMENT_SECTION, localizationService.getString(LocalizationKeys.CONFIG_GET_SECTION_DESCRIPTION)) {
                    descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.CONFIG_GET_SECTION_DESCRIPTION)
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
        val section = interaction.command.strings[ARGUMENT_SECTION]

        if (section == null || section !in validSections) {
            response.respond {
                content = localizationService.getString(LocalizationKeys.CONFIG_INVALID_SECTION, guildId = guildId, discordLocale = discordLocale)
            }
            return
        }

        val config = configService.config
        val sectionData = when (section) {
            "database" -> config.database
            "youtube" -> config.youtube
            "deezer" -> config.deezer
            "spotify" -> config.spotify
            "tidal" -> config.tidal
            "kokoro" -> config.kokoro
            else -> null
        }

        response.respond {
            content = localizationService.getStringFormat(
                LocalizationKeys.CONFIG_GET_DISPLAY,
                guildId = guildId,
                discordLocale = discordLocale,
                arguments = arrayOf(section, sectionData.toString())
            )
        }
    }

    override suspend fun onAutoComplete(interaction: AutoCompleteInteraction) {
        val input = interaction.command.strings[ARGUMENT_SECTION].orEmpty().lowercase()

        val suggestions = validSections
            .filter { it.lowercase().contains(input) }
            .take(25)
            .map { section ->
                Choice.StringChoice(
                    name = section,
                    nameLocalizations = Optional.Missing(),
                    value = section
                )
            }

        interaction.suggest(suggestions)
    }
}
