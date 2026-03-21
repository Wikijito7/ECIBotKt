package es.wokis.commands.config

import dev.kord.common.Locale
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import es.wokis.commands.config.ConfigReloadCommand
import es.wokis.localization.LocalizationKeys
import es.wokis.services.config.ConfigService
import es.wokis.services.localization.LocalizationService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import mock.mockedKord
import mock.mockedResponse
import org.junit.jupiter.api.Test

class ConfigReloadCommandTest {

    private val configService: ConfigService = mockk {
        every { reload() } returns mockk()
    }
    private val localizationService: LocalizationService = mockk()

    private val configReloadCommand = ConfigReloadCommand(
        configService = configService,
        localizationService = localizationService
    )

    @Test
    fun `Given reload succeeds When onExecute Then show success message`() = runTest {
        // Given
        val interaction = mockk<ChatInputCommandInteraction> {
            every { kord } returns mockedKord
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { data } returns mockk {
                every { guildId.value } returns null
            }
            every { user } returns mockk {
                every { id } returns mockk {
                    every { value } returns 12345u
                }
            }
        }

        coEvery {
            localizationService.getString(
                LocalizationKeys.CONFIG_RELOAD_SUCCESS,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        } returns "Configuration reloaded successfully"
        coEvery {
            localizationService.getString(
                LocalizationKeys.ERROR_UNEXPECTED,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        } returns "An unexpected error occurred"

        // When
        configReloadCommand.onExecute(interaction, mockedResponse)

        // Then
        verify(exactly = 1) { configService.reload() }
        coVerify(exactly = 1) {
            localizationService.getString(
                LocalizationKeys.CONFIG_RELOAD_SUCCESS,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        }
    }

    @Test
    fun `Given reload throws exception When onExecute Then show error message`() = runTest {
        // Given
        val interaction = mockk<ChatInputCommandInteraction> {
            every { kord } returns mockedKord
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { data } returns mockk {
                every { guildId.value } returns null
            }
            every { user } returns mockk {
                every { id } returns mockk {
                    every { value } returns 12345u
                }
            }
        }

        every { configService.reload() } throws RuntimeException("Config file not found")
        coEvery {
            localizationService.getString(
                LocalizationKeys.ERROR_UNEXPECTED,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        } returns "An unexpected error occurred"

        // When
        configReloadCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) { configService.reload() }
        coVerify(exactly = 1) {
            localizationService.getString(
                LocalizationKeys.ERROR_UNEXPECTED,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        }
    }

    @Test
    fun `Given spanish locale When onExecute Then use spanish locale`() = runTest {
        // Given
        val interaction = mockk<ChatInputCommandInteraction> {
            every { kord } returns mockedKord
            every { guildLocale } returns Locale.SPANISH_SPAIN
            every { data } returns mockk {
                every { guildId.value } returns null
            }
            every { user } returns mockk {
                every { id } returns mockk {
                    every { value } returns 12345u
                }
            }
        }

        coEvery {
            localizationService.getString(
                LocalizationKeys.CONFIG_RELOAD_SUCCESS,
                guildId = null,
                discordLocale = Locale.SPANISH_SPAIN
            )
        } returns "Configuración recargada correctamente"
        coEvery {
            localizationService.getString(
                LocalizationKeys.ERROR_UNEXPECTED,
                guildId = null,
                discordLocale = Locale.SPANISH_SPAIN
            )
        } returns "An unexpected error occurred"

        // When
        configReloadCommand.onExecute(interaction, mockedResponse)

        // Then
        verify(exactly = 1) { configService.reload() }
        coVerify(exactly = 1) {
            localizationService.getString(
                LocalizationKeys.CONFIG_RELOAD_SUCCESS,
                guildId = null,
                discordLocale = Locale.SPANISH_SPAIN
            )
        }
    }
}
