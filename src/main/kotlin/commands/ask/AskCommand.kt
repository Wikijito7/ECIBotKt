package es.wokis.commands.ask

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.response.PublicMessageInteractionResponse
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import es.wokis.commands.Command
import es.wokis.commands.CommandName
import es.wokis.localization.LocalizationKeys
import es.wokis.services.ask.AskService
import es.wokis.services.config.ConfigService
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import es.wokis.utils.getArgument

private const val ASK_ARGUMENT_NAME = "prompt"

class AskCommand(
    private val askService: AskService,
    private val localizationService: LocalizationService,
    private val guildQueueService: GuildQueueService,
    private val configService: ConfigService
) : Command {
    override fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        commandBuilder.apply {
            input(
                name = CommandName.Ask.commandName,
                description = localizationService.getLocalizations(LocalizationKeys.ASK_COMMAND_DESCRIPTION).values.first()
            ) {
                descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.ASK_COMMAND_DESCRIPTION)
                string(
                    name = ASK_ARGUMENT_NAME,
                    description = localizationService.getLocalizations(LocalizationKeys.ASK_COMMAND_INPUT_DESCRIPTION).values.first()
                ) {
                    descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.ASK_COMMAND_INPUT_DESCRIPTION)
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
        val prompt: String = interaction.getArgument(ASK_ARGUMENT_NAME)
            ?: response.respond {
                content = localizationService.getStringFormat(
                    key = LocalizationKeys.ERROR_NO_CONTENT_PROVIDED,
                    guildId = guildId,
                    discordLocale = discordLocale,
                    arguments = arrayOf(ASK_ARGUMENT_NAME)
                )
            }.let { return }

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
