package es.wokis.jda

import es.wokis.domain.model.GuildId
import es.wokis.exceptions.EmptyDeezerMasterDecryptionKeyException
import es.wokis.exceptions.EmptyDiscordTokenException
import es.wokis.player.engine.config.PlayerSourcesConfig
import es.wokis.player.engine.config.toPlayerSourcesConfig
import es.wokis.services.config.Config
import es.wokis.services.config.ConfigLoader
import es.wokis.services.config.DEFAULT_CONFIG_PATH
import java.nio.file.Path

/**
 * Validated configuration for the isolated JDA runtime.
 *
 * The Discord token and every player provider setting come from the **same shared
 * `./data/config.json` file** the root Kord runtime uses: one JSON schema, one loader, one set of
 * validation rules. Only the development guild id stays environment-only (`DISCORD_DEV_GUILD_ID`),
 * because it is a scoped target for command registration rather than bot configuration. The file
 * location can be pointed at a staging copy with `JDA_CONFIG_PATH`; the default is unchanged.
 *
 * Values are immutable and validated before any JDA client is created. The token is deliberately
 * kept out of [toString] and out of every validation error, so it cannot leak through logs. The
 * provider credentials are not rendered either: [sources] is not part of the rendered value.
 */
internal data class JdaRuntimeConfig(
    val token: String,
    val guildId: GuildId,
    val sources: PlayerSourcesConfig = PlayerSourcesConfig(),
) {
    override fun toString(): String = "JdaRuntimeConfig(guildId=$guildId)"

    companion object {
        const val ENV_GUILD_ID = "DISCORD_DEV_GUILD_ID"

        /**
         * Optional path to the shared configuration file.
         *
         * Absent or blank keeps [DEFAULT_CONFIG_PATH] (`./data/config.json`), so the existing
         * deployment behaviour is unchanged and a staging copy only needs this variable.
         */
        const val ENV_CONFIG_PATH = "JDA_CONFIG_PATH"

        /**
         * Reads the shared configuration file and validates the runtime environment.
         *
         * @throws JdaRuntimeConfigException when the file cannot be loaded/validated, when the
         * Discord token is empty or blank, or when the guild id is missing, non-numeric or not
         * positive. Messages name the variable or file but never include a configured secret.
         */
        fun fromEnv(
            env: Map<String, String> = System.getenv(),
            loader: ConfigLoader = ConfigLoader(),
        ): JdaRuntimeConfig {
            val guildId = GuildId.fromDecimal(readGuildId(env).toString())
            val configPath = readConfigPath(env)
            val sharedConfig = loadSharedConfig(configPath, loader)
            return JdaRuntimeConfig(
                token = sharedConfig.discordBotToken,
                guildId = guildId,
                sources = sharedConfig.toPlayerSourcesConfig(),
            )
        }

        private fun loadSharedConfig(path: Path, loader: ConfigLoader): Config = try {
            loader.load(path)
        } catch (error: EmptyDiscordTokenException) {
            // Safe to chain: this validation type only carries a fixed, secret-free message.
            throw JdaRuntimeConfigException(
                "The shared configuration at '$path' has an empty or blank discord_bot_token",
                error,
            )
        } catch (error: EmptyDeezerMasterDecryptionKeyException) {
            // Safe to chain: this validation type only carries a fixed, secret-free message.
            throw JdaRuntimeConfigException(
                "The shared configuration at '$path' enables Deezer without a master decryption key",
                error,
            )
        } catch (error: IllegalArgumentException) {
            // The exception type is reported, never its raw JSON message, so no configured value
            // can be echoed into the log. No cause is attached here: this branch can see a
            // decoder-originated exception whose message embeds the raw configuration text, and a
            // cause would reintroduce it through `getCause().message` or a stack-trace renderer.
            throw JdaRuntimeConfigException(
                "The shared configuration at '$path' is invalid (${error.javaClass.simpleName})",
            )
        }

        private fun readConfigPath(env: Map<String, String>): Path {
            val configured = env[ENV_CONFIG_PATH]?.trim()
            return if (configured.isNullOrEmpty()) DEFAULT_CONFIG_PATH else Path.of(configured)
        }

        private fun readGuildId(env: Map<String, String>): Long {
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
            return guildId
        }
    }
}

/**
 * Thrown when [JdaRuntimeConfig.fromEnv] cannot build a valid configuration.
 *
 * Messages never contain the configured bot token or any provider secret. A cause is only chained
 * for the explicit `Config.validate()` exceptions, whose messages are fixed literals; a
 * decoder-originated failure is never chained because its message can carry raw JSON text.
 */
internal class JdaRuntimeConfigException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
