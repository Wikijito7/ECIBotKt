package es.wokis.services.config

/**
 * Root (Kord) adapter over the shared [ConfigLoader].
 *
 * The root application keeps its startup-only, immutable configuration snapshot: the file is read
 * once when Koin builds this singleton, and nothing here watches, mutates or reloads it. Reloadable
 * configuration is explicitly out of scope for this slice; a change requires a restart.
 *
 * The loader itself lives in the framework-independent `:configuration` module so the isolated JDA
 * runtime reads the exact same schema, defaults and validation rules.
 */
class ConfigService(
    loader: ConfigLoader = ConfigLoader(),
) {

    val config: Config = loader.load()
}
