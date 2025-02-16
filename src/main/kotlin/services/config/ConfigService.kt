package es.wokis.services.config

import es.wokis.exceptions.EmptyDiscordTokenException
import es.wokis.utils.getOrCreateFile
import kotlinx.serialization.json.Json

private const val CONFIG_PATH = "./data/"
private const val FILE_NAME = "config.json"
private val CONFIG_TEMPLATE = {}::class.java.getResourceAsStream("/template/config_template.json")

class ConfigService {

    val config: Config = loadFromFile()

    private fun loadFromFile(): Config {
        val file = getOrCreateFile(CONFIG_PATH, FILE_NAME, CONFIG_TEMPLATE)
        val config = Json.decodeFromString<Config>(file.readText())
        config.validate()
        return config
    }

}

fun Config.validate() {
    if (discordBotToken.isEmpty()) throw EmptyDiscordTokenException()
}
