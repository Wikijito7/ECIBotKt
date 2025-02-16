package es.wokis.services.lang

import dev.kord.common.Locale
import java.nio.file.Paths

private val LANG_PATH = {}::class.java.getResource("/lang")

class LanguageLocalizationService {

    private val localizedStrings: Map<String, List<StringLocalized>> = loadLanguages()

    // Localization should follow Discord locale definition or else it will crash
    // https://discord.com/developers/docs/reference#locales
    private fun loadLanguages(): Map<String, List<StringLocalized>> {
        if (LANG_PATH == null) throw LocalizationFolderNotFoundException()
        val langFiles = Paths.get(LANG_PATH.toURI()).toFile().listFiles()?.toList() ?: throw NoLocalizationFileFoundException()
        val localizationMap: MutableMap<String, MutableList<StringLocalized>> = mutableMapOf()
        langFiles.forEach { file ->
            val locale = file.nameWithoutExtension.split("_").getOrNull(1)?.let { Locale.fromString(it) } ?: Locale.ENGLISH_GREAT_BRITAIN
            file.readLines().map { line ->
                val lineSplit = line.split(": ")
                localizationMap[lineSplit[0]] = (localizationMap[lineSplit[0]] ?: mutableListOf()).apply {
                    add(StringLocalized(locale, lineSplit[1]))
                }
            }
        }
        return localizationMap.map { it.key to it.value.toList() }.toMap()
    }

    fun getLocalization(key: String): MutableMap<Locale, String> = localizedStrings[key]?.associate { it.locale to it.value }?.toMutableMap()
        ?: throw NoLocalizationFoundException(key)

    fun getDefaultString(key: String): String = getString(key, Locale.ENGLISH_GREAT_BRITAIN)
        ?: throw NoLocalizationFoundException(key)

    fun getStringForLocale(key: String, locale: Locale): String = getString(key, locale)
        ?: getDefaultString(key)

    private fun getString(key: String, locale: Locale): String? =
        localizedStrings[key]?.find { it.locale == locale }?.value
}

data class StringLocalized(
    val locale: Locale,
    val value: String
)

class LocalizationFolderNotFoundException : Exception("There is an error finding the localization folder")

class NoLocalizationFileFoundException : Exception("There are no localization files inside localization folder")

class NoLocalizationFoundException(key: String) : Exception("There are no localization for key $key")
