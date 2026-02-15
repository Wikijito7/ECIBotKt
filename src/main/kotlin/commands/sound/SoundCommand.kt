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
                description = localizationService.getLocalizations(LocalizationKeys.SOUND_COMMAND_DESCRIPTION).values.first()
            ) {
                descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.SOUND_COMMAND_DESCRIPTION)
                string(
                    name = ARGUMENT_NAME,
                    description = localizationService.getLocalizations(LocalizationKeys.SOUND_COMMAND_INPUT_DESCRIPTION).values.first()
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
        val guildId = interaction.data.guildId.value
        val discordLocale = interaction.guildLocale
        val soundName: String = interaction.command.strings[ARGUMENT_NAME]?.takeIfNotEmpty()
            ?: response.respond {
                content = localizationService.getStringFormat(
                    key = LocalizationKeys.ERROR_NO_CONTENT_PROVIDED,
                    guildId = guildId,
                    discordLocale = discordLocale,
                    arguments = arrayOf(ARGUMENT_NAME)
                )
            }.let { return }

        response.respond {
            content = localizationService.getString(
                LocalizationKeys.SEARCHING_SONG,
                guildId = guildId,
                discordLocale = discordLocale
            )
        }

        val guildLavaPlayerService = guildQueueService.getOrCreateLavaPlayerService(interaction = interaction)
        val soundFilePath = "$AUDIO_FOLDER$soundName$AUDIO_EXTENSION"
        val file = File(soundFilePath)

        if (!file.exists()) {
            response.respond {
                content = localizationService.getStringFormat(
                    key = LocalizationKeys.NO_MATCHES,
                    guildId = guildId,
                    discordLocale = discordLocale,
                    arguments = arrayOf(soundName)
                )
            }.let { return }
        }

        guildLavaPlayerService.loadAndPlayMultipleWithCustomName(
            listOf(soundFilePath),
            soundName
        )
    }

    override suspend fun onAutoComplete(interaction: AutoCompleteInteraction) {
        val input = interaction.command.strings[ARGUMENT_NAME].orEmpty()
        input.takeIfNotEmpty()?.let {
            val allFiles = getFolderContent(AUDIO_FOLDER)

            // First: sounds that start with input (sorted alphabetically)
            val startsWithMatches = allFiles
                .filter { file ->
                    file.nameWithoutExtension.startsWith(input, ignoreCase = true)
                }
                .sortedBy { it.nameWithoutExtension.lowercase() }

            // Then: sounds that contain input but don't start with it (sorted alphabetically)
            val containsMatches = allFiles
                .filter { file ->
                    !file.nameWithoutExtension.startsWith(input, ignoreCase = true) &&
                    file.nameWithoutExtension.contains(input, ignoreCase = true)
                }
                .sortedBy { it.nameWithoutExtension.lowercase() }

            // Combine: startsWith first, then contains, up to 25 total
            val sounds = (startsWithMatches + containsMatches)
                .take(25)
                .map { file ->
                    Choice.StringChoice(
                        name = file.nameWithoutExtension.take(100),
                        nameLocalizations = Optional.Missing(),
                        value = file.nameWithoutExtension.take(100)
                    )
                }
                .toList()
            interaction.suggest(sounds)
        } ?: interaction.suggest(emptyList())
    }
}
