package es.wokis.commands.player

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.kord.common.Color
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.DiscordPartialEmoji
import dev.kord.rest.builder.component.ActionRowBuilder
import dev.kord.rest.builder.component.MessageComponentBuilder
import dev.kord.rest.builder.message.embed
import dev.kord.rest.builder.message.modify.AbstractMessageModifyBuilder
import es.wokis.commands.ComponentsEnum
import es.wokis.utils.getDisplayTrackName
import kotlin.time.DurationUnit
import kotlin.time.toDuration

fun AbstractMessageModifyBuilder.createPlayerEmbed(currentTrack: AudioTrack?, queue: List<AudioTrack>) {
    embed {
        title = "Player"
        thumbnail {
            url = currentTrack?.info?.artworkUrl.orEmpty()
        }
        color = Color(0x01B05B)
        currentTrack?.let {
            val duration = it.duration.toDisplayDuration()
            field {
                name = "Current Track"
                value = "**${it.info.title}**\n${it.info.author}\n$duration"
            }
        }
        if (queue.isNotEmpty()) {
            field {
                name = "Server's queue"
                value = queue.getDisplayQueue()
            }
        } else {
            description = "Currently, there are no tracks queued"
        }
    }
    components = if (queue.isNotEmpty()) createPlayerComponents() else mutableListOf()
}

private fun createPlayerComponents(): MutableList<MessageComponentBuilder> =
    mutableListOf(
        ActionRowBuilder().apply {
            interactionButton(
                style = ButtonStyle.Secondary,
                customId = ComponentsEnum.PLAYER_SKIP.customId
            ) {
                label = "Skip"
                emoji = DiscordPartialEmoji(name = "⏭")
            }
            interactionButton(
                style = ButtonStyle.Secondary,
                customId = ComponentsEnum.PLAYER_SHUFFLE.customId
            ) {
                label = "Shuffle"
                emoji = DiscordPartialEmoji(name = "\uD83D\uDD00")
            }
            interactionButton(
                style = ButtonStyle.Danger,
                customId = ComponentsEnum.PLAYER_DISCONNECT.customId
            ) {
                label = "Disconnect"
                emoji = DiscordPartialEmoji(name = "⏹")
            }
        }
    )

private fun List<AudioTrack>.getDisplayQueue(): String {
    val firstTrack = getOrNull(0)?.getDisplayTrackName()
    val secondTrack = getOrNull(1)?.getDisplayTrackName()
    val thirdTrack = getOrNull(2)?.getDisplayTrackName()
    val queueRemaining = (size - 3).takeIf { it > 0 }
    val duration = sumOf { it.duration }.toDisplayDuration()
    return firstTrack
        ?.plus(secondTrack?.let { "\n$it" }.orEmpty())
        ?.plus(thirdTrack?.let { "\n$it" }.orEmpty())
        ?.plus(queueRemaining?.let { "\nand another $it tracks" }.orEmpty())
        ?.plus("\n$duration remaining")
        .orEmpty()
}

private fun Long.toDisplayDuration() = toDuration(DurationUnit.MILLISECONDS).toComponents { hours, minutes, seconds, _ ->
    "%02d:%02d:%02d".format(hours, minutes, seconds)
}
