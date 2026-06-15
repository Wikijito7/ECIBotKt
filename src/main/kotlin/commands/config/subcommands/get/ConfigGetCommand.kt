package es.wokis.commands.config

import dev.kord.common.entity.Choice
import dev.kord.common.entity.optional.Optional
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.behavior.interaction.suggest
import dev.kord.core.entity.interaction.AutoCompleteInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import es.wokis.commands.Autocomplete
import es.wokis.commands.CommandName
import es.wokis.commands.SubCommand
import es.wokis.localization.LocalizationKeys
import es.wokis.services.config.Config
import es.wokis.services.config.ConfigService
import es.wokis.services.localization.LocalizationService

private const val ARGUMENT_SECTION = "section"
private const val AUTOCOMPLETE_SUGGESTION_LIMIT = 25
private const val MASKED_VALUE = "****"

class ConfigGetCommand(
    private val configService: ConfigService,
    private val localizationService: LocalizationService
) : SubCommand, Autocomplete {

    override suspend fun onRegisterCommand(builder: GlobalChatInputCreateBuilder) {
        builder.apply {
            subCommand(
                CommandName.Config.Get.commandName,
                localizationService.getString(LocalizationKeys.CONFIG_GET_COMMAND_DESCRIPTION)
            ) {
                descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.CONFIG_GET_COMMAND_DESCRIPTION)
                string(
                    ARGUMENT_SECTION,
                    localizationService.getString(LocalizationKeys.CONFIG_GET_SECTION_DESCRIPTION)
                ) {
                    descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.CONFIG_GET_SECTION_DESCRIPTION)
                    required = true
                    autocomplete = true
                }
            }
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val guildId = interaction.data.guildId.value
        val discordLocale = interaction.guildLocale
        val section = interaction.command.strings[ARGUMENT_SECTION]

        if (section == null || section !in CONFIG_VALID_SECTIONS) {
            response.respond {
                content = localizationService.getString(
                    LocalizationKeys.CONFIG_INVALID_SECTION,
                    guildId = guildId,
                    discordLocale = discordLocale
                )
            }
            return
        }

        val config = configService.config
        val sectionData = getMaskedSectionData(config, section)

        response.respond {
            content = localizationService.getStringFormat(
                LocalizationKeys.CONFIG_GET_DISPLAY,
                guildId = guildId,
                discordLocale = discordLocale,
                arguments = arrayOf(section, sectionData)
            )
        }
    }

    override suspend fun onAutoComplete(interaction: AutoCompleteInteraction) {
        val input = interaction.command.strings[ARGUMENT_SECTION].orEmpty().lowercase()

        val suggestions = CONFIG_VALID_SECTIONS.keys
            .filter { it.lowercase().contains(input) }
            .take(AUTOCOMPLETE_SUGGESTION_LIMIT)
            .map { section ->
                Choice.StringChoice(
                    name = section,
                    nameLocalizations = Optional.Missing(),
                    value = section
                )
            }

        interaction.suggest(suggestions)
    }

    private fun getMaskedSectionData(config: Config, section: String): String {
        val sectionKeys = CONFIG_VALID_SECTIONS[section] ?: return ""
        val configMap = getSectionMap(config, section)

        return sectionKeys.joinToString("\n") { key ->
            val value = configMap[key]
            val displayValue = if (isSensitiveKey(section, key)) MASKED_VALUE else value.toString()
            "$key: $displayValue"
        }
    }

    private fun getSectionMap(config: Config, section: String): Map<String, Any?> = when (section) {
        "database" -> mapOf(
            "enabled" to config.database.enabled,
            "username" to config.database.username,
            "password" to config.database.password,
            "database" to config.database.database
        )
        "youtube" -> mapOf(
            "enabled" to config.youtube.enabled,
            "oauth2_token" to config.youtube.oauth2Token,
            "po_token" to config.youtube.poToken,
            "visitor_data" to config.youtube.visitorData,
            "remote_cipher_url" to config.youtube.remoteCipherUrl,
            "remote_cipher_password" to config.youtube.remoteCipherPassword
        )
        "deezer" -> mapOf(
            "enabled" to config.deezer.enabled,
            "master_decryption_key" to config.deezer.masterDecryptionKey,
            "arl_token" to config.deezer.arlToken
        )
        "spotify" -> mapOf(
            "enabled" to config.spotify.enabled,
            "client_id" to config.spotify.clientId,
            "client_secret" to config.spotify.clientSecret,
            "custom_endpoint" to config.spotify.customEndpoint
        )
        "tidal" -> mapOf(
            "enabled" to config.tidal.enabled,
            "country_code" to config.tidal.countryCode,
            "token" to config.tidal.token
        )
        "kokoro" -> mapOf(
            "enabled" to config.kokoro.enabled,
            "base_url" to config.kokoro.baseUrl,
            "default_voice" to config.kokoro.defaultVoice,
            "default_speed" to config.kokoro.defaultSpeed,
            "default_lang_code" to config.kokoro.defaultLangCode
        )
        else -> emptyMap()
    }
}
