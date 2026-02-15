package es.wokis.commands.config

import dev.kord.common.entity.Permissions
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import es.wokis.commands.CommandName
import es.wokis.commands.SubCommand
import es.wokis.localization.LocalizationKeys
import es.wokis.services.config.ConfigService
import es.wokis.services.localization.LocalizationService
import es.wokis.utils.Log
import es.wokis.utils.orDefaultLocale
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val ARGUMENT_SECTION = "section"
private const val ARGUMENT_KEY = "key"
private const val ARGUMENT_VALUE = "value"
private const val CONFIG_PATH = "./data/config.json"

class ConfigSetCommand(
    private val configService: ConfigService,
    private val localizationService: LocalizationService
) : SubCommand {

    private val validSections = mapOf(
        "database" to listOf("enabled", "username", "password", "database"),
        "youtube" to listOf("enabled", "oauth2_token", "po_token", "visitor_data", "remote_cipher_url", "remote_cipher_password"),
        "deezer" to listOf("enabled", "master_decryption_key", "arl_token"),
        "spotify" to listOf("enabled", "client_id", "client_secret", "custom_endpoint"),
        "tidal" to listOf("enabled", "country_code", "token"),
        "kokoro" to listOf("enabled", "base_url", "default_voice", "default_speed", "default_lang_code")
    )

    override suspend fun onRegisterCommand(builder: GlobalChatInputCreateBuilder) {
        builder.apply {
            subCommand(CommandName.Config.Set.commandName, localizationService.getString(LocalizationKeys.CONFIG_SET_COMMAND_DESCRIPTION)) {
                descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.CONFIG_SET_COMMAND_DESCRIPTION)
                string(ARGUMENT_SECTION, localizationService.getString(LocalizationKeys.CONFIG_SET_SECTION_DESCRIPTION)) {
                    descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.CONFIG_SET_SECTION_DESCRIPTION)
                    required = true
                }
                string(ARGUMENT_KEY, localizationService.getString(LocalizationKeys.CONFIG_SET_KEY_DESCRIPTION)) {
                    descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.CONFIG_SET_KEY_DESCRIPTION)
                    required = true
                }
                string(ARGUMENT_VALUE, localizationService.getString(LocalizationKeys.CONFIG_SET_VALUE_DESCRIPTION)) {
                    descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.CONFIG_SET_VALUE_DESCRIPTION)
                    required = true
                }
            }
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val locale = interaction.guildLocale.orDefaultLocale()
        val section = interaction.command.strings[ARGUMENT_SECTION]
        val key = interaction.command.strings[ARGUMENT_KEY]
        val value = interaction.command.strings[ARGUMENT_VALUE]

        if (section == null || section !in validSections) {
            response.respond {
                content = localizationService.getString(LocalizationKeys.CONFIG_INVALID_SECTION, locale)
            }
            return
        }

        if (key == null || key !in validSections[section]!!) {
            response.respond {
                content = localizationService.getString(LocalizationKeys.CONFIG_INVALID_KEY, locale)
            }
            return
        }

        if (value.isNullOrEmpty()) {
            response.respond {
                content = localizationService.getString(LocalizationKeys.ERROR_NO_CONTENT_PROVIDED, locale)
            }
            return
        }

        if (section == "discord_bot_token" || (section == "database" && key == "password")) {
            response.respond {
                content = localizationService.getString(LocalizationKeys.CONFIG_CANNOT_MODIFY_TOKEN, locale)
            }
            return
        }

        try {
            updateConfigValue(section, key, value!!)
            configService.reload()
            Log.info("Config updated via command: $section.$key = $value by user ${interaction.user.id}")
            response.respond {
                content = localizationService.getStringFormat(
                    LocalizationKeys.CONFIG_SET_SUCCESS,
                    locale,
                    arrayOf("$section.$key", value)
                )
            }
        } catch (e: Exception) {
            Log.error("Failed to update config via command", e)
            response.respond {
                content = localizationService.getString(LocalizationKeys.ERROR_UNEXPECTED, locale)
            }
        }
    }

    private fun updateConfigValue(section: String, key: String, value: String) {
        val configFile = File(CONFIG_PATH)
        val json = Json { prettyPrint = true }
        val configJson = json.parseToJsonElement(configFile.readText()) as JsonObject
        val sectionJson = configJson[section] as? JsonObject
            ?: throw IllegalStateException("Section $section not found in config")

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

        configFile.writeText(json.encodeToString(
            JsonObject.serializer(),
            updatedConfigJson
        ))
    }
}
