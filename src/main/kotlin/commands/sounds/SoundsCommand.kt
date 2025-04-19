package es.wokis.commands.sounds

import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.message.EmbedBuilder
import es.wokis.commands.Command
import es.wokis.commands.CommandsEnum
import es.wokis.commands.Component
import es.wokis.commands.ComponentsEnum
import es.wokis.commands.commons.createPaginatedEmbedMessage
import es.wokis.constants.BLANK_SPACE
import es.wokis.localization.LocalizationKeys
import es.wokis.services.localization.LocalizationService
import es.wokis.utils.getFolderContent
import es.wokis.utils.orDefaultLocale
import java.io.File

private const val SOUNDS_PATH = "./audio"
private const val MAX_SOUNDS_PER_COLUMN = 50

class SoundsCommand(
    private val localizationService: LocalizationService
) : Command, Component {
    override fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        commandBuilder.apply {
            input(
                name = CommandsEnum.SOUNDS.commandName,
                description = localizationService.getString(LocalizationKeys.SOUNDS_DESCRIPTION)
            ) {
                descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.SOUNDS_DESCRIPTION)
            }
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        try {
            val locale = interaction.guildLocale.orDefaultLocale()
            val sounds: List<File> = getSoundFilesSorted()
            val displaySounds = getDisplaySounds(sounds).sorted()
            val title = localizationService.getString(key = LocalizationKeys.SOUNDS_EMBED_TITLE, locale = locale)
            val description = localizationService.getStringFormat(
                key = LocalizationKeys.SOUNDS_EMBED_DESCRIPTION,
                locale = locale,
                arguments = arrayOf(sounds.size)
            )
            val columns = 3
            val currentPageContent = displaySounds.chunked(columns).firstOrNull()
            val pageCount = displaySounds.size / columns
            response.respond {
                createPaginatedEmbedMessage(
                    locale = locale,
                    localizationService = localizationService,
                    title = title,
                    description = description,
                    currentPage = 1,
                    columns = columns,
                    currentPageContent = currentPageContent,
                    pageCount = pageCount,
                    previousButtonCustomId = ComponentsEnum.SOUNDS_PREVIOUS.customId,
                    nextButtonCustomId = ComponentsEnum.SOUNDS_NEXT.customId
                )
            }
        } catch (exc: IllegalStateException) {
            response.respond {
                content = exc.message
            }
        }
    }

    override suspend fun onInteract(interaction: ComponentInteraction) {
        val interactionCustomId = (interaction as? ButtonInteraction)?.component?.customId
        val updatePageBy = if (interactionCustomId == ComponentsEnum.QUEUE_PREVIOUS.customId) -1 else 1
        val sounds: List<File> = getSoundFilesSorted()
        val displayQueue = getDisplaySounds(sounds)
        val columns = 3
        val pageCount = displayQueue.size / columns
        val currentPage = interaction.message.embeds.firstOrNull()
            ?.footer?.text?.split(" ")?.get(1)?.toIntOrNull()?.plus(updatePageBy)
            ?.takeUnless { it > pageCount } ?: 1
        val displaySoundsPage = displayQueue.chunked(columns).getOrNull(currentPage - 1)
        updateQueueMessage(
            interaction = interaction,
            currentPage = currentPage,
            soundsCount = sounds.size,
            displaySoundsPage = displaySoundsPage,
            pageCount = pageCount,
            columns = columns
        )
    }

    private fun getSoundFilesSorted() = getFolderContent(SOUNDS_PATH).sortedBy { it.nameWithoutExtension }

    private fun getDisplaySounds(sounds: List<File>): List<String> {
        if (sounds.isEmpty()) return emptyList()
        var currentString = sounds.first().nameWithoutExtension
        val displaySounds: MutableList<String> = mutableListOf()

        sounds.drop(1).forEach { sound ->
            val soundName = sound.nameWithoutExtension
            val separator = "$BLANK_SPACE$BLANK_SPACE\n"
            val appendDisplayTrackName = separator.plus(soundName)
            if (currentString.split(separator).size < MAX_SOUNDS_PER_COLUMN && currentString.length + appendDisplayTrackName.length <= EmbedBuilder.Field.Limits.value) {
                currentString += appendDisplayTrackName
            } else {
                displaySounds.add(currentString)
                currentString = soundName
            }
        }
        if (currentString.isNotEmpty()) {
            displaySounds.add(currentString)
        }
        return displaySounds.toList()
    }

    private suspend fun updateQueueMessage(
        interaction: ComponentInteraction,
        currentPage: Int,
        soundsCount: Int,
        displaySoundsPage: List<String>?,
        pageCount: Int,
        columns: Int
    ) {
        val locale = interaction.guildLocale.orDefaultLocale()
        val title = localizationService.getString(key = LocalizationKeys.SOUNDS_EMBED_TITLE, locale = locale)
        val description = localizationService.getStringFormat(
            key = LocalizationKeys.SOUNDS_EMBED_DESCRIPTION,
            locale = locale,
            arguments = arrayOf(soundsCount)
        )
        interaction.message.edit {
            createPaginatedEmbedMessage(
                locale = locale,
                localizationService = localizationService,
                title = title,
                description = description,
                currentPage = currentPage,
                columns = columns,
                currentPageContent = displaySoundsPage,
                pageCount = pageCount,
                previousButtonCustomId = ComponentsEnum.SOUNDS_PREVIOUS.customId,
                nextButtonCustomId = ComponentsEnum.SOUNDS_NEXT.customId
            )
        }
    }
}
