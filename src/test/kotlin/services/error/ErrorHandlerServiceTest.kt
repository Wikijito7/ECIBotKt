package services.error

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.behavior.interaction.suggest
import dev.kord.core.entity.interaction.AutoCompleteInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import es.wokis.data.response.ErrorType
import es.wokis.exceptions.BotException
import es.wokis.localization.LocalizationKeys
import es.wokis.services.config.ConfigService
import es.wokis.services.error.ErrorHandlerService
import es.wokis.services.localization.LocalizationService
import es.wokis.utils.Log
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.Logger

class ErrorHandlerServiceTest {

    private val configService: ConfigService = mockk()
    private val localizationService: LocalizationService = mockk()
    private val errorHandlerService = ErrorHandlerService(
        configService = configService,
        localizationService = localizationService
    )

    companion object {
        private val logger: Logger = mockk()

        @JvmStatic
        @BeforeAll
        fun setUp() {
            Log.setLogger(logger)
        }
    }

    @BeforeEach
    fun setup() {
        clearMocks(logger)
        every { configService.config } returns mockk {
            every { debug } returns false
        }
        mockkStatic(AutoCompleteInteraction::suggest)
        mockkStatic(DeferredPublicMessageInteractionResponseBehavior::respond)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(AutoCompleteInteraction::suggest)
        unmockkStatic(DeferredPublicMessageInteractionResponseBehavior::respond)
    }

    // ──────────────────────────────────────────────
    // handleAutocompleteError tests
    // ──────────────────────────────────────────────

    @Test
    fun `Given error When handleAutocompleteError is called Then logs exception and calls suggest with empty list`() =
        runTest {
            // Given
            val exception = RuntimeException("Autocomplete processing failed")
            val interaction = mockk<AutoCompleteInteraction> {
                every { data } returns mockk {
                    every { guildId.value } returns Snowflake(123)
                }
                every { user } returns mockk {
                    every { username } returns "TestUser"
                    every { id } returns Snowflake(456)
                }
            }
            coJustRun { interaction.suggest(emptyList()) }
            justRun { logger.error(any<String>()) }

            // When
            errorHandlerService.handleAutocompleteError(exception, interaction)

            // Then
            coVerify(exactly = 1) { interaction.suggest(emptyList()) }
            verify(exactly = 1) {
                logger.error(match { it.startsWith("Autocomplete Error: Unknown | User: TestUser (456)") })
            }
        }

    @Test
    fun `Given error When handleAutocompleteError is called with command name Then includes command name in log`() =
        runTest {
            // Given
            val exception = RuntimeException("Autocomplete failed")
            val interaction = mockk<AutoCompleteInteraction> {
                every { data } returns mockk {
                    every { guildId.value } returns Snowflake(123)
                }
                every { user } returns mockk {
                    every { username } returns "TestUser"
                    every { id } returns Snowflake(456)
                }
            }
            coJustRun { interaction.suggest(emptyList()) }
            justRun { logger.error(any<String>()) }

            // When
            errorHandlerService.handleAutocompleteError(exception, interaction, commandName = "sound")

            // Then
            verify(exactly = 1) {
                logger.error(match { it.startsWith("Autocomplete Error: sound | User: TestUser (456)") })
            }
        }

    @Test
    fun `Given error When suggest throws Then exception is swallowed`() =
        runTest {
            // Given
            val exception = RuntimeException("Original autocomplete error")
            val interaction = mockk<AutoCompleteInteraction> {
                every { data } returns mockk {
                    every { guildId.value } returns Snowflake(123)
                }
                every { user } returns mockk {
                    every { username } returns "TestUser"
                    every { id } returns Snowflake(456)
                }
            }
            coEvery { interaction.suggest(emptyList()) } throws RuntimeException("Interaction already acknowledged")
            justRun { logger.error(any<String>()) }

            // When — should NOT propagate the suggest exception
            errorHandlerService.handleAutocompleteError(exception, interaction)

            // Then
            coVerify(exactly = 1) { interaction.suggest(emptyList()) }
            verify(exactly = 1) {
                logger.error(match { it.startsWith("Autocomplete Error: Unknown | User: TestUser (456)") })
            }
        }

    @Test
    fun `Given error in DM When handleAutocompleteError is called Then log shows DM`() =
        runTest {
            // Given
            val exception = RuntimeException("DM error")
            val interaction = mockk<AutoCompleteInteraction> {
                every { data } returns mockk {
                    every { guildId.value } returns null
                }
                every { user } returns mockk {
                    every { username } returns "DMUser"
                    every { id } returns Snowflake(789)
                }
            }
            coJustRun { interaction.suggest(emptyList()) }
            justRun { logger.error(any<String>()) }

            // When
            errorHandlerService.handleAutocompleteError(exception, interaction)

            // Then
            verify(exactly = 1) {
                logger.error(match { it.contains("Guild: DM") })
            }
        }

