package es.wokis.commands.queue

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.kord.common.Color
import dev.kord.common.Locale
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.DiscordPartialEmoji
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.rest.builder.component.ActionRowBuilder
import dev.kord.rest.builder.component.MessageComponentBuilder
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.embed
import dev.kord.rest.builder.message.modify.AbstractMessageModifyBuilder
import es.wokis.commands.Command
import es.wokis.commands.CommandsEnum
import es.wokis.commands.Component
import es.wokis.commands.ComponentsEnum
import es.wokis.constants.BLANK_SPACE
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
            response.respond {
                createQueueMessage(
                    locale = locale,
                    currentPage = 1,
                    queueLength = queue.size,
                    guildName = guildName,
                    displayQueuePage = displayQueue.takeIf { it.isNotEmpty() }?.get(0),
                    queuePageLength = displayQueue.size,
                )
            }
        } catch (exc: IllegalStateException) {
            response.respond {
                content = exc.message
            }
        }
    }

    private fun AbstractMessageModifyBuilder.createQueueMessage(
        locale: Locale,
        currentPage: Int,
        queueLength: Int,
        guildName: String,
        displayQueuePage: String?,
        queuePageLength: Int,
    ) {
        createEmbed(
            locale = locale,
            currentPage = currentPage,
            queueLength = queueLength,
            guildName = guildName,
            displayQueuePage = displayQueuePage,
            queuePageLength = queuePageLength
        )
        if (queuePageLength > 1) {
            components = getMessageComponentBuilders(
                locale = locale,
                disablePrevious = currentPage == 1,
                disableNext = currentPage == queuePageLength
            )
        }
    }

    private fun AbstractMessageModifyBuilder.createEmbed(
        locale: Locale,
        currentPage: Int,
        queueLength: Int,
        guildName: String,
        displayQueuePage: String?,
        queuePageLength: Int
    ) {
        embed {
            title = localizationService.getString(LocalizationKeys.QUEUE_EMBED_TITLE, locale)
            description = localizationService.getStringFormat(
                key = LocalizationKeys.QUEUE_EMBED_DESCRIPTION,
                locale = locale,
                arguments = arrayOf(queueLength, guildName)
            )
            color = Color(0x01B05B)
            displayQueuePage?.let { displayMessage ->
                field {
                    name = BLANK_SPACE
                    value = displayMessage
                    inline = true
                }
            }
            if (queuePageLength > 0) {
                footer {
                    text = localizationService.getStringFormat(
                        key = LocalizationKeys.QUEUE_EMBED_FOOTER,
                        locale = locale,
                        arguments = arrayOf(currentPage, queuePageLength)
                    )
                }
            }
        }
    }

    private fun getMessageComponentBuilders(
        locale: Locale,
        disablePrevious: Boolean,
        disableNext: Boolean
    ): MutableList<MessageComponentBuilder> =
        mutableListOf(
            ActionRowBuilder().apply {
                interactionButton(
                    style = ButtonStyle.Secondary,
                    customId = ComponentsEnum.QUEUE_PREVIOUS.customId
                ) {
                    label = localizationService.getString(LocalizationKeys.QUEUE_PREVIOUS_BUTTON_LABEL, locale)
                    emoji = DiscordPartialEmoji(name = "⬅")
                    disabled = disablePrevious
                }
                interactionButton(
                    style = ButtonStyle.Secondary,
                    customId = ComponentsEnum.QUEUE_NEXT.customId
                ) {
                    label = localizationService.getString(LocalizationKeys.QUEUE_NEXT_BUTTON_LABEL, locale)
                    emoji = DiscordPartialEmoji(name = "➡")
                    disabled = disableNext
                }
            }
        )

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
        return displayQueue.toList()
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
            displayQueuePage = displayQueue[currentPage - 1],
            queuePageLength = displayQueue.size
        )
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
        interaction.message.edit {
            createQueueMessage(
                locale = locale,
                currentPage = currentPage,
                queueLength = queueLength,
                guildName = guildName,
                displayQueuePage = displayQueuePage,
                queuePageLength = queuePageLength
            )
        }
    }
}
