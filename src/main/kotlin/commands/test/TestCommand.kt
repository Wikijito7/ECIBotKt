package es.wokis.commands.test

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import es.wokis.commands.Command
import es.wokis.commands.CommandsEnum
import es.wokis.localization.LocalizationKeys
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import es.wokis.utils.getMemberVoiceChannel
import es.wokis.utils.orDefaultLocale
import es.wokis.utils.takeIfNotEmpty

private const val ARGUMENT_NAME = "pepe"

class TestCommand(
    private val guildQueueService: GuildQueueService,
    private val localizationService: LocalizationService
) : Command {

    override fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        commandBuilder.apply {
            input(
                name = CommandsEnum.TEST.commandName,
                description = localizationService.getString(LocalizationKeys.TEST_COMMAND_DESCRIPTION)
            ) {
                descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.TEST_COMMAND_DESCRIPTION)
                string(
                    name = ARGUMENT_NAME,
                    description = localizationService.getString(LocalizationKeys.TEST_COMMAND_INPUT_DESCRIPTION)
                ) {
                    descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.TEST_COMMAND_INPUT_DESCRIPTION)
                    required = true
                }
            }
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val locale = interaction.guildLocale.orDefaultLocale()
        val voiceChannel = interaction.getMemberVoiceChannel(interaction.kord)
            ?: response.respond {
                content = localizationService.getString(LocalizationKeys.ERROR_NO_VOICE_CHANNEL, locale)
            }.let { return }
        val textChannel = interaction.channel.asChannelOrNull()
            ?: response.respond {
                content = localizationService.getString(LocalizationKeys.ERROR_NO_TEXT_CHANNEL, locale)
            }.let { return }
        val guildId = interaction.data.guildId.value
            ?: response.respond {
                content = localizationService.getString(LocalizationKeys.ERROR_NO_GUILD, locale)
            }.let { return }
        val input: String = interaction.command.strings[ARGUMENT_NAME]?.takeIfNotEmpty()
            ?: response.respond {
                content = localizationService.getStringFormat(
                    key = LocalizationKeys.ERROR_NO_CONTENT_PROVIDED,
                    locale = locale,
                    arguments = arrayOf(ARGUMENT_NAME)
                )
            }.let { return }
        response.respond {
            content = localizationService.getString(LocalizationKeys.SEARCHING_SONG, locale)
        }

        guildQueueService.getOrCreateLavaPlayerService(
            guildId = guildId,
            textChannel = textChannel,
            voiceChannel = voiceChannel
        ).loadAndPlay(input)
    }
}
