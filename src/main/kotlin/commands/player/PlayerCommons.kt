package es.wokis.commands.player

import com.sedmelluq.discord.lavaplayer.tools.Units.DURATION_MS_UNKNOWN
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
import es.wokis.localization.LocalizationKeys
import es.wokis.services.lavaplayer.model.TrackBO
import es.wokis.services.localization.LocalizationService
import es.wokis.utils.getDisplayTrackName
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private const val ENABLE_PLAYBACK_POSITION = false

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
            url = currentTrack?.customFavicon ?: currentTrack?.audioTrack?.info?.artworkUrl.orEmpty()
        }
        color = Color(0x01B05B)
        currentTrack?.let {
            val duration = it.audioTrack.duration.toDisplayDuration()
            val currentSeek = it.audioTrack.position.toDisplayDuration()
            field {
                name = localizationService.getString(key = LocalizationKeys.PLAYER_CURRENT_TRACK, guildId = guildId, discordLocale = discordLocale)
                value = it.getTrackName()
            }
            if (ENABLE_PLAYBACK_POSITION) {
                // TODO: Take a look in the future to solve discord update request error or delete it
                field {
                    name = localizationService.getString(key = LocalizationKeys.PLAYER_PLAYBACK_POSITION, guildId = guildId, discordLocale = discordLocale)
                    value = "`$currentSeek ${generatePlayerPosition(it.audioTrack.position, it.audioTrack.duration)} $duration`"
                }
            }
            field {
                name = localizationService.getString(key = LocalizationKeys.PLAYER_TRACK_DURATION, guildId = guildId, discordLocale = discordLocale)
                value = duration.takeUnless {
                    currentTrack.audioTrack.duration == DURATION_MS_UNKNOWN
                } ?: localizationService.getString(
                    key = if (currentTrack.audioTrack.info.isStream) LocalizationKeys.PLAYER_TRACK_DURATION_STREAM else LocalizationKeys.PLAYER_TRACK_DURATION_UNKNOWN,
                    guildId = guildId,
                    discordLocale = discordLocale
                )
            }
        }
        if (queue.isNotEmpty()) {
            field {
                name = localizationService.getString(key = LocalizationKeys.PLAYER_SERVER_QUEUE, guildId = guildId, discordLocale = discordLocale)
                value = queue.getDisplayQueue(localizationService, guildId, discordLocale)
            }
        } else if (currentTrack == null) {
            description = localizationService.getString(key = LocalizationKeys.PLAYER_SERVER_QUEUE_EMPTY, guildId = guildId, discordLocale = discordLocale)
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

private fun TrackBO.getTrackName() = customName?.let {
    "**$customName**"
} ?: "**${audioTrack.info.title}**\n${audioTrack.info.author}"

private fun generatePlayerPosition(currentSeek: Long, maxDuration: Long): String {
    val safeMaxDuration = maxDuration.takeUnless { it == 0L } ?: 1
    val position = ((currentSeek / 1000f) / (safeMaxDuration / 1000f) * 9).toInt() + 1
    val playerString = "─────────".split("").toMutableList().apply {
        add(position, "●")
    }.joinToString(separator = "")
    return playerString
}

private suspend fun createPlayerComponents(localizationService: LocalizationService, guildId: Snowflake?, discordLocale: Locale?, isPaused: Boolean): MutableList<MessageComponentBuilder> =
    mutableListOf(
        ActionRowBuilder().apply {
            if (isPaused) {
                interactionButton(
                    style = ButtonStyle.Secondary,
                    customId = ComponentsEnum.PLAYER_RESUME.customId
                ) {
                    label = localizationService.getString(key = LocalizationKeys.PLAYER_RESUME, guildId = guildId, discordLocale = discordLocale)
                    emoji = DiscordPartialEmoji(name = "▶️")
                }
            } else {
                interactionButton(
                    style = ButtonStyle.Secondary,
                    customId = ComponentsEnum.PLAYER_PAUSE.customId
                ) {
                    label = localizationService.getString(key = LocalizationKeys.PLAYER_PAUSE, guildId = guildId, discordLocale = discordLocale)
                    emoji = DiscordPartialEmoji(name = "⏸")
                }
            }
            interactionButton(
                style = ButtonStyle.Secondary,
                customId = ComponentsEnum.PLAYER_SKIP.customId
            ) {
                label = localizationService.getString(key = LocalizationKeys.PLAYER_SKIP, guildId = guildId, discordLocale = discordLocale)
                emoji = DiscordPartialEmoji(name = "⏭")
            }
            interactionButton(
                style = ButtonStyle.Secondary,
                customId = ComponentsEnum.PLAYER_SHUFFLE.customId
            ) {
                label = localizationService.getString(key = LocalizationKeys.PLAYER_SHUFFLE, guildId = guildId, discordLocale = discordLocale)
                emoji = DiscordPartialEmoji(name = "\uD83D\uDD00")
            }
            interactionButton(
                style = ButtonStyle.Secondary,
                customId = ComponentsEnum.PLAYER_RECONNECT.customId
            ) {
                label = localizationService.getString(key = LocalizationKeys.PLAYER_RECONNECT, guildId = guildId, discordLocale = discordLocale)
                emoji = DiscordPartialEmoji(name = "🔄")
            }
            interactionButton(
                style = ButtonStyle.Secondary,
                customId = ComponentsEnum.PLAYER_LYRICS.customId
            ) {
                label = localizationService.getString(key = LocalizationKeys.PLAYER_LYRICS, guildId = guildId, discordLocale = discordLocale)
                emoji = DiscordPartialEmoji(name = "📝")
            }
            interactionButton(
                style = ButtonStyle.Danger,
                customId = ComponentsEnum.PLAYER_DISCONNECT.customId
            ) {
                label = localizationService.getString(key = LocalizationKeys.PLAYER_DISCONNECT, guildId = guildId, discordLocale = discordLocale)
            }
        }
    )

private suspend fun List<TrackBO>.getDisplayQueue(localizationService: LocalizationService, guildId: Snowflake?, discordLocale: Locale?): String {
    val firstTrack = getOrNull(0)
    val secondTrack = getOrNull(1)
    val thirdTrack = getOrNull(2)
    val queueRemaining = (size - 3).takeIf { it > 0 }
    val duration = filterNot { it.audioTrack.info.isStream || it.audioTrack.duration == DURATION_MS_UNKNOWN }.sumOf { it.audioTrack.duration }.toDisplayDuration()
    return firstTrack?.getDisplayNameAndDuration(localizationService, guildId, discordLocale)
        ?.plus(secondTrack?.let { "\n${it.getDisplayNameAndDuration(localizationService, guildId, discordLocale)}" }.orEmpty())
        ?.plus(thirdTrack?.let { "\n${it.getDisplayNameAndDuration(localizationService, guildId, discordLocale)}" }.orEmpty())
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

private suspend fun TrackBO.getDisplayNameAndDuration(localizationService: LocalizationService, guildId: Snowflake?, discordLocale: Locale?): String {
    val displayDuration = if (audioTrack.duration != DURATION_MS_UNKNOWN) {
        audioTrack.duration.toDisplayDuration()
    } else {
        localizationService.getString(LocalizationKeys.PLAYER_TRACK_DURATION_STREAM, guildId, discordLocale)
    }
    return "${getDisplayTrackName()} ($displayDuration)"
}

private fun Long.toDisplayDuration() = toDuration(DurationUnit.MILLISECONDS).toComponents { hours, minutes, seconds, _ ->
    "%02d:%02d:%02d".format(hours, minutes, seconds)
}
