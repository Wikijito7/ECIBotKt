package es.wokis.commands.config

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import dev.kord.rest.builder.interaction.subCommand
import es.wokis.commands.CommandName
import es.wokis.commands.SubCommand
import es.wokis.localization.LocalizationKeys
import es.wokis.services.config.ConfigService
import es.wokis.services.localization.LocalizationService
import es.wokis.utils.Log

class ConfigReloadCommand(
    private val configService: ConfigService,
    private val localizationService: LocalizationService
) : SubCommand {

    override suspend fun onRegisterCommand(builder: GlobalChatInputCreateBuilder) {
        builder.apply {
            subCommand(
                CommandName.Config.Reload.commandName,
                localizationService.getString(LocalizationKeys.CONFIG_RELOAD_COMMAND_DESCRIPTION)
            ) {
                descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.CONFIG_RELOAD_COMMAND_DESCRIPTION)
            }
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val guildId = interaction.data.guildId.value
        val discordLocale = interaction.guildLocale
        try {
            configService.reload()
            Log.info("Configuration reloaded via command by user ${interaction.user.id}")
            response.respond {
                content = localizationService.getString(LocalizationKeys.CONFIG_RELOAD_SUCCESS, guildId = guildId, discordLocale = discordLocale)
            }
        } catch (e: Exception) {
            Log.error("Failed to reload config via command", e)
            response.respond {
                content = localizationService.getString(LocalizationKeys.ERROR_UNEXPECTED, guildId = guildId, discordLocale = discordLocale)
            }
        }
    }
}
