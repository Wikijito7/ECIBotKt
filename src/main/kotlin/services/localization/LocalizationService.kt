package es.wokis.services.localization

import dev.kord.common.Locale
import java.nio.file.Paths

private val LANG_PATH = {}::class.java.getResource("/lang")

class LocalizationService {

    private val localizedStrings: Map<String, List<LocalizedString>> = loadLanguages()

    // Localization should follow Discord locale definition or else it will crash
    // https://discord.com/developers/docs/reference#locales
    private fun loadLanguages(): Map<String, List<LocalizedString>> {
        if (LANG_PATH == null) throw LocalizationFolderNotFoundException()
        val langFiles = Paths.get(LANG_PATH.toURI()).toFile().listFiles()?.toList() ?: throw NoLocalizationFileFoundException()
        val localizationMap: MutableMap<String, MutableList<LocalizedString>> = mutableMapOf()
        langFiles.forEach { file ->
            val locale = file.nameWithoutExtension.split("_").getOrNull(1)?.let { Locale.fromString(it) } ?: Locale.ENGLISH_UNITED_STATES
            file.readLines().map { line ->
                val lineSplit = line.replace("\\n", "\n").split(": ")
                localizationMap[lineSplit.first()] = (localizationMap[lineSplit.first()] ?: mutableListOf()).apply {
                    add(LocalizedString(locale, lineSplit[1]))
                }
            }
        }
        return localizationMap.map { it.key to it.value.toList() }.toMap()
    }

    fun getLocalizations(key: String): MutableMap<Locale, String> = localizedStrings[key]?.associate { it.locale to it.value }?.toMutableMap()
        ?: throw NoLocalizationFoundException(key)

    fun getString(key: String, locale: Locale = Locale.ENGLISH_UNITED_STATES): String =
        localizedStrings[key]?.find { it.locale == locale }?.value ?: getDefaultString(key)

    fun getStringFormat(key: String, locale: Locale = Locale.ENGLISH_UNITED_STATES, vararg arguments: Any) =
        getString(key, locale).format(*arguments)

    private fun getDefaultString(key: String): String =
        localizedStrings[key]?.find { it.locale == Locale.ENGLISH_UNITED_STATES }?.value
            ?: throw NoLocalizationFoundException(key)
}

data class LocalizedString(
    val locale: Locale,
    val value: String
)

class LocalizationFolderNotFoundException : Exception("There is an error finding the localization folder")

class NoLocalizationFileFoundException : Exception("There are no localization files inside localization folder")

class NoLocalizationFoundException(key: String) : Exception("There are no localization for key $key")
