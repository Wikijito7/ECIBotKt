package es.wokis.domain.locale

/**
 * Platform-neutral Discord locale code owned by the domain, e.g. `es-ES`.
 *
 * Keeps the exact Discord locale string so neither Kord nor JDA locale enums
 * leak into the domain, repository or persistence layers.
 */
@JvmInline
value class LocaleCode(val value: String) {

    override fun toString(): String = value
}
