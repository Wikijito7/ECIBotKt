package es.wokis.services.config

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConfigMigrationServiceTest {

    // TODO: Refactor test to be shorter (issue: #detekt-suppress)
    @Suppress("LongMethod", "ForbiddenComment")
    @Test
    fun `Given old config without hugging_chat When buildMergedJson is called Then return merged config`() {
        // Given
        val oldJson = JsonObject(
            mapOf(
                "discord_bot_token" to JsonPrimitive("test_token"),
                "debug" to JsonPrimitive(false),
                "database" to JsonObject(
                    mapOf(
                        "enabled" to JsonPrimitive(true),
                        "username" to JsonPrimitive("user"),
                        "password" to JsonPrimitive("pass"),
                        "database" to JsonPrimitive("db")
                    )
                ),
                "youtube" to JsonObject(
                    mapOf(
                        "enabled" to JsonPrimitive(true)
                    )
                ),
                "deezer" to JsonObject(
                    mapOf(
                        "enabled" to JsonPrimitive(false)
                    )
                )
            )
        )

        val templateJson = JsonObject(
            mapOf(
                "discord_bot_token" to JsonPrimitive(""),
                "debug" to JsonPrimitive(false),
                "database" to JsonObject(
                    mapOf(
                        "enabled" to JsonPrimitive(false),
                        "username" to JsonPrimitive("db_username"),
                        "password" to JsonPrimitive("db_password"),
                        "database" to JsonPrimitive("db_database")
                    )
                ),
                "youtube" to JsonObject(
                    mapOf(
                        "enabled" to JsonPrimitive(false),
                        "oauth2_token" to JsonPrimitive(null)
                    )
                ),
                "deezer" to JsonObject(
                    mapOf(
                        "enabled" to JsonPrimitive(false),
                        "master_decryption_key" to JsonPrimitive(""),
                        "arl_token" to JsonPrimitive("")
                    )
                ),
                "spotify" to JsonObject(
                    mapOf(
                        "enabled" to JsonPrimitive(false),
                        "client_id" to JsonPrimitive(""),
                        "client_secret" to JsonPrimitive(""),
                        "custom_endpoint" to JsonPrimitive("")
                    )
                ),
                "tidal" to JsonObject(
                    mapOf(
                        "enabled" to JsonPrimitive(false),
                        "country_code" to JsonPrimitive("ES"),
                        "token" to JsonPrimitive("")
                    )
                ),
                "kokoro" to JsonObject(
                    mapOf(
                        "enabled" to JsonPrimitive(false),
                        "base_url" to JsonPrimitive(""),
                        "default_voice" to JsonPrimitive("am_santa"),
                        "default_speed" to JsonPrimitive(1.0f),
                        "default_lang_code" to JsonPrimitive("en")
                    )
                )
            )
        )

        // When
        val result = JsonObjectBuilder.buildMergedJson(oldJson, templateJson)

        // Then
        assertTrue(result.containsKey("discord_bot_token"))
        assertTrue(result.containsKey("database"))
        assertTrue(result.containsKey("youtube"))
        assertTrue(result.containsKey("spotify"))
        assertTrue(result.containsKey("tidal"))
        assertTrue(result.containsKey("kokoro"))
    }

    @Test
    fun `Given old config with enabled section containing data When buildMergedJson is called Then enabled should be true`() {
        // Given
        val oldJson = JsonObject(
            mapOf(
                "discord_bot_token" to JsonPrimitive("test_token"),
                "debug" to JsonPrimitive(false),
                "kokoro" to JsonObject(
                    mapOf(
                        "base_url" to JsonPrimitive("http://localhost:5000"),
                        "default_voice" to JsonPrimitive("test_voice")
                    )
                )
            )
        )

        val templateJson = JsonObject(
            mapOf(
                "discord_bot_token" to JsonPrimitive(""),
                "debug" to JsonPrimitive(false),
                "kokoro" to JsonObject(
                    mapOf(
                        "enabled" to JsonPrimitive(false),
                        "base_url" to JsonPrimitive(""),
                        "default_voice" to JsonPrimitive("am_santa"),
                        "default_speed" to JsonPrimitive(1.0f),
                        "default_lang_code" to JsonPrimitive("en")
                    )
                )
            )
        )

        // When
        val result = JsonObjectBuilder.buildMergedJson(oldJson, templateJson)

        // Then
        val kokoro = result["kokoro"] as JsonObject
        assertTrue(kokoro["enabled"]?.let { it is JsonPrimitive && it.content == "true" } ?: false)
        assertEquals("http://localhost:5000", kokoro["base_url"]?.let { (it as JsonPrimitive).content })
    }

    @Test
    fun `Given old config with empty section When buildMergedJson is called Then enabled should be template default`() {
        // Given
        val oldJson = JsonObject(
            mapOf(
                "discord_bot_token" to JsonPrimitive("test_token"),
                "debug" to JsonPrimitive(false),
                "kokoro" to JsonObject(
                    mapOf()
                )
            )
        )

        val templateJson = JsonObject(
            mapOf(
                "discord_bot_token" to JsonPrimitive(""),
                "debug" to JsonPrimitive(false),
                "kokoro" to JsonObject(
                    mapOf(
                        "enabled" to JsonPrimitive(false),
                        "base_url" to JsonPrimitive(""),
                        "default_voice" to JsonPrimitive("am_santa"),
                        "default_speed" to JsonPrimitive(1.0f),
                        "default_lang_code" to JsonPrimitive("en")
                    )
                )
            )
        )

        // When
        val result = JsonObjectBuilder.buildMergedJson(oldJson, templateJson)

        // Then
        val kokoro = result["kokoro"] as JsonObject
        assertTrue(kokoro["enabled"]?.let { it is JsonPrimitive && it.content == "false" } ?: false)
    }

    @Test
    fun `Given old config with new fields missing When buildMergedJson is called Then add missing fields from template`() {
        // Given
        val oldJson = JsonObject(
            mapOf(
                "discord_bot_token" to JsonPrimitive("test_token"),
                "debug" to JsonPrimitive(false)
            )
        )

        val templateJson = JsonObject(
            mapOf(
                "discord_bot_token" to JsonPrimitive(""),
                "debug" to JsonPrimitive(false),
                "database" to JsonObject(
                    mapOf(
                        "enabled" to JsonPrimitive(false),
                        "username" to JsonPrimitive("db_username"),
                        "password" to JsonPrimitive("db_password"),
                        "database" to JsonPrimitive("db_database")
                    )
                )
            )
        )

        // When
        val result = JsonObjectBuilder.buildMergedJson(oldJson, templateJson)

        // Then
        assertTrue(result.containsKey("database"))
        val database = result["database"] as JsonObject
        assertTrue(database["enabled"]?.let { it is JsonPrimitive && it.content == "false" } ?: false)
        assertEquals("db_username", database["username"]?.let { (it as JsonPrimitive).content })
    }

    @Test
    fun `Given old config with unknown keys When buildMergedJson is called Then remove unknown keys`() {
        // Given
        val oldJson = JsonObject(
            mapOf(
                "discord_bot_token" to JsonPrimitive("test_token"),
                "debug" to JsonPrimitive(false),
                "unknown_section" to JsonObject(
                    mapOf(
                        "key" to JsonPrimitive("value")
                    )
                )
            )
        )

        val templateJson = JsonObject(
            mapOf(
                "discord_bot_token" to JsonPrimitive(""),
                "debug" to JsonPrimitive(false)
            )
        )

        // When
        val result = JsonObjectBuilder.buildMergedJson(oldJson, templateJson)

        // Then
        assertFalse(result.containsKey("unknown_section"))
    }
}
