package spike.jdadave

import java.util.Locale

/**
 * Environment-driven configuration for the staging probe.
 *
 * The token is deliberately kept out of logs and out of [toString]. Only the exact
 * variable names below are read; there are no defaults for the token or guild.
 */
data class SpikeConfig(
    val botToken: String,
    val testGuildId: Long,
    val keepOnline: Boolean,
    val readyTimeoutMillis: Long,
    val voiceConnectTimeoutMillis: Long,
) {
    override fun toString(): String =
        "SpikeConfig(testGuildId=$testGuildId, keepOnline=$keepOnline, " +
            "readyTimeoutMillis=$readyTimeoutMillis, voiceConnectTimeoutMillis=$voiceConnectTimeoutMillis)"

    companion object {
        const val ENV_BOT_TOKEN = "DISCORD_BOT_TOKEN"
        const val ENV_TEST_GUILD_ID = "DISCORD_TEST_GUILD_ID"
        const val ENV_KEEP_ONLINE = "SPIKE_KEEP_ONLINE"

        /** Default deadline for the voice connection, shared with [VoiceTestProbe]. */
        const val DEFAULT_VOICE_CONNECT_TIMEOUT_MILLIS = 20_000L

        fun fromEnv(env: Map<String, String> = System.getenv()): SpikeConfig {
            val token = env[ENV_BOT_TOKEN]?.trim().orEmpty()
            if (token.isEmpty()) {
                throw ConfigException(
                    "Missing $ENV_BOT_TOKEN. Provide the staging bot token through the environment " +
                        "(for example with docker --env-file). It is never read from source.",
                )
            }

            val guildRaw = env[ENV_TEST_GUILD_ID]?.trim().orEmpty()
            if (guildRaw.isEmpty()) {
                throw ConfigException(
                    "Missing $ENV_TEST_GUILD_ID. Provide the positive numeric ID of the throwaway staging guild.",
                )
            }
            val guildId = guildRaw.toLongOrNull()
                ?: throw ConfigException("$ENV_TEST_GUILD_ID must be a numeric Discord guild ID, not '$guildRaw'.")
            if (guildId <= 0L) {
                throw ConfigException(
                    "$ENV_TEST_GUILD_ID must be a positive numeric Discord guild ID, not '$guildRaw'.",
                )
            }

            val keepOnline = env[ENV_KEEP_ONLINE]?.trim()?.lowercase(Locale.ROOT)
                .let { it == "1" || it == "true" || it == "yes" }

            return SpikeConfig(
                botToken = token,
                testGuildId = guildId,
                keepOnline = keepOnline,
                readyTimeoutMillis = 30_000,
                voiceConnectTimeoutMillis = DEFAULT_VOICE_CONNECT_TIMEOUT_MILLIS,
            )
        }
    }
}

class ConfigException(message: String) : RuntimeException(message)
