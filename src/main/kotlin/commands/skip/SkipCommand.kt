package es.wokis.commands.skip

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import es.wokis.commands.Command
import es.wokis.commands.CommandsEnum
import es.wokis.localization.LocalizationKeys
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import es.wokis.utils.orDefaultLocale

class SkipCommand(
    private val guildQueueService: GuildQueueService,
    private val localizationService: LocalizationService
) : Command {

    override fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        commandBuilder.apply {
            input(
                name = CommandsEnum.SKIP.commandName,
                description = localizationService.getString(key = LocalizationKeys.SKIP_COMMAND_DESCRIPTION)
            ) {
                descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.SKIP_COMMAND_DESCRIPTION)
            }
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        try {
            val locale = interaction.guildLocale.orDefaultLocale()
            val guildLavaPlayerService = guildQueueService.getOrCreateLavaPlayerService(interaction)
            guildLavaPlayerService.skip()
            response.respond {
                content = localizationService.getString(
                    key = LocalizationKeys.SKIP_COMMAND_RESPONSE,
                    locale = locale
                )
            }
        } catch (exc: IllegalStateException) {
            response.respond {
                content = exc.message
            }
        }
    }
}
