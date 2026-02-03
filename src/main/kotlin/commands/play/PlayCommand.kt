package commands.play

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import es.wokis.commands.Command
import es.wokis.commands.CommandName
import es.wokis.localization.LocalizationKeys
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import es.wokis.utils.orDefaultLocale
import es.wokis.utils.takeIfNotEmpty

private const val ARGUMENT_NAME = "sounds"
private const val SOUNDS_SEPARATOR = " "

class PlayCommand(
    private val guildQueueService: GuildQueueService,
    private val localizationService: LocalizationService
) : Command {

    override fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        commandBuilder.apply {
            input(
                name = CommandName.Play.commandName,
                description = localizationService.getString(LocalizationKeys.PLAY_COMMAND_DESCRIPTION)
            ) {
                descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.PLAY_COMMAND_DESCRIPTION)
                string(
                    name = ARGUMENT_NAME,
                    description = localizationService.getString(LocalizationKeys.PLAY_COMMAND_INPUT_DESCRIPTION)
                ) {
                    descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.PLAY_COMMAND_INPUT_DESCRIPTION)
                    required = true
                }
            }
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        try {
            val locale = interaction.guildLocale.orDefaultLocale()
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

            val guildLavaPlayerService = guildQueueService.getOrCreateLavaPlayerService(interaction = interaction)
            val sounds = input.split(SOUNDS_SEPARATOR)
            guildLavaPlayerService.loadAndPlayMultiple(sounds)
        } catch (exc: IllegalStateException) {
            response.respond {
                content = exc.message
            }
        }
    }
}
