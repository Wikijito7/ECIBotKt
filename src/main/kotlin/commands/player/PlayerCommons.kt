package es.wokis.commands.player

import dev.kord.common.Color
import dev.kord.common.Locale
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.DiscordPartialEmoji
import dev.kord.common.entity.Snowflake
import dev.kord.rest.builder.component.ActionRowBuilder
import dev.kord.rest.builder.component.MessageComponentBuilder
import dev.kord.rest.builder.message.MessageBuilder
import dev.kord.rest.builder.message.embed
import es.wokis.commands.ComponentsEnum
import es.wokis.constants.BRAND_COLOR
import es.wokis.localization.LocalizationKeys
import es.wokis.player.engine.model.PlayerTrack
import es.wokis.services.lavaplayer.model.TrackBO
import es.wokis.services.localization.LocalizationService
import es.wokis.utils.getDisplayTrackName
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private const val ENABLE_PLAYBACK_POSITION = false
private const val PLAYER_POSITION_BAR = "─────────"
private const val QUEUE_PREVIEW_SIZE = 3

suspend fun MessageBuilder.createPlayerEmbed(
    guildId: Snowflake?,
    discordLocale: Locale?,
    guildName: String,
    localizationService: LocalizationService,
    currentTrack: TrackBO?,
    queue: List<TrackBO>,
    isPaused: Boolean
) {
    embed {
        title = localizationService.getStringFormat(
            key = LocalizationKeys.PLAYER_TITLE,
            guildId = guildId,
            discordLocale = discordLocale,
            arguments = arrayOf(guildName)
        )
        thumbnail {
            url = currentTrack?.customFavicon ?: currentTrack?.artworkUrl.orEmpty()
        }
        color = Color(BRAND_COLOR)
        currentTrack?.let {
            val duration = it.durationMillis.toDisplayDuration()
            val currentSeek = it.positionMillis.toDisplayDuration()
            field {
                name = localizationService.getString(LocalizationKeys.PLAYER_CURRENT_TRACK, guildId, discordLocale)
                value = it.getTrackName()
            }
            if (ENABLE_PLAYBACK_POSITION) {
                // Playback-position updates stay disabled until the Discord update-request error is resolved.
                field {
                    name = localizationService.getString(LocalizationKeys.PLAYER_PLAYBACK_POSITION, guildId, discordLocale)
                    value = "`$currentSeek ${generatePlayerPosition(it.positionMillis, it.durationMillis)} $duration`"
                }
            }
            field {
                name = localizationService.getString(LocalizationKeys.PLAYER_TRACK_DURATION, guildId, discordLocale)
                value = duration.takeUnless {
                    currentTrack.durationMillis == PlayerTrack.UNKNOWN_DURATION
                } ?: localizationService.getString(
                    key = trackDurationKey(currentTrack.isStream),
                    guildId = guildId,
                    discordLocale = discordLocale
                )
            }
        }
        if (queue.isNotEmpty()) {
            field {
                name = localizationService.getString(LocalizationKeys.PLAYER_SERVER_QUEUE, guildId, discordLocale)
                value = queue.getDisplayQueue(localizationService, guildId, discordLocale)
            }
        } else if (currentTrack == null) {
            description = localizationService.getString(LocalizationKeys.PLAYER_SERVER_QUEUE_EMPTY, guildId, discordLocale)
        }
    }
    components = if (queue.isNotEmpty() || currentTrack != null) {
        createPlayerComponents(
            localizationService = localizationService,
            guildId = guildId,
            discordLocale = discordLocale,
            isPaused = isPaused
        )
    } else {
        mutableListOf()
    }
}

private fun trackDurationKey(isStream: Boolean): String =
    if (isStream) {
        LocalizationKeys.PLAYER_TRACK_DURATION_STREAM
    } else {
        LocalizationKeys.PLAYER_TRACK_DURATION_UNKNOWN
    }

private fun TrackBO.getTrackName() = customName?.let {
    "**$customName**"
} ?: "**$title**\n$author"

private fun generatePlayerPosition(currentSeek: Long, maxDuration: Long): String {
    val safeMaxDuration = maxDuration.takeUnless { it == 0L } ?: 1
    val position = ((currentSeek / 1000f) / (safeMaxDuration / 1000f) * PLAYER_POSITION_BAR.length).toInt() + 1
    val playerString = PLAYER_POSITION_BAR.split("").toMutableList().apply {
        add(position, "●")
    }.joinToString(separator = "")
    return playerString
}

