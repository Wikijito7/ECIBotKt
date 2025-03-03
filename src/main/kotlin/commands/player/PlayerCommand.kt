package es.wokis.commands.player

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import es.wokis.commands.Command
import es.wokis.commands.CommandsEnum
import es.wokis.commands.Component
import es.wokis.commands.ComponentsEnum
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService

class PlayerCommand(
    private val localizationService: LocalizationService,
    private val guildQueueService: GuildQueueService
) : Command, Component {

    override fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        commandBuilder.apply {
            input(CommandsEnum.PLAYER.commandName, "player") {
                descriptionLocalizations
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
            val queue: List<AudioTrack> = lavaPlayerService.getQueue()
            response.respond {
                createPlayerEmbed(currentTrack, queue, lavaPlayerService.isPaused())
            }.also {
                lavaPlayerService.savePlayerMessage(it.message)
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
        val queue: List<AudioTrack> = lavaPlayerService?.getQueue().orEmpty()
        interaction.message.edit {
            createPlayerEmbed(currentTrack, queue, lavaPlayerService?.isPaused() == true)
        }
    }
}
