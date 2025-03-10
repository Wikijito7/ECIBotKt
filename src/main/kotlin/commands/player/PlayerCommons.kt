package es.wokis.commands.player

import com.sedmelluq.discord.lavaplayer.tools.Units.DURATION_MS_UNKNOWN
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.kord.common.Color
import dev.kord.common.Locale
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.DiscordPartialEmoji
import dev.kord.rest.builder.component.ActionRowBuilder
import dev.kord.rest.builder.component.MessageComponentBuilder
import dev.kord.rest.builder.message.embed
import dev.kord.rest.builder.message.modify.AbstractMessageModifyBuilder
import es.wokis.commands.ComponentsEnum
import es.wokis.localization.LocalizationKeys
import es.wokis.services.localization.LocalizationService
import es.wokis.utils.getDisplayTrackName
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private const val ENABLE_PLAYBACK_POSITION = false

fun AbstractMessageModifyBuilder.createPlayerEmbed(
    guildName: String,
    localizationService: LocalizationService,
    locale: Locale,
    currentTrack: AudioTrack?,
    queue: List<AudioTrack>,
    isPaused: Boolean
) {
    embed {
        title = localizationService.getStringFormat(
            key = LocalizationKeys.PLAYER_TITLE,
            locale = locale,
            arguments = arrayOf(guildName)
        )
        thumbnail {
            url = currentTrack?.info?.artworkUrl.orEmpty()
        }
        color = Color(0x01B05B)
        currentTrack?.let {
            val duration = it.duration.toDisplayDuration()
            val currentSeek = it.position.toDisplayDuration()
            field {
                name = localizationService.getString(key = LocalizationKeys.PLAYER_CURRENT_TRACK, locale = locale)
                value = "**${it.info.title}**\n${it.info.author}"
            }
            if (ENABLE_PLAYBACK_POSITION) {
                // TODO: Take a look in the future to solve discord update request error or delete it
                field {
                    name = localizationService.getString(key = LocalizationKeys.PLAYER_PLAYBACK_POSITION, locale = locale)
                    value = "`$currentSeek ${generatePlayerPosition(it.position, it.duration)} $duration`"
                }
            }
            field {
                name = localizationService.getString(key = LocalizationKeys.PLAYER_TRACK_DURATION, locale = locale)
                value = duration.takeUnless {
                    currentTrack.duration == DURATION_MS_UNKNOWN
                } ?: localizationService.getString(
                    key = if (currentTrack.info.isStream) LocalizationKeys.PLAYER_TRACK_DURATION_STREAM else LocalizationKeys.PLAYER_TRACK_DURATION_UNKNOWN,
                    locale = locale
                )
            }
        }
        if (queue.isNotEmpty()) {
            field {
                name = localizationService.getString(key = LocalizationKeys.PLAYER_SERVER_QUEUE, locale = locale)
                value = queue.getDisplayQueue(localizationService, locale)
            }
        } else {
            description = localizationService.getString(key = LocalizationKeys.PLAYER_SERVER_QUEUE_EMPTY, locale = locale)
        }
    }
    components = if (queue.isNotEmpty() && currentTrack != null) {
        createPlayerComponents(
            localizationService = localizationService,
            locale = locale,
            isPaused = isPaused
        )
    } else {
        mutableListOf()
    }
}

private fun generatePlayerPosition(currentSeek: Long, maxDuration: Long): String {
    val safeMaxDuration = maxDuration.takeUnless { it == 0L } ?: 1
    val position = ((currentSeek / 1000f) / (safeMaxDuration / 1000f) * 9).toInt() + 1
    val playerString = "─────────".split("").toMutableList().apply {
        add(position, "●")
    }.joinToString(separator = "")
    return playerString
}

private fun createPlayerComponents(localizationService: LocalizationService, locale: Locale, isPaused: Boolean): MutableList<MessageComponentBuilder> =
    mutableListOf(
        ActionRowBuilder().apply {
            if (isPaused) {
                interactionButton(
                    style = ButtonStyle.Secondary,
                    customId = ComponentsEnum.PLAYER_RESUME.customId
                ) {
                    label = localizationService.getString(key = LocalizationKeys.PLAYER_RESUME, locale = locale)
                    emoji = DiscordPartialEmoji(name = "▶️")
                }
            } else {
                interactionButton(
                    style = ButtonStyle.Secondary,
                    customId = ComponentsEnum.PLAYER_PAUSE.customId
                ) {
                    label = localizationService.getString(key = LocalizationKeys.PLAYER_PAUSE, locale = locale)
                    emoji = DiscordPartialEmoji(name = "⏸")
                }
            }
            interactionButton(
                style = ButtonStyle.Secondary,
                customId = ComponentsEnum.PLAYER_SKIP.customId
            ) {
                label = localizationService.getString(key = LocalizationKeys.PLAYER_SKIP, locale = locale)
                emoji = DiscordPartialEmoji(name = "⏭")
            }
            interactionButton(
                style = ButtonStyle.Secondary,
                customId = ComponentsEnum.PLAYER_SHUFFLE.customId
            ) {
                label = localizationService.getString(key = LocalizationKeys.PLAYER_SHUFFLE, locale = locale)
                emoji = DiscordPartialEmoji(name = "\uD83D\uDD00")
            }
            interactionButton(
                style = ButtonStyle.Danger,
                customId = ComponentsEnum.PLAYER_DISCONNECT.customId
            ) {
                label = localizationService.getString(key = LocalizationKeys.PLAYER_DISCONNECT, locale = locale)
            }
        }
    )

private fun List<AudioTrack>.getDisplayQueue(localizationService: LocalizationService, locale: Locale): String {
    val firstTrack = getOrNull(0)
    val secondTrack = getOrNull(1)
    val thirdTrack = getOrNull(2)
    val queueRemaining = (size - 3).takeIf { it > 0 }
    val duration = sumOf { it.duration }.toDisplayDuration()
    return firstTrack?.getDisplayNameAndDuration()
        ?.plus(secondTrack?.let { "\n${it.getDisplayNameAndDuration()}" }.orEmpty())
        ?.plus(thirdTrack?.let { "\n${it.getDisplayNameAndDuration()}" }.orEmpty())
        ?.plus(
            queueRemaining?.let {
                "\n".plus(
                    localizationService.getStringFormat(
                        key = LocalizationKeys.PLAYER_QUEUE_TRACKS_REMAINING,
                        locale = locale,
                        arguments = arrayOf(it)
                    )
                )
            }.orEmpty()
        )
        ?.plus(
            "\n".plus(
                localizationService.getStringFormat(
                    key = LocalizationKeys.PLAYER_DURATION_REMAINING,
                    locale = locale,
                    arguments = arrayOf(duration)
                )
            )
        ).orEmpty()
}

private fun AudioTrack.getDisplayNameAndDuration() = "${getDisplayTrackName()} (${duration.toDisplayDuration()})"

private fun Long.toDisplayDuration() = toDuration(DurationUnit.MILLISECONDS).toComponents { hours, minutes, seconds, _ ->
    "%02d:%02d:%02d".format(hours, minutes, seconds)
}
