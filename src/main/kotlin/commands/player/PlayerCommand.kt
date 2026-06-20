package es.wokis.commands.player

import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import es.wokis.commands.Command
import es.wokis.commands.CommandName
import es.wokis.commands.Component
import es.wokis.commands.ComponentsEnum
import es.wokis.localization.LocalizationKeys
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.lavaplayer.LyricsService
import es.wokis.services.lavaplayer.model.TrackBO
import es.wokis.services.localization.LocalizationService
import es.wokis.services.player.PlayerChannelService
import es.wokis.services.queue.GuildQueueService
import es.wokis.utils.getDisplayTrackName
import es.wokis.utils.getGuildName
import services.player.result.PlayerChannelResult

class PlayerCommand(
    private val localizationService: LocalizationService,
    private val guildQueueService: GuildQueueService,
    private val playerChannelService: PlayerChannelService,
    private val lyricsService: LyricsService
) : Command, Component {

    override fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        commandBuilder.apply {
            input(
                name = CommandName.Player.commandName,
                description = localizationService.getLocalizations(LocalizationKeys.PLAYER_COMMAND_DESCRIPTION).values.first()
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
            val guildId = interaction.data.guildId.value
            val discordLocale = interaction.guildLocale
            val guildName = interaction.getGuildName()

            val playerMessageResult = playerChannelService.sendPlayerMessage(interaction) {
                createPlayerEmbed(
                    guildId = guildId,
                    discordLocale = discordLocale,
                    guildName = guildName,
                    localizationService = localizationService,
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
                    guildId = guildId,
                    discordLocale = discordLocale
                )
            } else {
                onPlayerChannelSearchFailed(
                    guildId = guildId,
                    discordLocale = discordLocale,
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
        guildId: Snowflake?,
        discordLocale: Locale?,
        response: DeferredPublicMessageInteractionResponseBehavior,
        guildName: String,
        currentTrack: TrackBO?,
        queue: List<TrackBO>,
        lavaPlayerService: GuildLavaPlayerService
    ) {
        val warningMessage = localizationService.getString(
            LocalizationKeys.PLAYER_CHANNEL_CREATION_FAILED,
            guildId,
            discordLocale
        )
        response.respond {
            content = warningMessage
            createPlayerEmbed(
                guildId = guildId,
                discordLocale = discordLocale,
                guildName = guildName,
                localizationService = localizationService,
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
        guildId: Snowflake?,
        discordLocale: Locale?
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
                    guildId = guildId,
                    discordLocale = discordLocale,
                    arguments = arrayOf(result.channel.id)
                )
            }
        }
    }

    override suspend fun onInteract(interaction: ComponentInteraction) {
        val lavaPlayerService = interaction.data.guildId.value?.let { guildQueueService.getLavaPlayerService(it) }
        val currentCustomId = (interaction as? ButtonInteraction)?.component?.customId

        if (currentCustomId == ComponentsEnum.PLAYER_LYRICS.customId) {
            handleLyricsButton(interaction, lavaPlayerService)
            return
        }

        when (currentCustomId) {
            ComponentsEnum.PLAYER_RESUME.customId -> lavaPlayerService?.resume()
            ComponentsEnum.PLAYER_PAUSE.customId -> lavaPlayerService?.pause()
            ComponentsEnum.PLAYER_SKIP.customId -> lavaPlayerService?.skip()
            ComponentsEnum.PLAYER_DISCONNECT.customId -> lavaPlayerService?.stop()
            ComponentsEnum.PLAYER_SHUFFLE.customId -> lavaPlayerService?.shuffle()
            ComponentsEnum.PLAYER_RECONNECT.customId -> lavaPlayerService?.reconnect()
            else -> return
        }
        val currentTrack = lavaPlayerService?.getCurrentPlayingTrack()
        val queue: List<TrackBO> = lavaPlayerService?.getQueue().orEmpty()
        val guildId = interaction.data.guildId.value
        val discordLocale = interaction.guildLocale
        val guildName = interaction.getGuildName()

        interaction.message.edit {
            createPlayerEmbed(
                guildId = guildId,
                discordLocale = discordLocale,
                guildName = guildName,
                localizationService = localizationService,
                currentTrack = currentTrack,
                queue = queue,
                isPaused = lavaPlayerService?.isPaused() == true
            )
        }
    }

    private suspend fun handleLyricsButton(
        interaction: ComponentInteraction,
        lavaPlayerService: GuildLavaPlayerService?
    ) {
        val guildPlayerService = lavaPlayerService ?: run {
            interaction.respondEphemeral { content = "No player service available" }
            return
        }
        val guildId = interaction.data.guildId.value
        val discordLocale = interaction.guildLocale

        val existingMsg = guildPlayerService.getLyricsMessage()
        if (existingMsg != null) {
            existingMsg.delete()
            guildPlayerService.clearLyricsMessage()
            interaction.respondEphemeral {
                content = localizationService.getString(
                    key = LocalizationKeys.PLAYER_HIDE_LYRICS,
                    guildId = guildId,
                    discordLocale = discordLocale
                )
            }
            return
        }

        val track = guildPlayerService.getCurrentPlayingTrack()
        if (track == null) {
            interaction.respondEphemeral {
                content = localizationService.getString(
                    key = LocalizationKeys.PLAYER_LYRICS_NO_TRACK,
                    guildId = guildId,
                    discordLocale = discordLocale
                )
            }
            return
        }

        val lyrics = lyricsService.getFormattedLyrics(track.audioTrack)
        if (lyrics != null) {
            interaction.respondPublic {
                content = localizationService.getStringFormat(
                    key = LocalizationKeys.PLAYER_LYRICS_TITLE,
                    guildId = guildId,
                    discordLocale = discordLocale,
                    arguments = arrayOf(track.getDisplayTrackName())
                ) + "\n\n```\n$lyrics\n```"
            }
            val msg = interaction.getOriginalInteractionResponse()
            guildPlayerService.saveLyricsMessage(msg)
        } else {
            interaction.respondEphemeral {
                content = localizationService.getString(
                    key = LocalizationKeys.PLAYER_LYRICS_NOT_FOUND,
                    guildId = guildId,
                    discordLocale = discordLocale
                )
            }
        }
    }
}
