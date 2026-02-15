package commands.locale

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.supplier.EntitySupplyStrategy
import es.wokis.commands.locale.LocaleCommand
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.SetGuildLocaleUseCase
import es.wokis.exceptions.BotException
import es.wokis.services.localization.LocalizationService
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LocaleCommandTest {

    private val localizationService: LocalizationService = mockk()
    private val setGuildLocaleUseCase: SetGuildLocaleUseCase = mockk()

    private val localeCommand = LocaleCommand(
        localizationService = localizationService,
        setGuildLocaleUseCase = setGuildLocaleUseCase
    )

    @Test
    fun `Given valid locale When onExecute is called Then set guild locale`() = runTest {
        // Given
        val mockKord: Kord = mockk {
            every { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            every { defaultSupplier } returns mockk()
            every { rest } returns mockk {
                every { interaction } returns mockk(relaxed = true)
            }
        }
        val testGuildId = Snowflake(123)
        val interaction = mockk<ChatInputCommandInteraction> {
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { data } returns mockk {
                every { guildId.value } returns testGuildId
            }
            every { kord } returns mockKord
            every { command } returns mockk {
                every { strings } returns mapOf("locale" to "es-ES")
            }
        }
        val response = mockk<DeferredPublicMessageInteractionResponseBehavior> {
            every { kord } returns mockKord
            every { applicationId } returns Snowflake(456)
            every { token } returns "testToken"
        }

        coJustRun { setGuildLocaleUseCase(any(), any()) }
        coEvery { localizationService.getStringFormat(any(), any(), any(), any()) } returns "Locale set to es-ES"

        // When
        localeCommand.onExecute(interaction, response)

        // Then
        coVerify(exactly = 1) {
            setGuildLocaleUseCase(testGuildId, Locale.SPANISH_SPAIN)
        }
    }

    @Test
    fun `Given reset locale When onExecute is called Then remove guild locale`() = runTest {
        // Given
        val mockKord: Kord = mockk {
            every { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            every { defaultSupplier } returns mockk()
            every { rest } returns mockk {
                every { interaction } returns mockk(relaxed = true)
            }
        }
        val testGuildId = Snowflake(123)
        val interaction = mockk<ChatInputCommandInteraction> {
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { data } returns mockk {
                every { guildId.value } returns testGuildId
            }
            every { kord } returns mockKord
            every { command } returns mockk {
                every { strings } returns mapOf("locale" to "reset")
            }
        }
        val response = mockk<DeferredPublicMessageInteractionResponseBehavior> {
            every { kord } returns mockKord
            every { applicationId } returns Snowflake(456)
            every { token } returns "testToken"
        }

        coJustRun { setGuildLocaleUseCase.removeLocale(any()) }
        coEvery { localizationService.getString(any(), any(), any()) } returns "Locale reset"

        // When
        localeCommand.onExecute(interaction, response)

        // Then
        coVerify(exactly = 1) {
            setGuildLocaleUseCase.removeLocale(testGuildId)
        }
    }

    @Test
    fun `Given invalid locale When onExecute is called Then show error message`() = runTest {
        // Given
        val mockKord: Kord = mockk {
            every { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            every { defaultSupplier } returns mockk()
            every { rest } returns mockk {
                every { interaction } returns mockk(relaxed = true)
            }
        }
        val testGuildId = Snowflake(123)
        val interaction = mockk<ChatInputCommandInteraction> {
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { data } returns mockk {
                every { guildId.value } returns testGuildId
            }
            every { kord } returns mockKord
            every { command } returns mockk {
                every { strings } returns mapOf("locale" to "invalid-locale")
            }
        }
        val response = mockk<DeferredPublicMessageInteractionResponseBehavior> {
            every { kord } returns mockKord
            every { applicationId } returns Snowflake(456)
            every { token } returns "testToken"
        }

        coEvery { localizationService.getString(any(), any(), any()) } returns "Invalid locale"

        // When
        localeCommand.onExecute(interaction, response)

        // Then
        coVerify(exactly = 0) {
            setGuildLocaleUseCase(any(), any())
        }
    }

    @Test
    fun `Given null guildId When onExecute is called Then show error message`() = runTest {
        // Given
        val mockKord: Kord = mockk {
            every { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            every { defaultSupplier } returns mockk()
            every { rest } returns mockk {
                every { interaction } returns mockk(relaxed = true)
            }
        }
        val interaction = mockk<ChatInputCommandInteraction> {
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { data } returns mockk {
                every { guildId.value } returns null
            }
            every { kord } returns mockKord
            every { command } returns mockk {
                every { strings } returns mapOf("locale" to "es-ES")
            }
        }
        val response = mockk<DeferredPublicMessageInteractionResponseBehavior> {
            every { kord } returns mockKord
            every { applicationId } returns Snowflake(456)
            every { token } returns "testToken"
        }

        coEvery { localizationService.getString(any(), any(), any()) } returns "No guild"

        // When
        assertThrows<BotException.UserException.NotInGuildException> {
            localeCommand.onExecute(interaction, response)
        }

        // Then
        coVerify(exactly = 0) {
            setGuildLocaleUseCase(any(), any())
        }
    }

    @Test
    fun `Given null locale input When onExecute is called Then show error message`() = runTest {
        // Given
        val mockKord: Kord = mockk {
            every { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            every { defaultSupplier } returns mockk()
            every { rest } returns mockk {
                every { interaction } returns mockk(relaxed = true)
            }
        }
        val testGuildId = Snowflake(123)
        val interaction = mockk<ChatInputCommandInteraction> {
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { data } returns mockk {
                every { guildId.value } returns testGuildId
            }
            every { kord } returns mockKord
            every { command } returns mockk {
                every { strings } returns emptyMap()
            }
        }
        val response = mockk<DeferredPublicMessageInteractionResponseBehavior> {
            every { kord } returns mockKord
            every { applicationId } returns Snowflake(456)
            every { token } returns "testToken"
        }

        coEvery { localizationService.getStringFormat(any(), any(), any(), any()) } returns "No content provided"

        // When
        assertThrows<BotException.UserException.NoContentProvidedException> {
            localeCommand.onExecute(interaction, response)
        }

        // Then
        coVerify(exactly = 0) {
            setGuildLocaleUseCase(any(), any())
        }
    }
}
