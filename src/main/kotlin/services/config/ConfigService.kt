package es.wokis.services.config

import es.wokis.exceptions.EmptyDeezerMasterDecryptionKeyException
import es.wokis.exceptions.EmptyDiscordTokenException
import es.wokis.utils.Log
import es.wokis.utils.getOrCreateFile
import kotlinx.serialization.json.Json

private const val CONFIG_PATH = "./data/"
private const val FILE_NAME = "config.json"
private val CONFIG_TEMPLATE = {}::class.java.getResourceAsStream("/template/config_template.json")

class ConfigService(
    private val json: Json,
    private val migrationService: ConfigMigrationService
) {

    val config: Config = loadFromFile()

    private fun loadFromFile(): Config {
        val file = getOrCreateFile(CONFIG_PATH, FILE_NAME, CONFIG_TEMPLATE)
        return try {
            val config = json.decodeFromString<Config>(file.readText())
            config.validate()
            config
        } catch (e: Exception) {
            Log.warning("Failed to parse config file: ${e.message}. Attempting migration...")
            try {
                val migratedConfig = migrationService.migrateConfig(file)
                migratedConfig.validate()
                migratedConfig
            } catch (migrationError: Exception) {
                Log.error("Config migration failed: ${migrationError.message}")
                throw e
            }
        }
    }

    fun reload(): Config = loadFromFile()

}

fun Config.validate() {
    if (discordBotToken.isEmpty()) {
        throw EmptyDiscordTokenException()
    }
    if (deezer.enabled && deezer.masterDecryptionKey.isEmpty()) {
        throw EmptyDeezerMasterDecryptionKeyException()
    }
}
