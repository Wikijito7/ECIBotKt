package es.wokis.domain.locale

/**
 * Platform-neutral Discord locale code, e.g. `es-ES`. Keeps the exact string so no gateway locale
 * enum leaks into the domain, repository or persistence layers.
 */
@JvmInline
value class LocaleCode(val value: String) {

    override fun toString(): String = value
}
