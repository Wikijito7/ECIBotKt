package es.wokis.services.config

import es.wokis.data.response.ErrorType
import es.wokis.data.response.RemoteResponse
import es.wokis.exceptions.EmptyDeezerMasterDecryptionKeyException
import es.wokis.exceptions.EmptyDiscordTokenException
import es.wokis.utils.Log
import es.wokis.utils.getOrCreateFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

private const val CONFIG_PATH = "./data/"
private const val FILE_NAME = "config.json"
private val CONFIG_TEMPLATE = {}::class.java.getResourceAsStream("/template/config_template.json")

class ConfigService(
    private val json: Json,
    private val migrationService: ConfigMigrationService
) {
    private val _config = MutableStateFlow(loadFromFile())
    val config: Config get() = _config.value
    val configFlow: StateFlow<Config> = _config.asStateFlow()

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
                throw migrationError
            }
        }
    }

    fun reload(): RemoteResponse<Config> {
        return try {
            val newConfig = loadFromFile()
            _config.value = newConfig
            RemoteResponse.Success(newConfig)
        } catch (e: Exception) {
            Log.error("Failed to reload config", e)
            RemoteResponse.Error(ErrorType.UnknownError(e, "Failed to reload config"))
        }
    }

    fun updateConfigValue(section: String, key: String, value: String?): RemoteResponse<Unit> {
        return try {
            check(!value.isNullOrEmpty()) { "Value cannot be null or empty" }
            val configFile = File(CONFIG_PATH, FILE_NAME)
            val configJson = json.parseToJsonElement(configFile.readText()) as JsonObject
            val sectionJson =
                checkNotNull(configJson[section] as? JsonObject) { "Section $section not found in config" }

            val updatedSectionJson = JsonObject(
                sectionJson.toMutableMap().apply {
                    val newValue = when {
                        value.lowercase() == "true" -> JsonPrimitive(true)
                        value.lowercase() == "false" -> JsonPrimitive(false)
                        value.toDoubleOrNull() != null -> JsonPrimitive(value.toDouble())
                        else -> JsonPrimitive(value)
                    }
                    put(key, newValue)
                }
            )

            val updatedConfigJson = JsonObject(
                configJson.toMutableMap().apply {
                    put(section, updatedSectionJson)
                }
            )

            configFile.writeText(
                json.encodeToString(JsonObject.serializer(), updatedConfigJson)
            )

            val newConfig = loadFromFile()
            _config.value = newConfig
            RemoteResponse.Success(Unit)
        } catch (e: Exception) {
            Log.error("Failed to update config value", e)
            RemoteResponse.Error(ErrorType.UnknownError(e, "Failed to update config"))
        }
    }

    fun isOwner(userId: String): Boolean = config.botOwnerId.isNotEmpty() && config.botOwnerId == userId
}

fun Config.validate() {
    if (discordBotToken.isEmpty()) {
        throw EmptyDiscordTokenException()
    }
    if (deezer.enabled && deezer.masterDecryptionKey.isEmpty()) {
        throw EmptyDeezerMasterDecryptionKeyException()
    }
}
