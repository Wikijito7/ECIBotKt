package es.wokis.helper

import es.wokis.exceptions.DiscordKeyIsNullException
import es.wokis.utils.getOrCreateFile
import kotlinx.serialization.json.Json

private const val CONFIG_PATH = "./data/"
private const val FILE_NAME = "config.json"
private val CONFIG_TEMPLATE = {}::class.java.getResourceAsStream("/template/config_template.json")

class ConfigHelper {

    val config: Config = startUpConfig()

    private fun startUpConfig(): Config {
        val file = getOrCreateFile(CONFIG_PATH, FILE_NAME, CONFIG_TEMPLATE)
        val valuesFromFile = Json.decodeFromString<Config>(file.readLines().joinToString(separator = " ") { it.trim() })
        return valuesFromFile
    }
}

val ConfigHelper.isDebugMode: Boolean
    get() = config.debug

val ConfigHelper.discordToken: String
    get() = config.key.takeIf { it.isNotEmpty() } ?: throw DiscordKeyIsNullException