    // ──────────────────────────────────────────────
    // handleCommandError tests
    // ──────────────────────────────────────────────

    @Test
    fun `Given generic exception When handleCommandError is called Then logs and responds with generic error`() =
        runTest {
            // Given
            val exception = RuntimeException("Command execution failed")
            val interaction = mockk<ChatInputCommandInteraction> {
                every { data } returns mockk {
                    every { guildId.value } returns Snowflake(123)
                }
                every { user } returns mockk {
                    every { username } returns "TestUser"
                    every { id } returns Snowflake(456)
                }
                every { guildLocale } returns null
            }
            val response = mockk<DeferredPublicMessageInteractionResponseBehavior>()

            coEvery { localizationService.getString(LocalizationKeys.ERROR_UNEXPECTED, 123, null) } returns
                "Something went wrong"
            coEvery { response.respond(any()) } returns mockk()
            justRun { logger.error(any<String>()) }

            // When
            errorHandlerService.handleCommandError(exception, interaction, response)

            // Then
            coVerify(exactly = 1) { response.respond(any()) }
            coVerify(exactly = 1) {
                localizationService.getString(LocalizationKeys.ERROR_UNEXPECTED, 123, null)
            }
            verify(exactly = 1) {
                logger.error(match { it.startsWith("Command Error: Unknown | User: TestUser (456)") })
            }
        }

    @Test
    fun `Given BotException UserException When handleCommandError is called Then responds with localized user error`() =
        runTest {
            // Given
            val exception = BotException.UserException.NoContentProvidedException()
            val interaction = mockk<ChatInputCommandInteraction> {
                every { data } returns mockk {
                    every { guildId.value } returns Snowflake(123)
                }
                every { user } returns mockk {
                    every { username } returns "TestUser"
                    every { id } returns Snowflake(456)
                }
                every { guildLocale } returns null
            }
            val response = mockk<DeferredPublicMessageInteractionResponseBehavior>()

            coEvery {
                localizationService.getStringFormat(any<String>(), any(), any(), *anyVararg())
            } returns "Please provide content"
            coEvery { response.respond(any()) } returns mockk()
            justRun { logger.error(any<String>()) }

            // When
            errorHandlerService.handleCommandError(exception, interaction, response)

            // Then
            coVerify(exactly = 1) { response.respond(any()) }
            coVerify(exactly = 1) {
                localizationService.getStringFormat(any<String>(), 123, null, *anyVararg())
            }
        }

    @Test
    fun `Given BotException APIException When handleCommandError is called Then responds with API error`() =
        runTest {
            // Given
            val exception = BotException.APIException.GenericAPIException(
                errorType = ErrorType.ServerError(500, "Service unavailable")
            )
            val interaction = mockk<ChatInputCommandInteraction> {
                every { data } returns mockk {
                    every { guildId.value } returns Snowflake(123)
                }
                every { user } returns mockk {
                    every { username } returns "TestUser"
                    every { id } returns Snowflake(456)
                }
                every { guildLocale } returns null
            }
            val response = mockk<DeferredPublicMessageInteractionResponseBehavior>()

            coEvery { localizationService.getString(LocalizationKeys.ERROR_API_UNEXPECTED, 123, null) } returns
                "API error occurred"
            coEvery { response.respond(any()) } returns mockk()
            justRun { logger.error(any<String>()) }

            // When
            errorHandlerService.handleCommandError(exception, interaction, response)

            // Then
            coVerify(exactly = 1) { response.respond(any()) }
            coVerify(exactly = 1) {
                localizationService.getString(LocalizationKeys.ERROR_API_UNEXPECTED, 123, null)
            }
        }

    @Test
    fun `Given BotException SystemException When handleCommandError is called Then responds with system error`() =
        runTest {
            // Given
            val exception = BotException.SystemException.CommandExecutionException("Unexpected state")
            val interaction = mockk<ChatInputCommandInteraction> {
                every { data } returns mockk {
                    every { guildId.value } returns Snowflake(123)
                }
                every { user } returns mockk {
                    every { username } returns "TestUser"
                    every { id } returns Snowflake(456)
                }
                every { guildLocale } returns null
            }
            val response = mockk<DeferredPublicMessageInteractionResponseBehavior>()

            coEvery { localizationService.getString(LocalizationKeys.ERROR_UNEXPECTED, 123, null) } returns
                "A system error occurred"
            coEvery { response.respond(any()) } returns mockk()
            justRun { logger.error(any<String>()) }

            // When
            errorHandlerService.handleCommandError(exception, interaction, response)

            // Then
            coVerify(exactly = 1) { response.respond(any()) }
            coVerify(exactly = 1) {
                localizationService.getString(LocalizationKeys.ERROR_UNEXPECTED, 123, null)
            }
        }

