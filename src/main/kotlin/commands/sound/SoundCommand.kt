package es.wokis.commands.sound

import dev.kord.common.entity.Choice
import dev.kord.common.entity.optional.Optional
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.behavior.interaction.suggest
import dev.kord.core.entity.interaction.AutoCompleteInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import es.wokis.commands.Autocomplete
import es.wokis.commands.Command
import es.wokis.commands.CommandName
import es.wokis.localization.LocalizationKeys
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import es.wokis.utils.getFolderContent
import es.wokis.utils.orDefaultLocale
import es.wokis.utils.takeIfNotEmpty
import java.io.File

private const val ARGUMENT_NAME = "name"
private const val AUDIO_FOLDER = "./audio/"
private const val AUDIO_EXTENSION = ".mp3"

class SoundCommand(
    private val guildQueueService: GuildQueueService,
    private val localizationService: LocalizationService
) : Command, Autocomplete {

    override fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        commandBuilder.apply {
            input(
                name = CommandName.Sound.commandName,
                description = localizationService.getString(LocalizationKeys.SOUND_COMMAND_DESCRIPTION)
            ) {
                descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.SOUND_COMMAND_DESCRIPTION)
                string(
                    name = ARGUMENT_NAME,
                    description = localizationService.getString(LocalizationKeys.SOUND_COMMAND_INPUT_DESCRIPTION)
                ) {
                    descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.SOUND_COMMAND_INPUT_DESCRIPTION)
                    required = true
                    autocomplete = true
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
            val soundName: String = interaction.command.strings[ARGUMENT_NAME]?.takeIfNotEmpty()
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
            val soundFilePath = "$AUDIO_FOLDER$soundName$AUDIO_EXTENSION"
            val file = File(soundFilePath)

            if (!file.exists()) {
                response.respond {
                    content = localizationService.getStringFormat(
                        key = LocalizationKeys.NO_MATCHES,
                        locale = locale,
                        arguments = arrayOf(soundName)
                    )
                }.let { return }
            }

            guildLavaPlayerService.loadAndPlayMultipleWithCustomName(
                listOf(soundFilePath),
                soundName
            )
        } catch (exc: IllegalStateException) {
            response.respond {
                content = exc.message
            }
        }
    }

    override suspend fun onAutoComplete(interaction: AutoCompleteInteraction) {
        val input = interaction.command.strings[ARGUMENT_NAME].orEmpty()
        input.takeIfNotEmpty()?.let {
            val sounds = getFolderContent(AUDIO_FOLDER)
                .filter { file ->
                    file.nameWithoutExtension.contains(input, ignoreCase = true)
                }
                .take(25)
                .map { file ->
                    Choice.StringChoice(
                        name = file.nameWithoutExtension.take(100),
                        nameLocalizations = Optional.Missing(),
                        value = file.nameWithoutExtension.take(100)
                    )
                }
            interaction.suggest(sounds)
        } ?: interaction.suggest(emptyList())
    }
}
