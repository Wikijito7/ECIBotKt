package es.wokis.commands.queue

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
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
import es.wokis.services.queue.GuildQueueService
import es.wokis.utils.getDisplayTrackName
import es.wokis.utils.orDefaultLocale

class QueueCommand(
    private val guildQueueService: GuildQueueService,
    private val localizationService: LocalizationService
) : Command, Component {
    override fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        commandBuilder.apply {
            input(
                name = CommandsEnum.QUEUE.commandName,
                description = localizationService.getString(LocalizationKeys.QUEUE_COMMAND_DESCRIPTION)
            ) {
                descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.QUEUE_COMMAND_DESCRIPTION)
            }
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        try {
            val locale = interaction.guildLocale.orDefaultLocale()
            val guildQueue = guildQueueService.getOrCreateLavaPlayerService(interaction = interaction)
            // This should never be null, if so it would throw an expected exception on getOrCreateLavaPlayerService
            val guildId = interaction.data.guildId.value ?: return
            val guildName = interaction.kord.getGuild(guildId).name
            val queue = guildQueue.getQueue().toList()
            val displayQueue = getDisplayQueue(queue)
            val description = localizationService.getStringFormat(
                key = LocalizationKeys.QUEUE_EMBED_DESCRIPTION,
                locale = locale,
                arguments = arrayOf(queue.size, guildName)
            )
            val currentPageContent = displayQueue.takeIf { it.isNotEmpty() }?.get(0)?.let { listOf(it) }
            response.respond {
                createPaginatedEmbedMessage(
                    locale = locale,
                    localizationService = localizationService,
                    title = localizationService.getString(LocalizationKeys.QUEUE_EMBED_TITLE, locale),
                    description = description,
                    currentPage = 1,
                    currentPageContent = currentPageContent,
                    columns = 1,
                    pageCount = displayQueue.size,
                    previousButtonCustomId = ComponentsEnum.QUEUE_PREVIOUS.customId,
                    nextButtonCustomId = ComponentsEnum.QUEUE_NEXT.customId
                )
            }
        } catch (exc: IllegalStateException) {
            response.respond {
                content = exc.message
            }
        }
    }

    override suspend fun onInteract(interaction: ComponentInteraction) {
        val guildId = interaction.data.guildId.value ?: return
        val interactionCustomId = (interaction as? ButtonInteraction)?.component?.customId
        val updatePageBy = if (interactionCustomId == ComponentsEnum.QUEUE_PREVIOUS.customId) -1 else 1
        val guildName = interaction.kord.getGuild(guildId).name
        val guildQueue = guildQueueService.getLavaPlayerService(guildId)?.getQueue().orEmpty()
        val displayQueue = getDisplayQueue(guildQueue)
        val currentPage = interaction.message.embeds.firstOrNull()
            ?.footer?.text?.split(" ")?.get(1)?.toIntOrNull()?.plus(updatePageBy)
            ?.takeUnless { it > displayQueue.size } ?: 1
        updateQueueMessage(
            interaction = interaction,
            currentPage = currentPage,
            queueLength = guildQueue.size,
            guildName = guildName,
            displayQueuePage = displayQueue.getOrNull(currentPage - 1),
            queuePageLength = displayQueue.size
        )
    }

    private fun getDisplayQueue(queue: List<AudioTrack>): List<String> {
        if (queue.isEmpty()) return emptyList()
        var currentString = queue.first().getDisplayTrackName()
        val displayQueue: MutableList<String> = mutableListOf()

        queue.drop(1).forEach { track ->
            val displayTrackName = track.getDisplayTrackName()
            val appendDisplayTrackName = ", ".plus(displayTrackName)
            if (currentString.length + appendDisplayTrackName.length <= EmbedBuilder.Field.Limits.value) {
                currentString += appendDisplayTrackName
            } else {
                displayQueue.add(currentString)
                currentString = displayTrackName
            }
        }
        if (currentString.isNotEmpty()) {
            displayQueue.add(currentString)
        }
        return displayQueue.toList()
    }

    private suspend fun updateQueueMessage(
        interaction: ComponentInteraction,
        currentPage: Int,
        queueLength: Int,
        guildName: String,
        displayQueuePage: String?,
        queuePageLength: Int
    ) {
        val locale = interaction.guildLocale.orDefaultLocale()
        val description = localizationService.getStringFormat(
            key = LocalizationKeys.QUEUE_EMBED_DESCRIPTION,
            locale = locale,
            arguments = arrayOf(queueLength, guildName)
        )
        val currentPageContent = displayQueuePage?.let { listOf(it) }
        interaction.message.edit {
            createPaginatedEmbedMessage(
                locale = locale,
                localizationService = localizationService,
                title = localizationService.getString(LocalizationKeys.QUEUE_EMBED_TITLE, locale),
                description = description,
                currentPage = currentPage,
                columns = 1,
                currentPageContent = currentPageContent,
                pageCount = queuePageLength,
                previousButtonCustomId = ComponentsEnum.QUEUE_PREVIOUS.customId,
                nextButtonCustomId = ComponentsEnum.QUEUE_NEXT.customId
            )
        }
    }
}
