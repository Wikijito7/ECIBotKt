package es.wokis.commands.queue

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.message.EmbedBuilder
import es.wokis.commands.Command
import es.wokis.commands.CommandName
import es.wokis.commands.Component
import es.wokis.commands.ComponentsEnum
import es.wokis.commands.commons.createPaginatedEmbedMessage
import es.wokis.localization.LocalizationKeys
import es.wokis.services.lavaplayer.model.TrackBO
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import es.wokis.utils.getDisplayTrackName

class QueueCommand(
    private val guildQueueService: GuildQueueService,
    private val localizationService: LocalizationService
) : Command, Component {
    override fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        commandBuilder.apply {
            input(
                name = CommandName.Queue.commandName,
                description = localizationService.getLocalizations(
                    LocalizationKeys.QUEUE_COMMAND_DESCRIPTION
                ).values.first()
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
            val guildId = interaction.data.guildId.value
            val discordLocale = interaction.guildLocale
            val guildQueue = guildQueueService.getOrCreateLavaPlayerService(interaction = interaction)
            // This should never be null, if so it would throw an expected exception on getOrCreateLavaPlayerService
            val resolvedGuildId = guildId ?: return
            val guildName = interaction.kord.getGuild(resolvedGuildId).name
            val queue = guildQueue.getQueue().toList()
            val displayQueue = getDisplayQueue(queue)
            val description = localizationService.getStringFormat(
                key = LocalizationKeys.QUEUE_EMBED_DESCRIPTION,
                guildId = guildId,
                discordLocale = discordLocale,
                arguments = arrayOf<Any>(queue.size, guildName)
            )
            val currentPageContent = displayQueue.takeIf { it.isNotEmpty() }?.get(0)?.let { listOf(it) }
            response.respond {
                createPaginatedEmbedMessage(
                    guildId = guildId,
                    discordLocale = discordLocale,
                    localizationService = localizationService,
                    title = localizationService.getString(LocalizationKeys.QUEUE_EMBED_TITLE, guildId, discordLocale),
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
        val discordLocale = interaction.guildLocale
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
            guildId = guildId,
            discordLocale = discordLocale,
            currentPage = currentPage,
            queueLength = guildQueue.size,
            guildName = guildName,
            displayQueuePage = displayQueue.getOrNull(currentPage - 1),
            queuePageLength = displayQueue.size
        )
    }

    private fun getDisplayQueue(queue: List<TrackBO>): List<String> {
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
        guildId: Snowflake,
        discordLocale: Locale?,
        currentPage: Int,
        queueLength: Int,
        guildName: String,
        displayQueuePage: String?,
        queuePageLength: Int
    ) {
        val description = localizationService.getStringFormat(
            key = LocalizationKeys.QUEUE_EMBED_DESCRIPTION,
            guildId = guildId,
            discordLocale = discordLocale,
            arguments = arrayOf<Any>(queueLength, guildName)
        )
        val currentPageContent = displayQueuePage?.let { listOf(it) }
        interaction.message.edit {
            createPaginatedEmbedMessage(
                guildId = guildId,
                discordLocale = discordLocale,
                localizationService = localizationService,
                title = localizationService.getString(LocalizationKeys.QUEUE_EMBED_TITLE, guildId, discordLocale),
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
