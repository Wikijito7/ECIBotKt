package es.wokis.commands.player

import es.wokis.player.engine.model.PlayerTrack

/** Kord's embed field value limit; one page never exceeds it. */
private const val FIELD_VALUE_LIMIT = 1024

private const val TRACK_SEPARATOR = ", "
private const val FIRST_PAGE = 1
private const val PREVIOUS_PAGE_DELTA = -1
private const val NEXT_PAGE_DELTA = 1

/**
 * Splits the pending queue into embed pages the way Kord's `/queue` does: track display names are
 * appended as long as the field value stays inside the gateway limit.
 */
internal fun queuePages(queue: List<PlayerTrack>): List<String> {
    if (queue.isEmpty()) return emptyList()
    val pages = mutableListOf<String>()
    var page = queue.first().displayName
    queue.drop(1).forEach { track ->
        val appended = TRACK_SEPARATOR + track.displayName
        if (page.length + appended.length <= FIELD_VALUE_LIMIT) {
            page += appended
        } else {
            pages += page
            page = track.displayName
        }
    }
    if (page.isNotEmpty()) {
        pages += page
    }
    return pages
}

/** 1-based page encoded in a `queue_next::<page>` payload; the first page when absent or malformed. */
internal fun pageFromPayload(payload: String?): Int =
    payload?.toIntOrNull()?.takeIf { it >= FIRST_PAGE } ?: FIRST_PAGE

/** Page reached by moving one step forward or back from [currentPage], clamped to the page bounds. */
internal fun adjacentPage(currentPage: Int, forward: Boolean, pageCount: Int): Int =
    (currentPage + if (forward) NEXT_PAGE_DELTA else PREVIOUS_PAGE_DELTA)
        .coerceIn(FIRST_PAGE, pageCount.coerceAtLeast(FIRST_PAGE))
