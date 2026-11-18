package es.wokis.domain.locale

/**
 * Canonical Discord locale tags supported by the migration.
 *
 * Values are the exact tags Discord documents and JDA 6.7.0 exposes (`DiscordLocale.locale`),
 * excluding the synthetic `unknown`. Only these may be persisted: enum names, localized names,
 * aliases and custom tags never are. The catalog is domain-owned so no gateway type leaks into the
 * persistence, use-case or localization layers; adapter enums map through their canonical tag and
 * an unknown future tag stays unrecognised instead of becoming a custom tag.
 */
object DiscordLocaleCatalog {

    /** Default when no guild locale is persisted and the guild default is unusable. */
    val defaultLocale: LocaleCode = LocaleCode("en-US")

    /** Input that removes the guild locale override, matched case-insensitively. */
    const val RESET_INPUT: String = "reset"

    /** All canonical tags, including the real JDA 6.7.0 `ar` and `he` tags. */
    val supportedLocales: List<LocaleCode> = listOf(
        LocaleCode("ar"),
        LocaleCode("bg"),
        LocaleCode("zh-CN"),
        LocaleCode("zh-TW"),
        LocaleCode("hr"),
        LocaleCode("cs"),
        LocaleCode("da"),
        LocaleCode("nl"),
        LocaleCode("en-GB"),
        LocaleCode("en-US"),
        LocaleCode("fi"),
        LocaleCode("fr"),
        LocaleCode("de"),
        LocaleCode("el"),
        LocaleCode("he"),
        LocaleCode("hi"),
        LocaleCode("hu"),
        LocaleCode("id"),
        LocaleCode("it"),
        LocaleCode("ja"),
        LocaleCode("ko"),
        LocaleCode("lt"),
        LocaleCode("no"),
        LocaleCode("pl"),
        LocaleCode("pt-BR"),
        LocaleCode("ro"),
        LocaleCode("ru"),
        LocaleCode("es-ES"),
        LocaleCode("es-419"),
        LocaleCode("sv-SE"),
        LocaleCode("th"),
        LocaleCode("tr"),
        LocaleCode("uk"),
        LocaleCode("vi"),
    )

    private val byTag: Map<String, LocaleCode> = supportedLocales.associateBy(LocaleCode::value)

    /**
     * Returns the canonical [LocaleCode] for an exact-case Discord tag, or `null`.
     *
     * Matching is deliberately case-sensitive: persisted values and command input use the canonical
     * Discord spelling.
     */
    fun find(tag: String): LocaleCode? = byTag[tag]

    /** True only when [locale] is one of the canonical tags. */
    fun isSupported(locale: LocaleCode?): Boolean = locale != null && byTag.containsKey(locale.value)

    /** True when [input] is the reset marker, compared case-insensitively. */
    fun isReset(input: String?): Boolean = input?.equals(RESET_INPUT, ignoreCase = true) == true

    /**
     * Autocomplete suggestions for [input]; matching is case-insensitive and results always use
     * the canonical spelling.
     */
    fun suggestions(input: String?, limit: Int = 25): List<LocaleCode> {
        val query = input.orEmpty().lowercase()
        val matches = if (query.isEmpty()) {
            supportedLocales
        } else {
            supportedLocales.filter { it.value.lowercase().contains(query) }
        }
        return matches.take(limit)
    }
}

/** Source-compatible alias for [DiscordLocaleCatalog.RESET_INPUT]. */
const val RESET_LOCALE_VALUE: String = DiscordLocaleCatalog.RESET_INPUT
