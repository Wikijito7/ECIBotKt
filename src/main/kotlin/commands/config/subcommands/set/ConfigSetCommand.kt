package es.wokis.commands.config

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import es.wokis.commands.CommandName
import es.wokis.commands.SubCommand
import es.wokis.data.response.RemoteResponse
import es.wokis.localization.LocalizationKeys
import es.wokis.services.config.ConfigService
import es.wokis.services.localization.LocalizationService
import es.wokis.utils.Log

private const val ARGUMENT_SECTION = "section"
private const val ARGUMENT_KEY = "key"
private const val ARGUMENT_VALUE = "value"

class ConfigSetCommand(
    private val configService: ConfigService,
    private val localizationService: LocalizationService
) : SubCommand {

    override suspend fun onRegisterCommand(builder: GlobalChatInputCreateBuilder) {
        builder.apply {
            subCommand(
                CommandName.Config.Set.commandName,
                localizationService.getString(LocalizationKeys.CONFIG_SET_COMMAND_DESCRIPTION)
            ) {
                descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.CONFIG_SET_COMMAND_DESCRIPTION)
                string(
                    ARGUMENT_SECTION,
                    localizationService.getString(LocalizationKeys.CONFIG_SET_SECTION_DESCRIPTION)
                ) {
                    descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.CONFIG_SET_SECTION_DESCRIPTION)
                    required = true
                }
                string(ARGUMENT_KEY, localizationService.getString(LocalizationKeys.CONFIG_SET_KEY_DESCRIPTION)) {
                    descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.CONFIG_SET_KEY_DESCRIPTION)
                    required = true
                }
                string(ARGUMENT_VALUE, localizationService.getString(LocalizationKeys.CONFIG_SET_VALUE_DESCRIPTION)) {
                    descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.CONFIG_SET_VALUE_DESCRIPTION)
                    required = true
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
        val key = interaction.command.strings[ARGUMENT_KEY]
        val value = interaction.command.strings[ARGUMENT_VALUE]

        val userId = interaction.user.id.value.toString()
        if (!configService.isOwner(userId)) {
            response.respond {
                content = localizationService.getString(
                    LocalizationKeys.CONFIG_AUTH_REQUIRED,
                    guildId = guildId,
                    discordLocale = discordLocale
                )
            }
            return
        }

        if (section == null || section !in CONFIG_VALID_SECTIONS) {
            response.respond {
                content = localizationService.getString(
                    LocalizationKeys.CONFIG_INVALID_SECTION,
                    guildId = guildId,
                    discordLocale = discordLocale
                )
            }
            return
        }

        if (key == null || CONFIG_VALID_SECTIONS[section]?.contains(key) != true) {
            response.respond {
                content = localizationService.getString(
                    LocalizationKeys.CONFIG_INVALID_KEY,
                    guildId = guildId,
                    discordLocale = discordLocale
                )
            }
            return
        }

        if (value.isNullOrEmpty()) {
            response.respond {
                content = localizationService.getString(
                    LocalizationKeys.ERROR_NO_CONTENT_PROVIDED,
                    guildId = guildId,
                    discordLocale = discordLocale
                )
            }
            return
        }

        if (section == "discord_bot_token" || (section == "database" && key == "password")) {
            response.respond {
                content = localizationService.getString(
                    LocalizationKeys.CONFIG_CANNOT_MODIFY_TOKEN,
                    guildId = guildId,
                    discordLocale = discordLocale
                )
            }
            return
        }

        when (val result = configService.updateConfigValue(section, key, value)) {
            is RemoteResponse.Success -> {
                Log.info("Config updated via command: $section.$key = $value by user ${interaction.user.id}")
                response.respond {
                    content = localizationService.getStringFormat(
                        LocalizationKeys.CONFIG_SET_SUCCESS,
                        guildId = guildId,
                        discordLocale = discordLocale,
                        arguments = arrayOf("$section.$key", value)
                    )
                }
            }
            is RemoteResponse.Error -> {
                Log.error("Failed to update config via command: ${result.error.errorMessage}")
                response.respond {
                    content = localizationService.getString(
                        LocalizationKeys.ERROR_UNEXPECTED,
                        guildId = guildId,
                        discordLocale = discordLocale
                    )
                }
            }
        }
    }
}
