package es.wokis.player.message

import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.GuildId
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.PlayerMessageKeys
import es.wokis.message.EmbedFieldView
import es.wokis.message.EmbedView
import es.wokis.message.MessageView
import es.wokis.player.engine.model.PlayerSnapshot
import es.wokis.player.engine.model.PlayerTrack
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/** Brand green shared by every player embed. */
const val PLAYER_BRAND_COLOR: Int = 0x01B05B

private const val QUEUE_PREVIEW_SIZE = 3
private const val DISPLAY_DURATION_FORMAT = "%02d:%02d:%02d"

/**
 * Builds the neutral [MessageView] for the Kord-parity player embed.
 *
 * Layout, field order, labels and the empty-queue description mirror Kord's `createPlayerEmbed`.
 * Playback position is intentionally absent: the Kord player disables position updates.
 * Action rows stay empty until the button stage.
 */
class PlayerMessageViewFactory(
    private val localizationService: GuildLocalizationService,
) {

    suspend fun createPlayerView(
        guildName: String,
        snapshot: PlayerSnapshot,
        guildId: GuildId? = null,
        guildDefaultLocale: LocaleCode? = null,
    ): MessageView {
        val current = snapshot.current
        val fields = mutableListOf<EmbedFieldView>()
        current?.let { fields += trackFields(it, guildId, guildDefaultLocale) }
        if (snapshot.queue.isNotEmpty()) {
            fields += EmbedFieldView(
                name = getString(PlayerMessageKeys.PLAYER_SERVER_QUEUE, guildId, guildDefaultLocale),
                value = queuePreview(snapshot.queue, guildId, guildDefaultLocale),
            )
        }
        val description = if (snapshot.queue.isEmpty() && current == null) {
            getString(PlayerMessageKeys.PLAYER_SERVER_QUEUE_EMPTY, guildId, guildDefaultLocale)
        } else {
            null
        }
        return MessageView(
            embed = EmbedView(
                title = getStringFormat(
                    key = PlayerMessageKeys.PLAYER_TITLE,
                    guildId = guildId,
                    guildDefaultLocale = guildDefaultLocale,
                    arguments = arrayOf(guildName),
                ),
                description = description,
                color = PLAYER_BRAND_COLOR,
                // Kord always assigns the thumbnail; a blank url is dropped so JDA never renders an empty image.
                thumbnailUrl = (current?.customFavicon ?: current?.artworkUrl)?.takeIf(String::isNotBlank),
                fields = fields,
            ),
        )
    }

    private suspend fun trackFields(
        track: PlayerTrack,
        guildId: GuildId?,
        guildDefaultLocale: LocaleCode?,
    ): List<EmbedFieldView> {
        val duration = track.durationMillis.toDisplayDuration()
        val durationValue = if (track.durationMillis == PlayerTrack.UNKNOWN_DURATION) {
            getString(trackDurationKey(track.isStream), guildId, guildDefaultLocale)
        } else {
            duration
        }
        return listOf(
            EmbedFieldView(
                name = getString(PlayerMessageKeys.PLAYER_CURRENT_TRACK, guildId, guildDefaultLocale),
                value = track.kordTrackName(),
            ),
            EmbedFieldView(
                name = getString(PlayerMessageKeys.PLAYER_TRACK_DURATION, guildId, guildDefaultLocale),
                value = durationValue,
            ),
        )
    }

    private suspend fun queuePreview(
        queue: List<PlayerTrack>,
        guildId: GuildId?,
        guildDefaultLocale: LocaleCode?,
    ): String {
        val remaining = (queue.size - QUEUE_PREVIEW_SIZE).takeIf { it > 0 }
        val remainingDuration = queue
            .filterNot { it.isStream || it.durationMillis == PlayerTrack.UNKNOWN_DURATION }
            .sumOf(PlayerTrack::durationMillis)
            .toDisplayDuration()
        return buildString {
            queue.take(QUEUE_PREVIEW_SIZE).forEachIndexed { index, track ->
                if (index > 0) append('\n')
                append(track.displayName)
                append(" (")
                append(
                    if (track.durationMillis == PlayerTrack.UNKNOWN_DURATION) {
                        getString(PlayerMessageKeys.PLAYER_TRACK_DURATION_STREAM, guildId, guildDefaultLocale)
                    } else {
                        track.durationMillis.toDisplayDuration()
                    },
                )
                append(')')
            }
            if (remaining != null) {
                append('\n').append(
                    getStringFormat(
                        key = PlayerMessageKeys.PLAYER_QUEUE_TRACKS_REMAINING,
                        guildId = guildId,
                        guildDefaultLocale = guildDefaultLocale,
                        arguments = arrayOf(remaining),
                    ),
                )
            }
            append('\n').append(
                getStringFormat(
                    key = PlayerMessageKeys.PLAYER_DURATION_REMAINING,
                    guildId = guildId,
                    guildDefaultLocale = guildDefaultLocale,
                    arguments = arrayOf(remainingDuration),
                ),
            )
        }
    }

    private suspend fun getString(key: String, guildId: GuildId?, guildDefaultLocale: LocaleCode?): String =
        localizationService.getString(key = key, guildId = guildId, guildDefaultLocale = guildDefaultLocale)

    private suspend fun getStringFormat(
        key: String,
        guildId: GuildId?,
        guildDefaultLocale: LocaleCode?,
        vararg arguments: Any,
    ): String = localizationService.getStringFormat(
        key = key,
        guildId = guildId,
        guildDefaultLocale = guildDefaultLocale,
        arguments = arguments,
    )
}

private fun trackDurationKey(isStream: Boolean): String =
    if (isStream) {
        PlayerMessageKeys.PLAYER_TRACK_DURATION_STREAM
    } else {
        PlayerMessageKeys.PLAYER_TRACK_DURATION_UNKNOWN
    }

/** Kord renders a custom name bold, otherwise the bolded title followed by the author. */
private fun PlayerTrack.kordTrackName(): String =
    customName?.let { "**$it**" } ?: "**$title**\n$author"

private fun Long.toDisplayDuration(): String = toDuration(DurationUnit.MILLISECONDS)
    .toComponents { hours, minutes, seconds, _ -> DISPLAY_DURATION_FORMAT.format(hours, minutes, seconds) }
