package es.wokis.commands.sounds

/** Kord's invisible field name keeps the sound columns aligned. */
internal const val SOUND_COLUMN_NAME = "\u2800"

/** Columns per `/sounds` page, matching Kord's registration. */
internal const val SOUND_COLUMNS = 3

private const val SOUND_SEPARATOR = "\u2800\u2800\n"
private const val MAX_SOUNDS_PER_COLUMN = 50
private const val FIELD_VALUE_LIMIT = 1024

/**
 * Packs sorted sound names into Kord's display columns: one name per line, at most 50 per column and
 * never beyond the embed field limit.
 */
internal fun soundColumns(soundNames: List<String>): List<String> {
    if (soundNames.isEmpty()) return emptyList()
    val columns = mutableListOf<String>()
    var currentColumn = soundNames.first()
    soundNames.drop(1).forEach { name ->
        val appendedName = SOUND_SEPARATOR + name
        val fitsColumn = currentColumn.split(SOUND_SEPARATOR).size < MAX_SOUNDS_PER_COLUMN &&
            currentColumn.length + appendedName.length <= FIELD_VALUE_LIMIT
        if (fitsColumn) {
            currentColumn += appendedName
        } else {
            columns += currentColumn
            currentColumn = name
        }
    }
    columns += currentColumn
    return columns
}

/** Sound pages: display columns sorted like Kord, then packed [SOUND_COLUMNS] per page. */
internal fun soundPages(soundNames: List<String>): List<List<String>> =
    soundColumns(soundNames.sorted()).sorted().chunked(SOUND_COLUMNS)
