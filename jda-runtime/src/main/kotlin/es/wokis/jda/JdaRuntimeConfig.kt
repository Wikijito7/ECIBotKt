package es.wokis.jda

import es.wokis.domain.model.GuildId

/**
 * Validated environment configuration for the isolated JDA runtime.
 *
 * Values are immutable and validated before any JDA client is created. The token is deliberately
 * kept out of [toString] and out of every validation error, so it cannot leak through logs.
 */
internal data class JdaRuntimeConfig(
    val token: String,
    val guildId: GuildId,
) {
    override fun toString(): String = "JdaRuntimeConfig(guildId=$guildId)"

    companion object {
        const val ENV_TOKEN = "DISCORD_BOT_TOKEN"
        const val ENV_GUILD_ID = "DISCORD_DEV_GUILD_ID"

        /**
         * Reads and validates the runtime configuration from [env].
         *
         * @throws JdaRuntimeConfigException when the token is missing or blank, or when the guild
         * id is missing, non-numeric or not positive. Messages name the environment variable but
         * never include the configured token.
         */
        fun fromEnv(env: Map<String, String> = System.getenv()): JdaRuntimeConfig {
            val token = env[ENV_TOKEN]?.trim().orEmpty()
            if (token.isEmpty()) {
                throw JdaRuntimeConfigException(
                    "Missing $ENV_TOKEN. Provide it through the environment; it is never read from source.",
                )
            }

            val rawGuildId = env[ENV_GUILD_ID]?.trim().orEmpty()
            if (rawGuildId.isEmpty()) {
                throw JdaRuntimeConfigException(
                    "Missing $ENV_GUILD_ID. Provide the positive numeric id of the development guild.",
                )
            }
            val guildId = rawGuildId.toLongOrNull()
            if (guildId == null || guildId <= 0L) {
                throw JdaRuntimeConfigException(
                    "$ENV_GUILD_ID must be a positive numeric guild id; global command registration is not allowed.",
                )
            }

            return JdaRuntimeConfig(
                token = token,
                guildId = GuildId.fromDecimal(guildId.toString()),
            )
        }
    }
}

/**
 * Thrown when [JdaRuntimeConfig.fromEnv] cannot build a valid configuration.
 *
 * Messages never contain the configured bot token.
 */
internal class JdaRuntimeConfigException(message: String) : IllegalArgumentException(message)
