package es.wokis.commands.player

import dev.kord.common.Locale
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
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.lavaplayer.model.TrackBO
import es.wokis.services.localization.LocalizationService
import es.wokis.services.player.PlayerChannelService
import es.wokis.services.queue.GuildQueueService
import es.wokis.utils.getGuildName
import es.wokis.utils.orDefaultLocale
import services.player.result.PlayerChannelResult
import kotlin.toString

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
                onPlayerChannelFoundOrCreatedSuccessfully(
                    playerMessageResult = playerMessageResult,
                    lavaPlayerService = lavaPlayerService,
                    response = response,
                    locale = locale
                )
            } else {
                onPlayerChannelSearchFailed(
                    locale = locale,
                    response = response,
                    guildName = guildName,
                    currentTrack = currentTrack,
                    queue = queue,
                    lavaPlayerService = lavaPlayerService
                )
            }
        } catch (exc: IllegalStateException) {
            response.respond {
                content = exc.message
            }
        }
    }

    private suspend fun onPlayerChannelSearchFailed(
        locale: Locale,
        response: DeferredPublicMessageInteractionResponseBehavior,
        guildName: String,
        currentTrack: TrackBO?,
        queue: List<TrackBO>,
        lavaPlayerService: GuildLavaPlayerService
    ) {
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

    private suspend fun onPlayerChannelFoundOrCreatedSuccessfully(
        playerMessageResult: Result<PlayerChannelResult>,
        lavaPlayerService: GuildLavaPlayerService,
        response: DeferredPublicMessageInteractionResponseBehavior,
        locale: Locale
    ) {
        playerMessageResult.getOrNull()?.let { result ->
            lavaPlayerService.savePlayerMessage(result.message)
            response.respond {
                content = localizationService.getStringFormat(
                    key = if (result.isNewChannel) {
                        LocalizationKeys.PLAYER_CHANNEL_CREATED
                    } else {
                        LocalizationKeys.PLAYER_SHOWN_ON_EXISTING_CHANNEL
                    },
                    locale = locale,
                    arguments = arrayOf(result.channel.id)
                )
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
