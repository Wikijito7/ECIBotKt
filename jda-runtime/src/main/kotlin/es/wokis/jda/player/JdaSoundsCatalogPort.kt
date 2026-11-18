package es.wokis.jda.player

import es.wokis.commands.sound.SoundsCatalogPort

/** Sound catalog backed by the repository's `./audio/` folder. */
internal class JdaSoundsCatalogPort : SoundsCatalogPort {

    override suspend fun soundNames(): List<String> = JdaSoundFiles.names()

    override suspend fun soundExists(name: String): Boolean = JdaSoundFiles.exists(name)
}
