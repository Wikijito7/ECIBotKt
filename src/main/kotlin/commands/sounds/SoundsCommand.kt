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
import es.wokis.localization.LocalizationKeys
import es.wokis.services.localization.LocalizationService
import es.wokis.utils.getFolderContent
import es.wokis.utils.orDefaultLocale
import java.io.File

private const val SOUNDS_PATH = "./audio"

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
            val sounds: List<File> = getFolderContent(SOUNDS_PATH)
            val displaySounds = getDisplaySounds(sounds)
            val title = localizationService.getString(key = LocalizationKeys.SOUNDS_EMBED_TITLE, locale = locale)
            val description = localizationService.getStringFormat(
                key = LocalizationKeys.SOUNDS_EMBED_DESCRIPTION,
                locale = locale,
                arguments = arrayOf(sounds.size)
            )
            response.respond {
                createPaginatedEmbedMessage(
                    locale = locale,
                    localizationService = localizationService,
                    title = title,
                    description = description,
                    currentPage = 1,
                    currentDisplayPage = displaySounds.takeIf { it.isNotEmpty() }?.get(0),
                    pageCount = displaySounds.size,
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
        val sounds: List<File> = getFolderContent(SOUNDS_PATH)
        val displayQueue = getDisplaySounds(sounds)
        val currentPage = interaction.message.embeds.firstOrNull()
            ?.footer?.text?.split(" ")?.get(1)?.toIntOrNull()?.plus(updatePageBy)
            ?.takeUnless { it > displayQueue.size } ?: 1
        updateQueueMessage(
            interaction = interaction,
            currentPage = currentPage,
            soundsCount = sounds.size,
            displaySoundsPage = displayQueue.getOrNull(currentPage - 1),
            pageCount = displayQueue.size
        )
    }

    private fun getDisplaySounds(sounds: List<File>): List<String> {
        if (sounds.isEmpty()) return emptyList()
        var currentString = sounds.first().nameWithoutExtension
        val displaySounds: MutableList<String> = mutableListOf()

        sounds.drop(1).forEach { sound ->
            val soundName = sound.nameWithoutExtension
            val appendDisplayTrackName = ", ".plus(soundName)
            if (currentString.length + appendDisplayTrackName.length <= EmbedBuilder.Field.Limits.value) {
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
        displaySoundsPage: String?,
        pageCount: Int
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
                currentDisplayPage = displaySoundsPage,
                pageCount = pageCount,
                previousButtonCustomId = ComponentsEnum.SOUNDS_PREVIOUS.customId,
                nextButtonCustomId = ComponentsEnum.SOUNDS_NEXT.customId
            )
        }
    }
}
