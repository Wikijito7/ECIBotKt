package es.wokis.commands.locale

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.CommandResponse
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.locale.LocaleRepository
import es.wokis.domain.locale.SetGuildLocaleUseCase
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.LocaleMessageKeys
import es.wokis.localization.LocalizationCatalog
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocaleCommandHandlerTest {

    private val guildId = GuildId(123uL)

    @Test
    fun `Given a canonical locale When handled Then persist it before confirming`() = runTest {
        // Given
        val order = mutableListOf<String>()
        val repository = RecordingLocaleRepository(order)
        val responder = RecordingResponder(order)
        val handler = handler(repository)

        // When
        handler.handle(context(input = "es-ES"), responder)

        // Then the persistence happened before the confirmation was sent
        assertEquals(listOf("set:es-ES", "respond"), order)
        assertEquals(LocaleCode("es-ES"), repository.locales[guildId])
        assertEquals(
            listOf(CommandResponse("El idioma del servidor se ha establecido a es-ES")),
            responder.responses,
        )
    }

    @Test
    fun `Given Arabic When handled Then persist it and answer with the English fallback`() = runTest {
        // Given
        val order = mutableListOf<String>()
        val repository = RecordingLocaleRepository(order)
        val responder = RecordingResponder(order)
        val handler = handler(repository)

        // When
        handler.handle(context(input = "ar"), responder)

        // Then Arabic is selectable even though it has no translation yet
        assertEquals(listOf("set:ar", "respond"), order)
        assertEquals(LocaleCode("ar"), repository.locales[guildId])
        assertEquals(listOf(CommandResponse("Guild locale has been set to ar")), responder.responses)
    }

    @Test
    fun `Given a locale with non-canonical case When handled Then reject it without persisting`() = runTest {
        // Given
        val order = mutableListOf<String>()
        val repository = RecordingLocaleRepository(order)
        val responder = RecordingResponder(order)
        val handler = handler(repository)

        // When
        handler.handle(context(input = "es-es", guildDefaultLocale = LocaleCode("es-ES")), responder)

        // Then
        assertEquals(listOf("respond"), order)
        assertTrue(repository.locales.isEmpty())
        assertEquals(
            listOf(
                CommandResponse(
                    "Idioma proporcionado no válido. Por favor, usa las opciones de autocompletado.",
                ),
            ),
            responder.responses,
        )
        assertTrue(responder.responses.single().ephemeral.not())
    }

    @Test
    fun `Given an unknown locale When handled Then reject it without persisting`() = runTest {
        // Given
        val order = mutableListOf<String>()
        val repository = RecordingLocaleRepository(order)
        val responder = RecordingResponder(order)
        val handler = handler(repository)

        // When
        handler.handle(context(input = "not-a-locale"), responder)

        // Then
        assertEquals(listOf("respond"), order)
        assertTrue(repository.locales.isEmpty())
        assertEquals(
            listOf(CommandResponse("Invalid locale provided. Please use the autocomplete options.")),
            responder.responses,
        )
    }

    @Test
    fun `Given reset in any case When handled Then remove the override before confirming`() = runTest {
        // Given
        listOf("reset", "RESET", "ReSeT").forEach { input ->
            val order = mutableListOf<String>()
            val repository = RecordingLocaleRepository(order, mapOf(guildId to LocaleCode("ar")))
            val responder = RecordingResponder(order)
            val handler = handler(repository)

            // When
            handler.handle(context(input = input), responder)

            // Then
            assertEquals(listOf("remove", "respond"), order, input)
            assertTrue(repository.locales.isEmpty(), input)
            assertEquals(
                listOf(CommandResponse("Guild locale has been reset to Discord's default")),
                responder.responses,
                input,
            )
        }
    }

    @Test
    fun `Given reset with a guild default When handled Then answer in that guild locale`() = runTest {
        // Given
        val order = mutableListOf<String>()
        val repository = RecordingLocaleRepository(order)
        val responder = RecordingResponder(order)
        val handler = handler(repository)

        // When
        handler.handle(context(input = "reset", guildDefaultLocale = LocaleCode("es-ES")), responder)

        // Then
        assertEquals(
            listOf(
                CommandResponse("El idioma del servidor se ha restablecido al valor predeterminado de Discord"),
            ),
            responder.responses,
        )
    }

    @Test
    fun `Given no guild When handled Then answer a public error and never touch persistence`() = runTest {
        // Given
        val order = mutableListOf<String>()
        val repository = RecordingLocaleRepository(order)
        val responder = RecordingResponder(order)
        val handler = handler(repository)

        // When
        handler.handle(context(input = "es-ES", guildId = null), responder)

        // Then the legacy command answers publicly, without an ephemeral flag
        assertEquals(listOf("respond"), order)
        assertTrue(repository.locales.isEmpty())
        assertEquals(
            listOf(CommandResponse("You need to be in a guild", ephemeral = false)),
            responder.responses,
        )
        assertFalse(responder.responses.single().ephemeral)
    }

    @Test
    fun `Given no option value When handled Then answer a public required-field error in the guild locale`() = runTest {
        // Given
        listOf(null, "").forEach { input ->
            val order = mutableListOf<String>()
            val repository = RecordingLocaleRepository(order)
            val responder = RecordingResponder(order)
            val handler = handler(repository)

            // When
            handler.handle(
                context(input = input, guildDefaultLocale = LocaleCode("es-ES")),
                responder,
            )

            // Then
            assertEquals(listOf("respond"), order, "input=$input")
            assertTrue(repository.locales.isEmpty())
            assertEquals(
                listOf(
                    CommandResponse(
                        "El campo locale es requerido. Es necesario que rellenes ese campo",
                        ephemeral = false,
                    ),
                ),
                responder.responses,
                "input=$input",
            )
            assertFalse(responder.responses.single().ephemeral, "input=$input")
        }
    }

    @Test
    fun `Given a guild context When handled Then never persist the user locale as the guild default`() = runTest {
        // Given
        val order = mutableListOf<String>()
        val repository = RecordingLocaleRepository(order)
        val responder = RecordingResponder(order)
        val handler = handler(repository)

        // When: the guild default is Spanish and the user locale is German
        handler.handle(
            context(
                input = "fr",
                guildDefaultLocale = LocaleCode("es-ES"),
                userLocale = LocaleCode("de"),
            ),
            responder,
        )

        // Then only the explicit option is persisted
        assertEquals(listOf("set:fr", "respond"), order)
        assertEquals(LocaleCode("fr"), repository.locales[guildId])
    }

    @Test
    fun `Given the guild-scoped definition When inspected Then carry the actual command and option localizations`() {
        // Given: the real catalog currently translates both messages to en-US and es-ES only
        val catalog = LocalizationCatalog.load()
        val descriptionKey = LocaleMessageKeys.LOCALE_COMMAND_DESCRIPTION
        val optionDescriptionKey = LocaleMessageKeys.LOCALE_COMMAND_INPUT_DESCRIPTION
        val descriptionLocalizations = catalog.getLocalizations(descriptionKey)
        val optionLocalizations = catalog.getLocalizations(optionDescriptionKey)

        // When
        val definition = LocaleCommandHandler.definition(
            description = catalog.getDefaultString(descriptionKey),
            optionDescription = catalog.getDefaultString(optionDescriptionKey),
            descriptionLocalizations = descriptionLocalizations,
            optionDescriptionLocalizations = optionLocalizations,
        )

        // Then the existing command shape is preserved
        assertEquals(LocaleCommandHandler.COMMAND_NAME, definition.name)
        assertEquals("locale", definition.name)
        assertEquals("Set the preferred locale for this guild", definition.description)
        assertTrue(definition.adminOnly)
        val option = requireNotNull(definition.stringOption)
        assertEquals(LocaleCommandHandler.OPTION_NAME, option.name)
        assertEquals("locale", option.name)
        assertEquals(
            "Locale to set (use autocomplete to see available options)",
            option.description,
        )
        assertTrue(option.required)
        assertTrue(option.autocomplete)

        // And the definition carries the actual en-US/es-ES command metadata
        assertEquals(
            setOf(LocaleCode("en-US"), LocaleCode("es-ES")),
            definition.descriptionLocalizations.keys,
        )
        assertEquals(
            "Set the preferred locale for this guild",
            definition.descriptionLocalizations[LocaleCode("en-US")],
        )
        assertEquals(
            "Establecer el idioma preferido para este servidor",
            definition.descriptionLocalizations[LocaleCode("es-ES")],
        )
        assertFalse(definition.descriptionLocalizations.containsKey(LocaleCode("ar")))
        assertFalse(definition.descriptionLocalizations.containsKey(LocaleCode("he")))

        // And the same applies to the locale option metadata
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

    private fun handler(repository: LocaleRepository): LocaleCommandHandler = LocaleCommandHandler(
        setGuildLocaleUseCase = SetGuildLocaleUseCase(repository),
        localizationService = GuildLocalizationService(
            getGuildLocaleUseCase = GetGuildLocaleUseCase(repository),
            catalog = LocalizationCatalog.load(),
        ),
    )

    private fun context(
        input: String?,
        guildId: GuildId? = this.guildId,
        guildDefaultLocale: LocaleCode? = null,
        userLocale: LocaleCode? = null,
    ): CommandContext = CommandContext(
        commandName = LocaleCommandHandler.COMMAND_NAME,
        guildId = guildId,
        channelId = ChannelId(456uL),
        userId = UserId(789uL),
        locale = guildDefaultLocale ?: userLocale,
        guildLocale = guildDefaultLocale,
        userLocale = userLocale,
        stringOptions = input?.let { mapOf(LocaleCommandHandler.OPTION_NAME to it) } ?: emptyMap(),
    )

    private class RecordingResponder(
        private val order: MutableList<String>,
    ) : CommandResponder {
        val responses = mutableListOf<CommandResponse>()

        override suspend fun defer(ephemeral: Boolean) {
            order += "defer"
        }

        override suspend fun respond(response: CommandResponse) {
            order += "respond"
            responses += response
        }
    }

    private class RecordingLocaleRepository(
        private val order: MutableList<String>,
        initial: Map<GuildId, LocaleCode> = emptyMap(),
    ) : LocaleRepository {
        val locales: MutableMap<GuildId, LocaleCode> = initial.toMutableMap()

        override suspend fun getGuildLocale(guildId: GuildId): LocaleCode? = locales[guildId]

        override suspend fun setGuildLocale(guildId: GuildId, locale: LocaleCode) {
            locales[guildId] = locale
            order += "set:${locale.value}"
        }

        override suspend fun removeGuildLocale(guildId: GuildId) {
            locales.remove(guildId)
            order += "remove"
        }

        override suspend fun getAllGuildLocales(): Map<GuildId, LocaleCode> = locales.toMap()
    }
}
