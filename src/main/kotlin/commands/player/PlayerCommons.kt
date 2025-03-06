package es.wokis.commands.player

import com.sedmelluq.discord.lavaplayer.tools.Units.DURATION_MS_UNKNOWN
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
import kotlin.math.max
import kotlin.time.DurationUnit
import kotlin.time.toDuration

fun AbstractMessageModifyBuilder.createPlayerEmbed(currentTrack: AudioTrack?, queue: List<AudioTrack>, isPaused: Boolean) {
    embed {
        title = "Player"
        thumbnail {
            url = currentTrack?.info?.artworkUrl.orEmpty()
        }
        color = Color(0x01B05B)
        currentTrack?.let {
            val duration = it.duration.toDisplayDuration()
            val currentSeek = it.position.toDisplayDuration()
            field {
                name = "Current Track"
                value = "**${it.info.title}**\n${it.info.author}"
            }
            if (false) {
                // TODO: Take a look in the future to solve discord update request error or delete it
                field {
                    name = "Playback position"
                    value = "`$currentSeek ${generatePlayerPosition(it.position, it.duration)} $duration`"
                }
            }
            field {
                name = "Track duration"
                value = duration.takeUnless { currentTrack.duration == DURATION_MS_UNKNOWN } ?: "Unknown/Stream"
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
    components = if (queue.isNotEmpty()) createPlayerComponents(isPaused) else mutableListOf()
}

private fun generatePlayerPosition(currentSeek: Long, maxDuration: Long): String {
    val safeMaxDuration = maxDuration.takeUnless { it == 0L } ?: 1
    val position = ((currentSeek / 1000f) / (safeMaxDuration / 1000f) * 9).toInt() + 1
    val playerString = "─────────".split("").toMutableList().apply {
        add(position, "●")
    }.joinToString(separator = "")
    return playerString
}

private fun createPlayerComponents(isPaused: Boolean): MutableList<MessageComponentBuilder> =
    mutableListOf(
        ActionRowBuilder().apply {
            if (isPaused) {
                interactionButton(
                    style = ButtonStyle.Secondary,
                    customId = ComponentsEnum.PLAYER_RESUME.customId
                ) {
                    label = "Resume"
                    emoji = DiscordPartialEmoji(name = "▶️")
                }
            } else {
                interactionButton(
                    style = ButtonStyle.Secondary,
                    customId = ComponentsEnum.PLAYER_PAUSE.customId
                ) {
                    label = "Pause"
                    emoji = DiscordPartialEmoji(name = "⏸")
                }
            }
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
            }
        }
    )

private fun List<AudioTrack>.getDisplayQueue(): String {
    val firstTrack = getOrNull(0)
    val secondTrack = getOrNull(1)
    val thirdTrack = getOrNull(2)
    val queueRemaining = (size - 3).takeIf { it > 0 }
    val duration = sumOf { it.duration }.toDisplayDuration()
    return firstTrack?.getDisplayNameAndDuration()
        ?.plus(secondTrack?.let { "\n${it.getDisplayNameAndDuration()}" }.orEmpty())
        ?.plus(thirdTrack?.let { "\n${it.getDisplayNameAndDuration()}" }.orEmpty())
        ?.plus(queueRemaining?.let { "\nand another $it tracks" }.orEmpty())
        ?.plus("\n$duration remaining")
        .orEmpty()
}

private fun AudioTrack.getDisplayNameAndDuration() = "${getDisplayTrackName()} (${duration.toDisplayDuration()})"

private fun Long.toDisplayDuration() = toDuration(DurationUnit.MILLISECONDS).toComponents { hours, minutes, seconds, _ ->
    "%02d:%02d:%02d".format(hours, minutes, seconds)
}
