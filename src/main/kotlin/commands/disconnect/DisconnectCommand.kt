package es.wokis.commands.disconnect

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import es.wokis.commands.Command
import es.wokis.commands.CommandName
import es.wokis.localization.LocalizationKeys
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService

class DisconnectCommand(
    private val guildQueueService: GuildQueueService,
    private val localizationService: LocalizationService
) : Command {

    override fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        commandBuilder.apply {
            input(
                name = CommandName.Disconnect.commandName,
                description = localizationService.getLocalizations(LocalizationKeys.DISCONNECT_COMMAND_DESCRIPTION).values.first()
            ) {
                descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.DISCONNECT_COMMAND_DESCRIPTION)
            }
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val guildId = interaction.data.guildId.value
        val discordLocale = interaction.guildLocale
        val guildLavaPlayerService = guildQueueService.getOrCreateLavaPlayerService(interaction)
        guildLavaPlayerService.stop()
        response.respond {
            content = localizationService.getString(
                key = LocalizationKeys.DISCONNECT_COMMAND_RESPONSE,
                guildId = guildId,
                discordLocale = discordLocale
            )
        }
    }
}
