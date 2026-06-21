package es.wokis.commands.ask

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import es.wokis.commands.Command
import es.wokis.commands.CommandName
import es.wokis.localization.LocalizationKeys
import es.wokis.services.ask.AskExecutor
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import es.wokis.utils.getArgument

private const val ASK_ARGUMENT_NAME = "prompt"

class AskCommand(
    private val askExecutor: AskExecutor,
    private val localizationService: LocalizationService,
    private val guildQueueService: GuildQueueService
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
        val prompt: String = interaction.getArgument(ASK_ARGUMENT_NAME)
            ?: response.respond {
                content = localizationService.getStringFormat(
                    key = LocalizationKeys.ERROR_NO_CONTENT_PROVIDED,
                    guildId = interaction.data.guildId.value,
                    discordLocale = interaction.guildLocale,
                    arguments = arrayOf(ASK_ARGUMENT_NAME)
                )
            }.let { return }

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
