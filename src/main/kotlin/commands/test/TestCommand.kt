package es.wokis.commands.test

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import es.wokis.commands.Command
import es.wokis.commands.CommandsEnum
import es.wokis.localization.LocalizationKeys
import es.wokis.services.lang.LanguageLocalizationService
import es.wokis.services.queue.GuildQueueService
import es.wokis.utils.getMemberVoiceChannel
import es.wokis.utils.takeIfNotEmpty

class TestCommand(
    private val guildQueueService: GuildQueueService,
    private val languageLocalizationService: LanguageLocalizationService
) : Command {

    override fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        commandBuilder.apply {
            input(
                name = CommandsEnum.TEST.commandName,
                description = languageLocalizationService.getDefaultString(LocalizationKeys.TEST_COMMAND_DESCRIPTION)
            ) {
                descriptionLocalizations = languageLocalizationService.getLocalization(LocalizationKeys.TEST_COMMAND_DESCRIPTION)
                string(
                    name = languageLocalizationService.getDefaultString(LocalizationKeys.TEST_COMMAND_INPUT_NAME),
                    description = languageLocalizationService.getDefaultString(LocalizationKeys.TEST_COMMAND_INPUT_DESCRIPTION)
                ) {
                    nameLocalizations = languageLocalizationService.getLocalization(LocalizationKeys.TEST_COMMAND_INPUT_NAME)
                    descriptionLocalizations = languageLocalizationService.getLocalization(LocalizationKeys.TEST_COMMAND_INPUT_DESCRIPTION)
                    required = true
                }
            }
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val voiceChannel = interaction.getMemberVoiceChannel(interaction.kord)
            ?: response.respond { content = "You need to be in a voice channel" }.let { return }
        val textChannel = interaction.channel.asChannelOrNull()
            ?: response.respond { content = "You need to be in a text channel" }.let { return }
        val guildId = interaction.data.guildId.value
            ?: response.respond { content = "You need to be in a guild" }.let { return }
        val input: String = interaction.command.strings["pepe"]?.takeIfNotEmpty()
            ?: response.respond { content = "You need to give provide a url" }.let { return }
        response.respond { content = "Searching the song" }

        guildQueueService.getOrCreateLavaPlayerService(
            guildId = guildId,
            textChannel = textChannel,
            voiceChannel = voiceChannel
        ).loadAndPlay(input)
    }
}
