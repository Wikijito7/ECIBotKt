package es.wokis.commands.ask

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.MessageCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import es.wokis.localization.LocalizationKeys
import es.wokis.services.ask.AskExecutor
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService

private const val ASK_CONTEXT_MENU_NAME = "Ask AI"

class AskContextMenuCommand(
    private val askExecutor: AskExecutor,
    private val localizationService: LocalizationService,
    private val guildQueueService: GuildQueueService
) {
    fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        commandBuilder.message(ASK_CONTEXT_MENU_NAME)
    }

    suspend fun onExecute(
        interaction: MessageCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val prompt = interaction.getTarget().content

        if (prompt.isBlank()) {
            response.respond {
                content = localizationService.getString(
                    key = LocalizationKeys.ERROR_NO_CONTENT_PROVIDED,
                    guildId = interaction.data.guildId.value,
                    discordLocale = interaction.guildLocale
                )
            }
            return
        }

        val guildLavaPlayerService = guildQueueService.getOrCreateLavaPlayerService(interaction = interaction)
        askExecutor.execute(
            prompt = prompt,
            guildId = interaction.data.guildId.value,
            discordLocale = interaction.guildLocale,
            response = response,
            guildLavaPlayerService = guildLavaPlayerService
        )
    }
}
