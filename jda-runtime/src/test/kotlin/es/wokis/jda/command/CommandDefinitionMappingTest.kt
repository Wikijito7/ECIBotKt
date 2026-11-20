package es.wokis.jda.command

import es.wokis.commands.locale.LocaleCommandHandler
import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.neutral.StringOptionDefinition
import es.wokis.commands.neutral.SubcommandDefinition
import es.wokis.commands.neutral.SubcommandGroupDefinition
import es.wokis.commands.player.PlayerCommandDefinitions
import es.wokis.commands.radio.RadioCommandDefinitions
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.locale.LocaleRepository
import es.wokis.domain.model.GuildId
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.LocalizationCatalog
import es.wokis.localization.PlayerMessageKeys
import es.wokis.localization.RadioMessageKeys
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Neutral-to-JDA mapping contract for the moved `toJdaCommandData` helpers: names, descriptions,
 * localizations, options and the radio subcommand family must reach JDA intact.
 */
class CommandDefinitionMappingTest {

    private val localization = GuildLocalizationService(
        getGuildLocaleUseCase = GetGuildLocaleUseCase(EmptyLocaleRepository()),
        catalog = LocalizationCatalog.load(),
    )

    @Test
    fun `Given the locale definition When mapped Then publish an admin command with a required autocomplete option`() {
        val command = LocaleCommandHandler.definition(
            description = "Set the preferred locale for this guild",
            optionDescription = "Locale to set (use autocomplete to see available options)",
            descriptionLocalizations = mapOf(
                LocaleCode("en-US") to "Set the preferred locale for this guild",
                LocaleCode("es-ES") to "Establecer el idioma preferido para este servidor",
                LocaleCode("xx-YY") to "not a Discord locale",
                LocaleCode("unknown") to "not a Discord locale",
            ),
            optionDescriptionLocalizations = mapOf(
                LocaleCode("en-US") to "Locale to set (use autocomplete to see available options)",
                LocaleCode("es-ES") to
                    "Idioma a establecer (usa el autocompletado para ver las opciones disponibles)",
                LocaleCode("xx-YY") to "not a Discord locale",
                LocaleCode("unknown") to "not a Discord locale",
            ),
        )

        val data = command.toJdaCommandData() as SlashCommandData

        assertEquals("locale", data.name)
        assertEquals("Set the preferred locale for this guild", data.description)
        assertEquals(
            DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR),
            data.defaultPermissions,
        )

        val option = data.options.single()
        assertEquals(OptionType.STRING, option.type)
        assertEquals("locale", option.name)
        assertEquals("Locale to set (use autocomplete to see available options)", option.description)
        assertTrue(option.isRequired)
        assertTrue(option.isAutoComplete)

