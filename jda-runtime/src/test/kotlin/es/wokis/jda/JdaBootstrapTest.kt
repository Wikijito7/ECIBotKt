package es.wokis.jda

import es.wokis.commands.locale.LocaleCommandHandler
import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.neutral.GuildCommandRegistrar
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.locale.LocaleRepository
import es.wokis.domain.model.GuildId
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.LocaleMessageKeys
import es.wokis.localization.LocalizationCatalog
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JdaBootstrapTest {

    @Test
    fun `Given an explicit guild When locale is registered Then publish the admin guild command there`() = runTest {
        val registrar = RecordingGuildCommandRegistrar()
        val guildId = GuildId(987654321uL)
        val localizationService = GuildLocalizationService(
            getGuildLocaleUseCase = GetGuildLocaleUseCase(EmptyLocaleRepository()),
            catalog = LocalizationCatalog.load(),
        )

        registerLocaleCommand(registrar, guildId, localizationService)

        val registration = registrar.registrations.single()
        assertEquals(guildId, registration.guildId)
        val command = registration.command
        assertEquals(LocaleCommandHandler.COMMAND_NAME, command.name)
        assertEquals("locale", command.name)
        assertEquals(
            localizationService.getDefaultString(LocaleMessageKeys.LOCALE_COMMAND_DESCRIPTION),
            command.description,
        )
        assertTrue(command.adminOnly)
        assertEquals(
            setOf(LocaleCode("en-US"), LocaleCode("es-ES")),
            command.descriptionLocalizations.keys,
        )
        assertEquals(
            "Set the preferred locale for this guild",
            command.descriptionLocalizations[LocaleCode("en-US")],
        )
        assertEquals(
            "Establecer el idioma preferido para este servidor",
            command.descriptionLocalizations[LocaleCode("es-ES")],
        )
        assertFalse(command.descriptionLocalizations.containsKey(LocaleCode("ar")))
        assertFalse(command.descriptionLocalizations.containsKey(LocaleCode("he")))

        val option = requireNotNull(command.stringOption)
        assertEquals(LocaleCommandHandler.OPTION_NAME, option.name)
        assertEquals("locale", option.name)
        assertEquals(
            localizationService.getDefaultString(LocaleMessageKeys.LOCALE_COMMAND_INPUT_DESCRIPTION),
            option.description,
        )
        assertTrue(option.required)
        assertTrue(option.autocomplete)
        assertEquals(
            setOf(LocaleCode("en-US"), LocaleCode("es-ES")),
            option.descriptionLocalizations.keys,
        )
        assertEquals(
            "Locale to set (use autocomplete to see available options)",
            option.descriptionLocalizations[LocaleCode("en-US")],
        )
        assertEquals(
            "Idioma a establecer (usa el autocompletado para ver las opciones disponibles)",
            option.descriptionLocalizations[LocaleCode("es-ES")],
        )
        assertFalse(option.descriptionLocalizations.containsKey(LocaleCode("ar")))
        assertFalse(option.descriptionLocalizations.containsKey(LocaleCode("he")))
    }

    private data class Registration(
        val guildId: GuildId,
        val command: CommandDefinition,
    )

    private class RecordingGuildCommandRegistrar : GuildCommandRegistrar {
        val registrations = mutableListOf<Registration>()

        override suspend fun register(guildId: GuildId, command: CommandDefinition) {
            registrations += Registration(guildId = guildId, command = command)
        }
    }

    private class EmptyLocaleRepository : LocaleRepository {
        override suspend fun getGuildLocale(guildId: GuildId): LocaleCode? = null

        override suspend fun setGuildLocale(guildId: GuildId, locale: LocaleCode) =
            error("registerLocaleCommand must not persist")

        override suspend fun removeGuildLocale(guildId: GuildId) =
            error("registerLocaleCommand must not persist")

        override suspend fun getAllGuildLocales(): Map<GuildId, LocaleCode> = emptyMap()
    }
}
