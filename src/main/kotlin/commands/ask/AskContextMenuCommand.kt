package es.wokis.commands.ask

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.MessageCommandInteraction
import dev.kord.core.entity.interaction.response.PublicMessageInteractionResponse
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import es.wokis.localization.LocalizationKeys
import es.wokis.services.ask.AskService
import es.wokis.services.config.ConfigService
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService

private const val ASK_CONTEXT_MENU_NAME = "Ask AI"

class AskContextMenuCommand(
    private val askService: AskService,
    private val localizationService: LocalizationService,
    private val guildQueueService: GuildQueueService,
    private val configService: ConfigService
) {
    fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        commandBuilder.message(ASK_CONTEXT_MENU_NAME)
    }

    suspend fun onExecute(
        interaction: MessageCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val guildId = interaction.data.guildId.value
        val discordLocale = interaction.guildLocale

        if (!configService.config.ask.enabled) {
            response.respond {
                content = localizationService.getString(
                    key = LocalizationKeys.ASK_NOT_ENABLED,
                    guildId = guildId,
                    discordLocale = discordLocale
                )
            }
            return
        }

        val prompt = interaction.getTarget().content

        if (prompt.isBlank()) {
            response.respond {
                content = localizationService.getString(
                    key = LocalizationKeys.ERROR_NO_CONTENT_PROVIDED,
                    guildId = guildId,
                    discordLocale = discordLocale
                )
            }
            return
        }

        val initialResponse: PublicMessageInteractionResponse = response.respond {
            content = localizationService.getString(
                key = LocalizationKeys.ASK_THINKING,
                guildId = guildId,
                discordLocale = discordLocale
            )
        }

        try {
            val guildLavaPlayerService = guildQueueService.getOrCreateLavaPlayerService(interaction = interaction)
            val answer = askService.askAndPlayTTS(prompt, guildLavaPlayerService)
            initialResponse.edit {
                content = answer
            }
        } catch (e: Exception) {
            initialResponse.edit {
                content = localizationService.getString(
                    key = LocalizationKeys.ERROR_API_UNEXPECTED,
                    guildId = guildId,
                    discordLocale = discordLocale
                )
            }
        }
    }
}