    @Test
    fun `Given BotException SystemException with debug mode When handleCommandError is called Then responds with debug error`() =
        runTest {
            // Given — enable debug mode
            every { configService.config } returns mockk {
                every { debug } returns true
            }
            val exception = BotException.SystemException.CommandExecutionException("Unexpected state")
            val interaction = mockk<ChatInputCommandInteraction> {
                every { data } returns mockk {
                    every { guildId.value } returns Snowflake(123)
                }
                every { user } returns mockk {
                    every { username } returns "TestUser"
                    every { id } returns Snowflake(456)
                }
                every { guildLocale } returns null
            }
            val response = mockk<DeferredPublicMessageInteractionResponseBehavior>()

            coEvery { localizationService.getStringFormat(any<String>(), any(), any(), *anyVararg()) } returns
                "Debug: Unexpected state"
            coEvery { response.respond(any()) } returns mockk()
            justRun { logger.error(any<String>()) }

            // When
            errorHandlerService.handleCommandError(exception, interaction, response)

            // Then
            coVerify(exactly = 1) { response.respond(any()) }
            coVerify(exactly = 1) {
                localizationService.getStringFormat(
                    LocalizationKeys.ERROR_UNEXPECTED_WITH_DEBUG,
                    123,
                    null,
                    *anyVararg()
                )
            }
        }

    @Test
    fun `Given generic exception with debug mode When handleCommandError is called Then responds with debug error`() =
        runTest {
            // Given — enable debug mode
            every { configService.config } returns mockk {
                every { debug } returns true
            }
            val exception = RuntimeException("Something broke")
            val interaction = mockk<ChatInputCommandInteraction> {
                every { data } returns mockk {
                    every { guildId.value } returns Snowflake(123)
                }
                every { user } returns mockk {
                    every { username } returns "TestUser"
                    every { id } returns Snowflake(456)
                }
                every { guildLocale } returns null
            }
            val response = mockk<DeferredPublicMessageInteractionResponseBehavior>()

            coEvery { localizationService.getStringFormat(any<String>(), any(), any(), *anyVararg()) } returns
                "Debug: Something broke"
            coEvery { response.respond(any()) } returns mockk()
            justRun { logger.error(any<String>()) }

            // When
            errorHandlerService.handleCommandError(exception, interaction, response)

            // Then
            coVerify(exactly = 1) { response.respond(any()) }
            coVerify(exactly = 1) {
                localizationService.getStringFormat(
                    LocalizationKeys.ERROR_UNEXPECTED_WITH_DEBUG,
                    123,
                    null,
                    *anyVararg()
                )
            }
        }

    @Test
    fun `Given error When respond throws Then exception is swallowed`() =
        runTest {
            // Given
            val exception = RuntimeException("Original command error")
            val interaction = mockk<ChatInputCommandInteraction> {
                every { data } returns mockk {
                    every { guildId.value } returns Snowflake(123)
                }
                every { user } returns mockk {
                    every { username } returns "TestUser"
                    every { id } returns Snowflake(456)
                }
                every { guildLocale } returns null
            }
            val response = mockk<DeferredPublicMessageInteractionResponseBehavior>()

            coEvery { localizationService.getString(any(), any(), any()) } returns "Error message"
            coEvery { response.respond(any()) } throws RuntimeException("Interaction already responded")
            justRun { logger.error(any<String>()) }

            // When — should NOT propagate the respond exception
            errorHandlerService.handleCommandError(exception, interaction, response)

            // Then
            coVerify(exactly = 1) { response.respond(any()) }
            verify(exactly = 1) {
                logger.error(match { it.startsWith("Command Error: Unknown | User: TestUser (456)") })
            }
        }

    @Test
    fun `Given command name When handleCommandError is called Then log includes command name`() =
        runTest {
            // Given
            val exception = RuntimeException("Error")
            val interaction = mockk<ChatInputCommandInteraction> {
                every { data } returns mockk {
                    every { guildId.value } returns Snowflake(123)
                }
                every { user } returns mockk {
                    every { username } returns "TestUser"
                    every { id } returns Snowflake(456)
                }
                every { guildLocale } returns null
            }
            val response = mockk<DeferredPublicMessageInteractionResponseBehavior>()

            coEvery { localizationService.getString(any(), any(), any()) } returns "Error"
            coEvery { response.respond(any()) } returns mockk()
            justRun { logger.error(any<String>()) }

            // When
            errorHandlerService.handleCommandError(exception, interaction, response, commandName = "play")

            // Then
            verify(exactly = 1) {
                logger.error(match { it.startsWith("Command Error: play | User: TestUser (456)") })
            }
        }
}
