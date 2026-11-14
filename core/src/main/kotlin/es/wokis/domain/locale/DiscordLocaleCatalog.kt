package es.wokis.domain.locale

/**
 * Canonical Discord locale tags supported by the migration.
 *
 * The values are the exact tags Discord documents and the tags exposed by JDA 6.7.0
 * (`DiscordLocale.locale`), excluding the synthetic `unknown` marker. They are the only values
 * that may be persisted as a guild locale: enum names, localized names, aliases and custom tags
 * are never stored.
 *
 * The catalog is deliberately owned by the domain so neither Kord nor JDA has to leak into the
 * persistence, use-case or localization layers. Adapter enums must map through their canonical
 * tag and are validated against this list; a future JDA locale that is not listed here stays
 * unrecognised instead of silently becoming a stored custom tag.
 */
object DiscordLocaleCatalog {

    /**
     * Default locale used when no guild locale is persisted and the guild has no usable default.
     */
    val defaultLocale: LocaleCode = LocaleCode("en-US")

    /**
     * Input that removes the guild locale override. It is matched case-insensitively by commands.
     */
    const val RESET_INPUT: String = "reset"

    /**
     * All canonical Discord tags, including the real JDA 6.7.0 `ar` and `he` tags.
     */
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
     * Returns the canonical [LocaleCode] for an exact-case Discord tag, or `null` when the tag is
     * not part of the catalog.
     *
     * Matching is intentionally case-sensitive: persisted values and command input must use the
     * canonical Discord spelling.
     */
    fun find(tag: String): LocaleCode? = byTag[tag]

    /**
     * Returns `true` only when [locale] is one of the canonical catalog tags.
     */
    fun isSupported(locale: LocaleCode?): Boolean = locale != null && byTag.containsKey(locale.value)

    /**
     * Returns `true` when [input] is the reset marker, compared case-insensitively.
     */
    fun isReset(input: String?): Boolean = input?.equals(RESET_INPUT, ignoreCase = true) == true

    /**
     * Autocomplete suggestions for [input]. Canonical tags are matched case-insensitively and the
     * returned values are always the canonical spelling.
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

/**
 * Public source-compatible alias for the canonical reset marker owned by [DiscordLocaleCatalog].
 *
 * Existing callers can keep importing this top-level name; the catalog's [DiscordLocaleCatalog.RESET_INPUT]
 * stays the single value used by the reset matching logic.
 */
const val RESET_LOCALE_VALUE: String = DiscordLocaleCatalog.RESET_INPUT
