package es.wokis.services.config

import es.wokis.utils.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val ARCHIVE_PATH = "./data/archive/"
private const val CONFIG_TEMPLATE_PATH = "/template/config_template.json"

class ConfigMigrationService(private val json: Json) {

    fun migrateConfig(configFile: File): Config {
        Log.info("Starting config migration...")
        
        if (!configFile.exists()) {
            throw IllegalStateException("Config file not found at ${configFile.absolutePath}")
        }

        val oldConfigJson = configFile.readText()
        
        backupConfig(oldConfigJson)
        
        val migratedJson = migrateJson(oldConfigJson)
        
        configFile.writeText(migratedJson)
        
        Log.info("Config migration completed successfully.")
        
        return json.decodeFromString<Config>(migratedJson)
    }

    private fun backupConfig(configContent: String) {
        val archiveDir = File(ARCHIVE_PATH)
        if (!archiveDir.exists()) {
            archiveDir.mkdirs()
        }

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val backupFile = File("$ARCHIVE_PATH/config-$timestamp.json")
        
        backupFile.writeText(configContent)
        Log.info("Config backed up to: ${backupFile.absolutePath}")
    }

    private fun migrateJson(oldJsonString: String): String {
        val templateJson = getTemplateJson()
        val oldJson = json.parseToJsonElement(oldJsonString) as JsonObject

        val migratedObject = JsonObjectBuilder.buildMergedJson(oldJson, templateJson)
        
        return json.encodeToString(
            JsonObject.serializer(),
            migratedObject
        )
    }

    private fun getTemplateJson(): JsonObject {
        val templateStream = {}::class.java.getResourceAsStream(CONFIG_TEMPLATE_PATH)
            ?: throw IllegalStateException("Config template not found")
        
        return json.parseToJsonElement(templateStream.bufferedReader().use { it.readText() }) as JsonObject
    }
}

object JsonObjectBuilder {

    fun buildMergedJson(oldJson: JsonObject, templateJson: JsonObject): JsonObject {
        val result = mutableMapOf<String, JsonElement>()

        for ((key, templateValue) in templateJson.entries) {
            val oldValue = oldJson[key]

            when {
                oldValue == null -> {
                    result[key] = templateValue
                }
                templateValue is JsonObject && oldValue is JsonObject -> {
                    result[key] = mergeObject(oldValue, templateValue)
                }
                else -> {
                    result[key] = oldValue
                }
            }
        }

        return JsonObject(result)
    }

    private fun mergeObject(oldObject: JsonObject, templateObject: JsonObject): JsonObject {
        val result = mutableMapOf<String, JsonElement>()
        val hasEnabledField = templateObject.containsKey("enabled")

        val oldObjectHasData = hasNonEmptyValues(oldObject)

        for ((key, templateValue) in templateObject.entries) {
            val oldValue = oldObject[key]

            when {
                oldValue == null -> {
                    if (key == "enabled" && hasEnabledField && oldObjectHasData) {
                        result[key] = JsonPrimitive(true)
                    } else {
                        result[key] = templateValue
                    }
                }
                templateValue is JsonObject && oldValue is JsonObject -> {
                    result[key] = mergeObject(oldValue, templateValue)
                }
                templateValue is JsonArray && oldValue is JsonArray -> {
                    result[key] = mergeArray(oldValue, templateValue)
                }
                else -> {
                    result[key] = oldValue
                }
            }
        }

        return JsonObject(result)
    }

    private fun hasNonEmptyValues(obj: JsonObject): Boolean {
        for ((key, value) in obj.entries) {
            if (key == "enabled") continue

            when (value) {
                is JsonPrimitive -> {
                    if (!value.content.isNullOrBlank()) {
                        return true
                    }
                }
                is JsonObject -> {
                    if (hasNonEmptyValues(value)) {
                        return true
                    }
                }
                is JsonArray -> {
                    if (value.isNotEmpty()) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun mergeArray(oldArray: JsonArray, templateArray: JsonArray): JsonArray {
        return oldArray
    }
}
