package es.wokis.jda

import es.wokis.exceptions.EmptyDeezerMasterDecryptionKeyException
import es.wokis.exceptions.EmptyDiscordTokenException
import es.wokis.player.engine.config.PlayerSourcesConfig
import es.wokis.player.engine.config.toPlayerSourcesConfig
import es.wokis.services.config.Config
import es.wokis.services.config.ConfigLoader
import es.wokis.services.config.DEFAULT_CONFIG_PATH
import java.nio.file.Path

/**
 * Validated configuration for the isolated JDA runtime, loaded from the shared
 * `./data/config.json` with no env override. Neither [token] nor [sources] is ever rendered.
 */
internal data class JdaRuntimeConfig(
    val token: String,
    val sources: PlayerSourcesConfig = PlayerSourcesConfig(),
) {
    override fun toString(): String = "JdaRuntimeConfig(tokenSet=${token.isNotBlank()})"

    companion object {
        /** @throws JdaRuntimeConfigException when the token is blank. */
        fun fromConfig(config: Config): JdaRuntimeConfig {
            if (config.discordBotToken.isBlank()) {
                throw JdaRuntimeConfigException(
                    "The shared configuration has an empty or blank discord_bot_token",
                )
            }
            return JdaRuntimeConfig(
                token = config.discordBotToken,
                sources = config.toPlayerSourcesConfig(),
            )
        }
    }
}

/**
 * Loads the shared config from the root data directory shared with the Kord runtime.
 *
 * Both runtimes resolve `./data` from the root CWD, so they must not run concurrently against that
 * same mutable directory.
 */
internal fun loadSharedConfig(
    path: Path = DEFAULT_CONFIG_PATH,
    loader: ConfigLoader = ConfigLoader(),
): Config = try {
    loader.load(path)
} catch (error: EmptyDiscordTokenException) {
    throw JdaRuntimeConfigException(
        "The shared configuration at '$path' has an empty or blank discord_bot_token",
        error,
    )
} catch (error: EmptyDeezerMasterDecryptionKeyException) {
    throw JdaRuntimeConfigException(
        "The shared configuration at '$path' enables Deezer without a master decryption key",
        error,
    )
} catch (error: IllegalArgumentException) {
    // Do not chain: a decoder message can embed the raw JSON, and the cause would expose it again.
    throw JdaRuntimeConfigException(
        "The shared configuration at '$path' is invalid (${error.javaClass.simpleName})",
    )
}

/** Configuration failure whose message never contains the token or a provider secret. */
internal class JdaRuntimeConfigException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
