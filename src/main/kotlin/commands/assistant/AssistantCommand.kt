package es.wokis.commands.assistant

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import es.wokis.commands.Command
import es.wokis.commands.CommandName
import es.wokis.exceptions.BotException
import es.wokis.localization.LocalizationKeys
import es.wokis.services.assistant.AssistantService
import es.wokis.services.assistant.AssistantServiceFactory
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import es.wokis.utils.getMemberVoiceChannel

class AssistantCommand(
    private val assistantServiceFactory: AssistantServiceFactory,
    private val localizationService: LocalizationService,
    private val guildQueueService: GuildQueueService
) : Command {

    private var currentAssistantService: AssistantService? = null

    override fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        commandBuilder.apply {
            input(
                name = CommandName.Assistant.commandName,
                description = localizationService.getLocalizations(LocalizationKeys.ASSISTANT_COMMAND_DESCRIPTION).values.first()
            ) {
                descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.ASSISTANT_COMMAND_DESCRIPTION)
            }
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val guildId = interaction.data.guildId.value ?: throw BotException.UserException.NotInGuildException()
        val discordLocale = interaction.guildLocale

        currentAssistantService?.let {
            if (it.isAssistantActive()) {
                response.respond {
                    content = localizationService.getString(
                        key = LocalizationKeys.ASSISTANT_ALREADY_ACTIVE,
                        guildId = guildId,
                        discordLocale = discordLocale
                    )
                }
                return
            }
        }

        val guildLavaPlayerService = guildQueueService.getOrCreateLavaPlayerService(
            guildId = guildId,
            textChannel = interaction.channel.asChannel(),
            voiceChannel = interaction.getMemberVoiceChannel(interaction.kord)!!,
            discordLocale = discordLocale
        )

        if (guildLavaPlayerService.isConnected()) {
            response.respond {
                content = localizationService.getString(
                    key = LocalizationKeys.ASSISTANT_BOT_IN_VOICE,
                    guildId = guildId,
                    discordLocale = discordLocale
                )
            }
            return
        }

        val assistantService = assistantServiceFactory.createAssistantService(
            interaction = interaction,
            guildLavaPlayerService = guildLavaPlayerService
        )
        currentAssistantService = assistantService

        response.respond {
            content = localizationService.getString(
                key = LocalizationKeys.ASSISTANT_LISTENING,
                guildId = guildId,
                discordLocale = discordLocale
            )
        }
        assistantService.startAssistant()
    }
}
