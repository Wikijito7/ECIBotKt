package es.wokis.localization

import es.wokis.domain.locale.DiscordLocaleCatalog
import es.wokis.domain.locale.LocaleCode
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.nameWithoutExtension

private const val LANG_PATH = "/lang"

/**
 * Neutral, gateway-free catalog of localized messages.
 *
 * The catalog keeps the legacy resource format unchanged: one `lang.yml` for the default
 * (`en-US`) strings and one `lang_<tag>.yml` file per translated locale. Values are resolved
 * per key, so a selectable locale without a translation file (for example `ar` or `he`) falls
 * back to the English string for that key instead of hiding the message.
 */
class LocalizationCatalog private constructor(
    private val entries: Map<String, Map<LocaleCode, String>>,
) {

    /**
     * Returns every translation of [key].
     *
     * @throws NoLocalizationFoundException when the key is missing from the catalog.
     */
    fun getLocalizations(key: String): Map<LocaleCode, String> =
        entries[key] ?: throw NoLocalizationFoundException(key)

    /**
     * Returns the translation of [key] for [locale], falling back to the default `en-US` value.
     *
     * @throws NoLocalizationFoundException when the key does not exist at all.
     */
    fun getString(key: String, locale: LocaleCode?): String {
        val localizations = getLocalizations(key)
        return locale?.let(localizations::get)
            ?: localizations[DiscordLocaleCatalog.defaultLocale]
            ?: throw NoLocalizationFoundException(key)
    }

    /**
     * Returns the default (`en-US`) value of [key].
     */
    fun getDefaultString(key: String): String = getString(key, DiscordLocaleCatalog.defaultLocale)

    companion object {

        /**
         * Loads the resources from the first `/lang` folder visible on the classpath.
         *
         * @throws LocalizationFolderNotFoundException when the folder cannot be found.
         */
        fun load(): LocalizationCatalog {
            val resource = LocalizationCatalog::class.java.getResource(LANG_PATH)
                ?: throw LocalizationFolderNotFoundException()
            return LocalizationCatalog(readAllLocalizations(resource.toURI()))
        }

        /**
         * Builds a catalog from already parsed entries. Intended for adapters and tests that need
         * an explicit resource seam.
         */
        fun of(entries: Map<String, Map<LocaleCode, String>>): LocalizationCatalog =
            LocalizationCatalog(entries.mapValues { (_, translations) -> translations.toMap() })

        private fun readAllLocalizations(uri: URI): Map<String, Map<LocaleCode, String>> {
            if (uri.scheme != "jar") {
                val paths = Files.list(Paths.get(uri))
                return try {
                    readPaths(paths)
                } finally {
                    paths.close()
                }
            }

            val fileSystem = FileSystems.newFileSystem(uri, emptyMap<String, Any>())
            return try {
                val paths = Files.list(fileSystem.getPath(LANG_PATH))
                try {
                    readPaths(paths)
                } finally {
                    paths.close()
                }
            } finally {
                fileSystem.close()
            }
        }

        private fun readPaths(paths: java.util.stream.Stream<Path>): Map<String, Map<LocaleCode, String>> {
            val localizations = mutableMapOf<String, MutableMap<LocaleCode, String>>()
            val iterator = paths.iterator()
            while (iterator.hasNext()) {
                val path = iterator.next()
                val suffix = path.nameWithoutExtension.split("_").getOrNull(1)
                val locale = if (suffix == null) {
                    DiscordLocaleCatalog.defaultLocale
                } else {
                    DiscordLocaleCatalog.find(suffix) ?: continue
                }
                Files.newBufferedReader(path).use { reader ->
                    reader.readLines().forEach { line ->
                        val lineSplit = line.replace("\\n", "\n").split(": ", limit = 2)
                        val key = lineSplit.first()
                        localizations[key] = (localizations[key] ?: mutableMapOf()).apply {
                            put(locale, lineSplit[1])
                        }
                    }
                }
            }
            return localizations.mapValues { (_, translations) -> translations.toMap() }
        }
    }
}

class LocalizationFolderNotFoundException : Exception("There is an error finding the localization folder")

class NoLocalizationFileFoundException : Exception("There are no localization files inside localization folder")

class NoLocalizationFoundException(key: String) : Exception("There are no localization for key $key")
