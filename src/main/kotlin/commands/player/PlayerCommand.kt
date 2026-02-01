package es.wokis.commands.player

import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import es.wokis.commands.Command
import es.wokis.commands.CommandName
import es.wokis.commands.Component
import es.wokis.commands.ComponentsEnum
import es.wokis.localization.LocalizationKeys
import es.wokis.services.lavaplayer.model.TrackBO
import es.wokis.services.localization.LocalizationService
import es.wokis.services.player.PlayerChannelService
import es.wokis.services.queue.GuildQueueService
import es.wokis.utils.getGuildName
import es.wokis.utils.orDefaultLocale

class PlayerCommand(
    private val localizationService: LocalizationService,
    private val guildQueueService: GuildQueueService,
    private val playerChannelService: PlayerChannelService
) : Command, Component {

    override fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        commandBuilder.apply {
            input(
                name = CommandName.Player.commandName,
                description = localizationService.getString(key = LocalizationKeys.PLAYER_COMMAND_DESCRIPTION)
            ) {
                descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.PLAYER_COMMAND_DESCRIPTION)
            }
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        try {
            val lavaPlayerService = guildQueueService.getOrCreateLavaPlayerService(interaction)
            val currentTrack = lavaPlayerService.getCurrentPlayingTrack()
            val queue: List<TrackBO> = lavaPlayerService.getQueue()
            val locale = interaction.guildLocale.orDefaultLocale()
            val guildName = interaction.getGuildName()

            val playerMessageResult = playerChannelService.sendPlayerMessage(interaction) {
                createPlayerEmbed(
                    guildName = guildName,
                    localizationService = localizationService,
                    locale = locale,
                    currentTrack = currentTrack,
                    queue = queue,
                    isPaused = lavaPlayerService.isPaused()
                )
            }

            if (playerMessageResult.isSuccess) {
                val result = playerMessageResult.getOrNull()
                result?.message?.let {
                    lavaPlayerService.savePlayerMessage(it)
                }
                // Show appropriate message based on whether channel was created or already existed
                if (result?.isNewChannel == true) {
                    // Newly created channel
                    response.respond {
                        content = localizationService.getStringFormat(
                            LocalizationKeys.PLAYER_CHANNEL_CREATED,
                            locale,
                            result.channel.id.toString()
                        )
                    }
                } else {
                    // Using existing channel
                    response.respond {
                        content = localizationService.getStringFormat(
                            LocalizationKeys.PLAYER_SHOWN_ON_EXISTING_CHANNEL,
                            locale,
                            result?.channel?.id?.toString() ?: ""
                        )
                    }
                }
            } else {
                val warningMessage = localizationService.getString(
                    LocalizationKeys.PLAYER_CHANNEL_CREATION_FAILED,
                    locale
                )
                response.respond {
                    content = warningMessage
                    createPlayerEmbed(
                        guildName = guildName,
                        localizationService = localizationService,
                        locale = locale,
                        currentTrack = currentTrack,
                        queue = queue,
                        isPaused = lavaPlayerService.isPaused()
                    )
                }.also {
                    lavaPlayerService.savePlayerMessage(it.message)
                }
            }
        } catch (exc: IllegalStateException) {
            response.respond {
                content = exc.message
            }
        }
    }

    override suspend fun onInteract(interaction: ComponentInteraction) {
        val lavaPlayerService = interaction.data.guildId.value?.let { guildQueueService.getLavaPlayerService(it) }
        val currentCustomId = (interaction as? ButtonInteraction)?.component?.customId

        when (currentCustomId) {
            ComponentsEnum.PLAYER_RESUME.customId -> lavaPlayerService?.resume()
            ComponentsEnum.PLAYER_PAUSE.customId -> lavaPlayerService?.pause()
            ComponentsEnum.PLAYER_SKIP.customId -> lavaPlayerService?.skip()
            ComponentsEnum.PLAYER_DISCONNECT.customId -> lavaPlayerService?.stop()
            ComponentsEnum.PLAYER_SHUFFLE.customId -> lavaPlayerService?.shuffle()
            else -> return
        }
        val currentTrack = lavaPlayerService?.getCurrentPlayingTrack()
        val queue: List<TrackBO> = lavaPlayerService?.getQueue().orEmpty()
        val locale = interaction.guildLocale.orDefaultLocale()
        val guildName = interaction.getGuildName()

        interaction.message.edit {
            createPlayerEmbed(
                guildName = guildName,
                localizationService = localizationService,
                locale = locale,
                currentTrack = currentTrack,
                queue = queue,
                isPaused = lavaPlayerService?.isPaused() == true
            )
        }
    }
}