        // Canonical neutral tags become JDA locales at the edge, and unknown tags are dropped
        assertEquals(
            mapOf(
                DiscordLocale.ENGLISH_US to "Set the preferred locale for this guild",
                DiscordLocale.SPANISH to "Establecer el idioma preferido para este servidor",
            ),
            data.descriptionLocalizations.toMap(),
        )
        assertEquals(
            mapOf(
                DiscordLocale.ENGLISH_US to "Locale to set (use autocomplete to see available options)",
                DiscordLocale.SPANISH to
                    "Idioma a establecer (usa el autocompletado para ver las opciones disponibles)",
            ),
            option.descriptionLocalizations.toMap(),
        )
    }

    @Test
    fun `Given a public player definition When mapped Then publish an enabled command with the optional action option`() {
        val data = PlayerCommandDefinitions.player(localization).toJdaCommandData() as SlashCommandData

        assertEquals("player", data.name)
        assertEquals(PlayerCommandDefinitions.PLAYER_COMMAND_NAME, data.name)
        assertEquals(
            localization.getDefaultString(PlayerMessageKeys.PLAYER_COMMAND_DESCRIPTION),
            data.description,
        )
        assertEquals(DefaultMemberPermissions.ENABLED, data.defaultPermissions)

        val option = data.options.single()
        assertEquals(OptionType.STRING, option.type)
        assertEquals(PlayerCommandDefinitions.PLAYER_OPTION_ACTION, option.name)
        assertEquals(
            localization.getDefaultString(PlayerMessageKeys.PLAYER_COMMAND_ACTION_DESCRIPTION),
            option.description,
        )
        assertFalse(
            option.isRequired,
            "Kord parity: an omitted /player action shows the player view, so the option must stay optional",
        )
        assertFalse(option.isAutoComplete)
    }

    @Test
    fun `Given a public play definition When mapped Then publish an enabled command with the required input option`() {
        val data = PlayerCommandDefinitions.play(localization).toJdaCommandData() as SlashCommandData

        assertEquals("play", data.name)
        assertEquals(PlayerCommandDefinitions.PLAY_COMMAND_NAME, data.name)
        assertEquals(
            localization.getDefaultString(PlayerMessageKeys.PLAY_COMMAND_DESCRIPTION),
            data.description,
        )
        assertEquals(DefaultMemberPermissions.ENABLED, data.defaultPermissions)

        val option = data.options.single()
        assertEquals(OptionType.STRING, option.type)
        assertEquals(PlayerCommandDefinitions.PLAY_OPTION_INPUT, option.name)
        assertEquals(
            localization.getDefaultString(PlayerMessageKeys.PLAY_COMMAND_INPUT_DESCRIPTION),
            option.description,
        )
        assertTrue(option.isRequired)
        assertFalse(option.isAutoComplete)
    }

    @Test
    fun `Given the radio group definition When mapped Then keep Kord's subcommands groups and localizations`() {
        val data = RadioCommandDefinitions.radio(localization).toJdaCommandData() as SlashCommandData

        assertEquals("radio", data.name)
        assertEquals(DefaultMemberPermissions.ENABLED, data.defaultPermissions)
        assertEquals(
            localization.getDefaultString(RadioMessageKeys.RADIO_COMMAND_DESCRIPTION),
            data.description,
        )
        assertEquals(SUPPORTED_LOCALES, data.descriptionLocalizations.toMap().keys)
        assertTrue(data.options.isEmpty(), "a group command must not publish a top-level option")
        assertEquals(listOf("play", "list", "countrycodes", "random"), data.subcommands.map { it.name })

        val expectedLeaves = listOf(
            "play" to RadioMessageKeys.RADIO_PLAY_COMMAND_DESCRIPTION,
            "list" to RadioMessageKeys.RADIO_LIST_COMMAND_DESCRIPTION,
            "countrycodes" to RadioMessageKeys.RADIO_COUNTRYCODES_COMMAND_DESCRIPTION,
            "random" to RadioMessageKeys.RADIO_RANDOM_COMMAND_DESCRIPTION,
        )
        expectedLeaves.forEach { (name, descriptionKey) ->
            val subcommand = data.subcommands.first { it.name == name }
            assertEquals(localization.getDefaultString(descriptionKey), subcommand.description)
            assertEquals(SUPPORTED_LOCALES, subcommand.descriptionLocalizations.toMap().keys)
        }

        val play = data.subcommands.first { it.name == "play" }
        val playOption = play.options.single()
        assertEquals("radio", playOption.name)
        assertEquals(OptionType.STRING, playOption.type)
        assertEquals(
            localization.getDefaultString(RadioMessageKeys.RADIO_PLAY_INPUT_DESCRIPTION),
            playOption.description,
        )
        assertTrue(playOption.isRequired)
        assertTrue(playOption.isAutoComplete)
        assertEquals(SUPPORTED_LOCALES, playOption.descriptionLocalizations.toMap().keys)

        val search = data.subcommandGroups.single()
        assertEquals("search", search.name)
        assertEquals(
            localization.getDefaultString(RadioMessageKeys.RADIO_SEARCH_COMMAND_DESCRIPTION),
            search.description,
        )
        assertEquals(listOf("name", "countrycode"), search.subcommands.map { it.name })
        assertEquals(SUPPORTED_LOCALES, search.descriptionLocalizations.toMap().keys)

        val nameOption = search.subcommands.first { it.name == "name" }.options.single()
        assertEquals("name", nameOption.name)
        assertEquals(OptionType.STRING, nameOption.type)
        assertTrue(nameOption.isRequired)
        assertFalse(nameOption.isAutoComplete)

        val countryCodeOption = search.subcommands.first { it.name == "countrycode" }.options.single()
        assertEquals("countrycode", countryCodeOption.name)
        assertEquals(OptionType.STRING, countryCodeOption.type)
        assertTrue(countryCodeOption.isRequired)
        assertTrue(countryCodeOption.isAutoComplete)
    }

    @Test
    fun `Given only non-Discord locale tags When mapped Then publish no description localizations`() {
        val command = CommandDefinition(
            name = "custom",
            description = "A command whose translations have no gateway locale",
            descriptionLocalizations = mapOf(
                LocaleCode("xx-YY") to "not a Discord locale",
                LocaleCode("unknown") to "not a Discord locale",
            ),
        )

        val data = command.toJdaCommandData()

        assertTrue(
            data.nameLocalizations.toMap().isEmpty(),
            "name localizations were never mapped and must stay empty",
        )
        assertTrue(
            (data as SlashCommandData).descriptionLocalizations.toMap().isEmpty(),
            "unknown locale tags must not be fabricated into gateway locales",
        )
    }

    @Test
    fun `Given a grouped definition When mapped Then keep the group shape and only string options`() {
        val command = CommandDefinition(
            name = "grouped",
            description = "A grouped command",
            subcommands = listOf(
                SubcommandDefinition(
                    name = "leaf",
                    description = "A leaf subcommand",
                    stringOption = StringOptionDefinition(
                        name = "value",
                        description = "A value",
                        required = false,
                    ),
                ),
                SubcommandDefinition(name = "bare", description = "A subcommand without options"),
            ),
            subcommandGroups = listOf(
                SubcommandGroupDefinition(
                    name = "nested",
                    description = "A nested group",
                    subcommands = listOf(SubcommandDefinition(name = "inner", description = "An inner leaf")),
                ),
            ),
        )

        val data = command.toJdaCommandData() as SlashCommandData

        assertTrue(data.options.isEmpty())
        assertEquals(listOf("leaf", "bare"), data.subcommands.map { it.name })
        assertEquals(listOf("value"), data.subcommands.first { it.name == "leaf" }.options.map { it.name })
        assertTrue(data.subcommands.first { it.name == "bare" }.options.isEmpty())
        assertTrue(data.subcommands.first { it.name == "bare" }.descriptionLocalizations.toMap().isEmpty())
        assertEquals(listOf("nested"), data.subcommandGroups.map { it.name })
        assertTrue(data.subcommandGroups.single().subcommands.first().options.isEmpty())

        val allOptions = data.options +
            data.subcommands.flatMap { it.options } +
            data.subcommandGroups.flatMap { group -> group.subcommands.flatMap { it.options } }
        assertTrue(allOptions.all { it.type == OptionType.STRING }, "the narrow model only maps string options")
    }

    private class EmptyLocaleRepository : LocaleRepository {
        override suspend fun getGuildLocale(guildId: GuildId): LocaleCode? = null

        override suspend fun setGuildLocale(guildId: GuildId, locale: LocaleCode) =
            error("definition access must not persist")

        override suspend fun removeGuildLocale(guildId: GuildId) =
            error("definition access must not persist")

        override suspend fun getAllGuildLocales(): Map<GuildId, LocaleCode> = emptyMap()
    }

    private companion object {
        private val SUPPORTED_LOCALES = setOf(DiscordLocale.ENGLISH_US, DiscordLocale.SPANISH)
    }
}
