package es.wokis.services.localization

import dev.kord.common.Locale
import es.wokis.utils.Log
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.nameWithoutExtension

private const val LANG_PATH = "/lang"
private val LANG_RESOURCE = {}::class.java.getResource(LANG_PATH)

class LocalizationService {

    private val localizedStrings: Map<String, List<LocalizedString>> = loadLanguages()

    fun getLocalizations(key: String): MutableMap<Locale, String> = localizedStrings[key]?.associate { it.locale to it.value }?.toMutableMap()
        ?: throw NoLocalizationFoundException(key)

    fun getString(key: String, locale: Locale = Locale.ENGLISH_UNITED_STATES): String =
        localizedStrings[key]?.find { it.locale == locale }?.value ?: getDefaultString(key)

    fun getStringFormat(key: String, locale: Locale = Locale.ENGLISH_UNITED_STATES, vararg arguments: Any) =
        getString(key, locale).format(*arguments)

    private fun getDefaultString(key: String): String =
        localizedStrings[key]?.find { it.locale == Locale.ENGLISH_UNITED_STATES }?.value
            ?: throw NoLocalizationFoundException(key)

    // Localization should follow Discord locale definition or else it will crash
    // https://discord.com/developers/docs/reference#locales
    private fun loadLanguages(): Map<String, List<LocalizedString>> {
        val localizationMap = getLocalizations()
        return localizationMap.map { it.key to it.value.toList() }.toMap()
    }

    private fun getLocalizations(): Map<String, MutableList<LocalizedString>> {
        if (LANG_RESOURCE == null) throw LocalizationFolderNotFoundException()
        val uri = LANG_RESOURCE.toURI()
        val paths = getPaths(uri)
        return readAllLocalizations(paths)
    }

    private fun getPaths(uri: URI): List<Path> = if (uri.scheme == "jar") {
        val fs = FileSystems.newFileSystem(uri, emptyMap<String, Any>())
        val folderPath = fs.getPath(LANG_PATH)
        Files.list(folderPath).toList()
    } else {
        Files.list(Paths.get(uri)).toList()
    }

    private fun readAllLocalizations(paths: List<Path>): Map<String, MutableList<LocalizedString>> {
        val localizations: MutableMap<String, MutableList<LocalizedString>> = mutableMapOf()
        paths.forEach { path ->
            val locale = path.nameWithoutExtension.split("_").getOrNull(1)?.let { Locale.fromString(it) }
                ?: Locale.ENGLISH_UNITED_STATES
            Files.newBufferedReader(path).use { it.readLines() }.forEach { line ->
                val lineSplit = line.replace("\\n", "\n").split(": ")
                localizations[lineSplit.first()] = (localizations[lineSplit.first()] ?: mutableListOf()).apply {
                    add(LocalizedString(locale, lineSplit[1]))
                }
            }
        }
        return localizations.toMap()
    }
}

data class LocalizedString(
    val locale: Locale,
    val value: String
)

class LocalizationFolderNotFoundException : Exception("There is an error finding the localization folder")

class NoLocalizationFileFoundException : Exception("There are no localization files inside localization folder")

class NoLocalizationFoundException(key: String) : Exception("There are no localization for key $key")