private suspend fun createPlayerComponents(
    localizationService: LocalizationService,
    guildId: Snowflake?,
    discordLocale: Locale?,
    isPaused: Boolean
): MutableList<MessageComponentBuilder> =
    mutableListOf(
        ActionRowBuilder().apply {
            if (isPaused) {
                interactionButton(
                    style = ButtonStyle.Secondary,
                    customId = ComponentsEnum.PLAYER_RESUME.customId
                ) {
                    label = localizationService.getString(LocalizationKeys.PLAYER_RESUME, guildId, discordLocale)
                    emoji = DiscordPartialEmoji(name = "▶️")
                }
            } else {
                interactionButton(
                    style = ButtonStyle.Secondary,
                    customId = ComponentsEnum.PLAYER_PAUSE.customId
                ) {
                    label = localizationService.getString(LocalizationKeys.PLAYER_PAUSE, guildId, discordLocale)
                    emoji = DiscordPartialEmoji(name = "⏸")
                }
            }
            interactionButton(
                style = ButtonStyle.Secondary,
                customId = ComponentsEnum.PLAYER_SKIP.customId
            ) {
                label = localizationService.getString(LocalizationKeys.PLAYER_SKIP, guildId, discordLocale)
                emoji = DiscordPartialEmoji(name = "⏭")
            }
            interactionButton(
                style = ButtonStyle.Secondary,
                customId = ComponentsEnum.PLAYER_SHUFFLE.customId
            ) {
                label = localizationService.getString(LocalizationKeys.PLAYER_SHUFFLE, guildId, discordLocale)
                emoji = DiscordPartialEmoji(name = "\uD83D\uDD00")
            }
            interactionButton(
                style = ButtonStyle.Secondary,
                customId = ComponentsEnum.PLAYER_RECONNECT.customId
            ) {
                label = localizationService.getString(LocalizationKeys.PLAYER_RECONNECT, guildId, discordLocale)
                emoji = DiscordPartialEmoji(name = "🔄")
            }
            interactionButton(
                style = ButtonStyle.Danger,
                customId = ComponentsEnum.PLAYER_DISCONNECT.customId
            ) {
                label = localizationService.getString(LocalizationKeys.PLAYER_DISCONNECT, guildId, discordLocale)
            }
        }
    )

private suspend fun List<TrackBO>.getDisplayQueue(
    localizationService: LocalizationService,
    guildId: Snowflake?,
    discordLocale: Locale?
): String {
    val firstTrack = getOrNull(0)
    val secondTrack = getOrNull(1)
    val thirdTrack = getOrNull(2)
    val queueRemaining = (size - QUEUE_PREVIEW_SIZE).takeIf { it > 0 }
    val duration = filterNot {
        it.isStream || it.durationMillis == PlayerTrack.UNKNOWN_DURATION
    }.sumOf { it.durationMillis }.toDisplayDuration()
    return firstTrack?.getDisplayNameAndDuration(localizationService, guildId, discordLocale)
        ?.plus(
            secondTrack?.let {
                "\n${it.getDisplayNameAndDuration(
                    localizationService,
                    guildId,
                    discordLocale
                )}"
            }.orEmpty()
        )
        ?.plus(
            thirdTrack?.let {
                "\n${it.getDisplayNameAndDuration(
                    localizationService,
                    guildId,
                    discordLocale
                )}"
            }.orEmpty()
        )
        ?.plus(
            queueRemaining?.let {
                "\n".plus(
                    localizationService.getStringFormat(
                        key = LocalizationKeys.PLAYER_QUEUE_TRACKS_REMAINING,
                        guildId = guildId,
                        discordLocale = discordLocale,
                        arguments = arrayOf(it)
                    )
                )
            }.orEmpty()
        )
        ?.plus(
            "\n".plus(
                localizationService.getStringFormat(
                    key = LocalizationKeys.PLAYER_DURATION_REMAINING,
                    guildId = guildId,
                    discordLocale = discordLocale,
                    arguments = arrayOf(duration)
                )
            )
        ).orEmpty()
}

private suspend fun TrackBO.getDisplayNameAndDuration(
    localizationService: LocalizationService,
    guildId: Snowflake?,
    discordLocale: Locale?
): String {
    val displayDuration = if (durationMillis != PlayerTrack.UNKNOWN_DURATION) {
        durationMillis.toDisplayDuration()
    } else {
        localizationService.getString(LocalizationKeys.PLAYER_TRACK_DURATION_STREAM, guildId, discordLocale)
    }
    return "${getDisplayTrackName()} ($displayDuration)"
}

private fun Long.toDisplayDuration() = toDuration(
    DurationUnit.MILLISECONDS
).toComponents { hours, minutes, seconds, _ ->
    "%02d:%02d:%02d".format(hours, minutes, seconds)
}
