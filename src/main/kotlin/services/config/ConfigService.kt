package es.wokis.services.config

/**
 * Root (Kord) adapter over the shared [ConfigLoader].
 *
 * The snapshot is immutable and read once when Koin builds this singleton, so a change requires a
 * restart. The loader lives in the framework-independent `:configuration` module so both runtimes
 * share the same schema, defaults and validation rules.
 */
class ConfigService(
    loader: ConfigLoader = ConfigLoader(),
) {

    val config: Config = loader.load()
}
