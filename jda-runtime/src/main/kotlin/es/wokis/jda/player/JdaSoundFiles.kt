package es.wokis.jda.player

import java.io.File

private const val AUDIO_FOLDER = "./audio/"
private const val AUDIO_EXTENSION = ".mp3"

/**
 * Local `audio/` access for `/sound` and `/sounds`; the runtime working directory is the repository
 * root, so the paths match the legacy Kord utilities.
 */
internal object JdaSoundFiles {

    /** Path Kord plays for [soundName]: the folder plus the fixed `.mp3` extension. */
    fun pathFor(soundName: String): String = "$AUDIO_FOLDER$soundName$AUDIO_EXTENSION"

    /** Exact-case probe, mirroring Kord's `File("./audio/<name>.mp3").exists()`. */
    fun exists(soundName: String): Boolean = File(pathFor(soundName)).exists()

    /** Mirrors `getFolderContent`: empty when the folder is missing or is a file. */
    fun names(): List<String> {
        val folder = File(AUDIO_FOLDER)
        if (!folder.exists() || folder.isFile) return emptyList()
        return folder.listFiles()?.map { file -> file.nameWithoutExtension }.orEmpty()
    }
}
