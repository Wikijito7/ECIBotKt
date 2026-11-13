package es.wokis.jda

import es.wokis.domain.model.GuildId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JdaRuntimeConfigTest {

    @Test
    fun `Given valid token and guild id When fromEnv Then build the config`() {
        // When
        val config = JdaRuntimeConfig.fromEnv(
            env(
                token = "fake-token-value",
                guildId = "123456789012345678",
            ),
        )

        // Then
        assertEquals("fake-token-value", config.token)
        assertEquals(GuildId.fromDecimal("123456789012345678"), config.guildId)
    }

    @Test
    fun `Given surrounding whitespace When fromEnv Then trim token and guild id`() {
        // When
        val config = JdaRuntimeConfig.fromEnv(
            env(
                token = "  fake-token-value  ",
                guildId = "  42  ",
            ),
        )

        // Then
        assertEquals("fake-token-value", config.token)
        assertEquals(GuildId(42uL), config.guildId)
    }

    @Test
    fun `Given missing token When fromEnv Then fail naming the variable without any token value`() {
        // When
        val error = expectConfigError(env(token = null, guildId = "42"))

        // Then
        assertTrue(error.message.orEmpty().contains(JdaRuntimeConfig.ENV_TOKEN))
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
    }

    @Test
    fun `Given blank token When fromEnv Then fail naming the variable`() {
        // When
        val error = expectConfigError(env(token = "   ", guildId = "42"))

        // Then
        assertTrue(error.message.orEmpty().contains(JdaRuntimeConfig.ENV_TOKEN))
    }

    @Test
    fun `Given missing guild id When fromEnv Then fail naming the variable without the token`() {
        // When
        val error = expectConfigError(env(token = SECRET_TOKEN, guildId = null))

        // Then
        assertTrue(error.message.orEmpty().contains(JdaRuntimeConfig.ENV_GUILD_ID))
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
    }

    @Test
    fun `Given blank guild id When fromEnv Then fail naming the variable without the token`() {
        // When
        val error = expectConfigError(env(token = SECRET_TOKEN, guildId = "   "))

        // Then
        assertTrue(error.message.orEmpty().contains(JdaRuntimeConfig.ENV_GUILD_ID))
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
    }

    @Test
    fun `Given a non numeric guild id When fromEnv Then fail without the token`() {
        // When
        val error = expectConfigError(env(token = SECRET_TOKEN, guildId = "not-a-guild"))

        // Then
        assertTrue(error.message.orEmpty().contains(JdaRuntimeConfig.ENV_GUILD_ID))
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
    }

    @Test
    fun `Given a zero guild id When fromEnv Then reject global registration`() {
        // When
        val error = expectConfigError(env(token = SECRET_TOKEN, guildId = "0"))

        // Then
        assertTrue(error.message.orEmpty().contains(JdaRuntimeConfig.ENV_GUILD_ID))
        assertTrue(error.message.orEmpty().contains("positive"))
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
    }

    @Test
    fun `Given a negative guild id When fromEnv Then reject it`() {
        // When
        val error = expectConfigError(env(token = SECRET_TOKEN, guildId = "-1"))

        // Then
        assertTrue(error.message.orEmpty().contains(JdaRuntimeConfig.ENV_GUILD_ID))
        assertTrue(error.message.orEmpty().contains("positive"))
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
    }

    @Test
    fun `Given a guild id larger than Long max When fromEnv Then reject the overflow`() {
        // When
        val error = expectConfigError(env(token = SECRET_TOKEN, guildId = "9223372036854775808"))

        // Then
        assertTrue(error.message.orEmpty().contains(JdaRuntimeConfig.ENV_GUILD_ID))
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
    }

    @Test
    fun `Given an unsigned overflow guild id When fromEnv Then reject the overflow`() {
        // When
        val error = expectConfigError(env(token = SECRET_TOKEN, guildId = "18446744073709551616"))

        // Then
        assertTrue(error.message.orEmpty().contains(JdaRuntimeConfig.ENV_GUILD_ID))
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
    }

    @Test
    fun `Given a built config When rendered Then never include the token`() {
        // Given
        val config = JdaRuntimeConfig.fromEnv(env(token = SECRET_TOKEN, guildId = "42"))

        // When
        val rendered = config.toString()

        // Then
        assertEquals("JdaRuntimeConfig(guildId=42)", rendered)
        assertFalse(rendered.contains(SECRET_TOKEN))
    }

    private fun env(token: String?, guildId: String?): Map<String, String> = buildMap {
        token?.let { put(JdaRuntimeConfig.ENV_TOKEN, it) }
        guildId?.let { put(JdaRuntimeConfig.ENV_GUILD_ID, it) }
    }

    private fun expectConfigError(env: Map<String, String>): JdaRuntimeConfigException =
        assertThrows(JdaRuntimeConfigException::class.java) { JdaRuntimeConfig.fromEnv(env) }

    private companion object {
        private const val SECRET_TOKEN = "super-secret-bot-token"
    }
}
