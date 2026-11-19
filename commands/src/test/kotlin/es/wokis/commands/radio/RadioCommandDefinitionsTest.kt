package es.wokis.commands.radio

import es.wokis.commands.neutral.SubcommandDefinition
import es.wokis.commands.neutral.SubcommandGroupDefinition
import es.wokis.domain.locale.LocaleCode
import es.wokis.localization.RadioMessageKeys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RadioCommandDefinitionsTest {

    private val localization = RadioLocalizationFixture()
    private val supportedLocales = setOf(LocaleCode("en-US"), LocaleCode("es-ES"))

    @Test
    fun `Given the radio definition When built Then publish Kord's public group command with no top-level option`() {
        val definition = RadioCommandDefinitions.radio(localization.service)

        assertEquals("radio", definition.name)
        assertEquals(localization.english(RadioMessageKeys.RADIO_COMMAND_DESCRIPTION), definition.description)
        assertFalse(definition.adminOnly, "Kord registers /radio as a public command")
        assertNull(definition.stringOption, "a group command must not publish a top-level option")
        assertEquals(supportedLocales, definition.descriptionLocalizations.keys)
        assertEquals(
            mapOf(
                LocaleCode("en-US") to localization.english(RadioMessageKeys.RADIO_COMMAND_DESCRIPTION),
                LocaleCode("es-ES") to localization.spanish(RadioMessageKeys.RADIO_COMMAND_DESCRIPTION),
            ),
            definition.descriptionLocalizations,
        )
    }

    @Test
    fun `Given the radio definition When built Then expose play list countrycodes and random in Kord's order`() {
        val subcommands = RadioCommandDefinitions.radio(localization.service).subcommands

        assertEquals(listOf("play", "list", "countrycodes", "random"), subcommands.map(SubcommandDefinition::name))
    }

    @Test
    fun `Given the play leaf When built Then require and autocomplete Kord's radio option`() {
        val play = leaf("play")

        assertEquals(localization.english(RadioMessageKeys.RADIO_PLAY_COMMAND_DESCRIPTION), play.description)
        assertEquals(supportedLocales, play.descriptionLocalizations.keys)
        val option = requireNotNull(play.stringOption)
        assertEquals("radio", option.name)
        assertEquals(localization.english(RadioMessageKeys.RADIO_PLAY_INPUT_DESCRIPTION), option.description)
        assertTrue(option.required)
        assertTrue(option.autocomplete)
        assertEquals(supportedLocales, option.descriptionLocalizations.keys)
    }

    @Test
    fun `Given the optionless leaves When built Then publish no option and Kord's localized description`() {
        listOf(
            "list" to RadioMessageKeys.RADIO_LIST_COMMAND_DESCRIPTION,
            "countrycodes" to RadioMessageKeys.RADIO_COUNTRYCODES_COMMAND_DESCRIPTION,
            "random" to RadioMessageKeys.RADIO_RANDOM_COMMAND_DESCRIPTION,
        ).forEach { (name, descriptionKey) ->
            val leaf = leaf(name)
            assertNull(leaf.stringOption, "/radio $name takes no option")
            assertEquals(localization.english(descriptionKey), leaf.description)
            assertEquals(supportedLocales, leaf.descriptionLocalizations.keys)
        }
    }

    @Test
    fun `Given the search group When built Then contain name and countrycode in Kord's order`() {
        val search = group()

        assertEquals("search", search.name)
        assertEquals(localization.english(RadioMessageKeys.RADIO_SEARCH_COMMAND_DESCRIPTION), search.description)
        assertEquals(supportedLocales, search.descriptionLocalizations.keys)
        assertEquals(listOf("name", "countrycode"), search.subcommands.map(SubcommandDefinition::name))
    }

    @Test
    fun `Given the search name leaf When built Then require a non autocomplete name option`() {
        val name = requireNotNull(group().subcommands.firstOrNull { it.name == "name" })

        assertEquals(localization.english(RadioMessageKeys.RADIO_SEARCH_NAME_COMMAND_DESCRIPTION), name.description)
        val option = requireNotNull(name.stringOption)
        assertEquals("name", option.name)
        assertEquals(localization.english(RadioMessageKeys.RADIO_SEARCH_NAME_INPUT_DESCRIPTION), option.description)
        assertTrue(option.required)
        assertFalse(option.autocomplete, "Kord autocompletes only the country-code search")
    }

    @Test
    fun `Given the search countrycode leaf When built Then require and autocomplete the countrycode option`() {
        val countryCode = requireNotNull(group().subcommands.firstOrNull { it.name == "countrycode" })

        assertEquals(
            localization.english(RadioMessageKeys.RADIO_SEARCH_COUNTRYCODE_COMMAND_DESCRIPTION),
            countryCode.description,
        )
        val option = requireNotNull(countryCode.stringOption)
        assertEquals("countrycode", option.name)
        assertEquals(
            localization.english(RadioMessageKeys.RADIO_SEARCH_COUNTRYCODE_INPUT_DESCRIPTION),
            option.description,
        )
        assertTrue(option.required)
        assertTrue(option.autocomplete)
    }

    @Test
    fun `Given every name constant When compared Then it matches Kord's registration`() {
        assertEquals("radio", RadioCommandDefinitions.RADIO_COMMAND_NAME)
        assertEquals("play", RadioCommandDefinitions.PLAY_COMMAND_NAME)
        assertEquals("list", RadioCommandDefinitions.LIST_COMMAND_NAME)
        assertEquals("search", RadioCommandDefinitions.SEARCH_COMMAND_NAME)
        assertEquals("name", RadioCommandDefinitions.SEARCH_NAME_COMMAND_NAME)
        assertEquals("countrycode", RadioCommandDefinitions.SEARCH_COUNTRY_CODE_COMMAND_NAME)
        assertEquals("random", RadioCommandDefinitions.RANDOM_COMMAND_NAME)
        assertEquals("countrycodes", RadioCommandDefinitions.COUNTRY_CODES_COMMAND_NAME)
        assertEquals("radio", RadioCommandDefinitions.PLAY_OPTION_NAME)
        assertEquals("name", RadioCommandDefinitions.SEARCH_NAME_OPTION_NAME)
        assertEquals("countrycode", RadioCommandDefinitions.SEARCH_COUNTRY_CODE_OPTION_NAME)
    }

    private fun leaf(name: String): SubcommandDefinition {
        val definition = RadioCommandDefinitions.radio(localization.service)
        return requireNotNull(definition.subcommands.firstOrNull { it.name == name }) { "missing /radio $name" }
    }

    private fun group(): SubcommandGroupDefinition {
        val definition = RadioCommandDefinitions.radio(localization.service)
        return requireNotNull(definition.subcommandGroups.singleOrNull()) { "missing the search group" }
    }
}
