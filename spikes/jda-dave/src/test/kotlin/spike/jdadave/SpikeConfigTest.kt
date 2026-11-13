package spike.jdadave

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpikeConfigTest {

    @Test
    fun `parses a valid environment and trims values`() {
        val config = SpikeConfig.fromEnv(
            mapOf(
                SpikeConfig.ENV_BOT_TOKEN to "  staging-token-value  ",
                SpikeConfig.ENV_TEST_GUILD_ID to " 123456789012345678 ",
                SpikeConfig.ENV_KEEP_ONLINE to " TRUE ",
            ),
        )

        assertEquals("staging-token-value", config.botToken)
        assertEquals(123_456_789_012_345_678L, config.testGuildId)
        assertTrue(config.keepOnline)
        assertEquals(30_000L, config.readyTimeoutMillis)
        assertEquals(20_000L, config.voiceConnectTimeoutMillis)
    }

    @Test
    fun `the voice connect timeout defaults to the shared 20 second constant`() {
        assertEquals(20_000L, SpikeConfig.DEFAULT_VOICE_CONNECT_TIMEOUT_MILLIS)

        val config = SpikeConfig.fromEnv(
            mapOf(
                SpikeConfig.ENV_BOT_TOKEN to "token",
                SpikeConfig.ENV_TEST_GUILD_ID to "123",
            ),
        )

        assertEquals(SpikeConfig.DEFAULT_VOICE_CONNECT_TIMEOUT_MILLIS, config.voiceConnectTimeoutMillis)
        assertTrue(config.voiceConnectTimeoutMillis > 0, "the connect deadline must be positive")
    }

    @Test
    fun `accepts the largest positive guild id`() {
        val config = SpikeConfig.fromEnv(
            mapOf(
                SpikeConfig.ENV_BOT_TOKEN to "token",
                SpikeConfig.ENV_TEST_GUILD_ID to Long.MAX_VALUE.toString(),
            ),
        )

        assertEquals(Long.MAX_VALUE, config.testGuildId)
    }

    @Test
    fun `rejects a zero guild id`() {
        val error = assertFailsWith<ConfigException> {
            SpikeConfig.fromEnv(
                mapOf(
                    SpikeConfig.ENV_BOT_TOKEN to "token",
                    SpikeConfig.ENV_TEST_GUILD_ID to "0",
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains(SpikeConfig.ENV_TEST_GUILD_ID))
        assertTrue(error.message.orEmpty().contains("positive"))
    }

    @Test
    fun `rejects a negative guild id`() {
        val error = assertFailsWith<ConfigException> {
            SpikeConfig.fromEnv(
                mapOf(
                    SpikeConfig.ENV_BOT_TOKEN to "token",
                    SpikeConfig.ENV_TEST_GUILD_ID to "-42",
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains(SpikeConfig.ENV_TEST_GUILD_ID))
        assertTrue(error.message.orEmpty().contains("positive"))
    }

    @Test
    fun `rejects a non-numeric guild id`() {
        val error = assertFailsWith<ConfigException> {
            SpikeConfig.fromEnv(
                mapOf(
                    SpikeConfig.ENV_BOT_TOKEN to "token",
                    SpikeConfig.ENV_TEST_GUILD_ID to "not-a-number",
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains(SpikeConfig.ENV_TEST_GUILD_ID))
        assertTrue(error.message.orEmpty().contains("not-a-number"))
    }

    @Test
    fun `rejects a guild id that overflows Long`() {
        val error = assertFailsWith<ConfigException> {
            SpikeConfig.fromEnv(
                mapOf(
                    SpikeConfig.ENV_BOT_TOKEN to "token",
                    SpikeConfig.ENV_TEST_GUILD_ID to "9223372036854775808",
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains(SpikeConfig.ENV_TEST_GUILD_ID))
    }

    @Test
    fun `rejects a blank guild id`() {
        val error = assertFailsWith<ConfigException> {
            SpikeConfig.fromEnv(
                mapOf(
                    SpikeConfig.ENV_BOT_TOKEN to "token",
                    SpikeConfig.ENV_TEST_GUILD_ID to "   ",
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains(SpikeConfig.ENV_TEST_GUILD_ID))
    }

    @Test
    fun `rejects a missing guild id`() {
        val error = assertFailsWith<ConfigException> {
            SpikeConfig.fromEnv(mapOf(SpikeConfig.ENV_BOT_TOKEN to "token"))
        }

        assertTrue(error.message.orEmpty().contains(SpikeConfig.ENV_TEST_GUILD_ID))
    }

    @Test
    fun `rejects a missing token`() {
        val error = assertFailsWith<ConfigException> {
            SpikeConfig.fromEnv(mapOf(SpikeConfig.ENV_TEST_GUILD_ID to "123"))
        }

        assertTrue(error.message.orEmpty().contains(SpikeConfig.ENV_BOT_TOKEN))
    }

    @Test
    fun `rejects a blank token`() {
        val error = assertFailsWith<ConfigException> {
            SpikeConfig.fromEnv(
                mapOf(
                    SpikeConfig.ENV_BOT_TOKEN to "   ",
                    SpikeConfig.ENV_TEST_GUILD_ID to "123",
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains(SpikeConfig.ENV_BOT_TOKEN))
    }

    @Test
    fun `keepOnline only accepts the documented truthy values`() {
        val truthyValues = listOf("1", "true", "TRUE", "yes", " Yes ")
        val falsyValues = listOf("0", "false", "FALSE", "no", "2", "")

        truthyValues.forEach { raw ->
            val config = configWithKeepOnline(raw)
            assertTrue(config.keepOnline, "expected '$raw' to enable keepOnline")
        }
        falsyValues.forEach { raw ->
            val config = configWithKeepOnline(raw)
            assertFalse(config.keepOnline, "expected '$raw' to disable keepOnline")
        }
    }

    @Test
    fun `keepOnline defaults to false when the variable is absent`() {
        val config = SpikeConfig.fromEnv(
            mapOf(
                SpikeConfig.ENV_BOT_TOKEN to "token",
                SpikeConfig.ENV_TEST_GUILD_ID to "123",
            ),
        )

        assertFalse(config.keepOnline)
    }

    @Test
    fun `toString never exposes the token`() {
        val token = "super-secret-staging-token"
        val config = SpikeConfig.fromEnv(
            mapOf(
                SpikeConfig.ENV_BOT_TOKEN to token,
                SpikeConfig.ENV_TEST_GUILD_ID to "987654321",
            ),
        )

        val rendered = config.toString()

        assertFalse(rendered.contains(token), "toString must not contain the bot token")
        assertFalse(rendered.contains("botToken"), "toString must not name the token field")
        assertTrue(rendered.contains("987654321"))
        assertTrue(rendered.contains("SpikeConfig("))
    }

    @Test
    fun `configuration errors never expose the token`() {
        val token = "another-super-secret-token"
        val error = assertFailsWith<ConfigException> {
            SpikeConfig.fromEnv(
                mapOf(
                    SpikeConfig.ENV_BOT_TOKEN to token,
                    SpikeConfig.ENV_TEST_GUILD_ID to "invalid",
                ),
            )
        }

        assertFalse(error.message.orEmpty().contains(token))
    }

    private fun configWithKeepOnline(raw: String): SpikeConfig =
        SpikeConfig.fromEnv(
            mapOf(
                SpikeConfig.ENV_BOT_TOKEN to "token",
                SpikeConfig.ENV_TEST_GUILD_ID to "123",
                SpikeConfig.ENV_KEEP_ONLINE to raw,
            ),
        )
}
