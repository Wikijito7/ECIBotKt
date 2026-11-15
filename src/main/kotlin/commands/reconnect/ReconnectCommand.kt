package es.wokis.commands.reconnect

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import es.wokis.commands.Command
import es.wokis.commands.CommandName
import es.wokis.localization.LocalizationKeys
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService

class ReconnectCommand(
    private val guildQueueService: GuildQueueService,
    private val localizationService: LocalizationService
) : Command {
    override fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        commandBuilder.input(
            name = CommandName.Reconnect.commandName,
            description = localizationService.getLocalizations(
                LocalizationKeys.RECONNECT_COMMAND_DESCRIPTION
            ).values.first()
        ) {
            descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.RECONNECT_COMMAND_DESCRIPTION)
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val guildId = interaction.data.guildId.value
        val discordLocale = interaction.guildLocale
        val lavaPlayerService = guildQueueService.getOrCreateLavaPlayerService(interaction)

        if (!lavaPlayerService.isConnected()) {
            response.respond {
                content = localizationService.getString(LocalizationKeys.RECONNECT_NOT_CONNECTED, guildId, discordLocale)
            }
            return
        }

        lavaPlayerService.reconnect()
        response.respond {
            content = localizationService.getString(LocalizationKeys.RECONNECT_SUCCESS, guildId, discordLocale)
        }
    }
}
