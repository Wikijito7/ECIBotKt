package es.wokis.commands.config

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import es.wokis.data.response.ErrorType
import es.wokis.data.response.RemoteResponse
import es.wokis.localization.LocalizationKeys
import es.wokis.services.config.ConfigService
import es.wokis.services.localization.LocalizationService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mock.mockedKord
import mock.mockedResponse
import org.junit.jupiter.api.Test

class ConfigReloadCommandTest {

    private val configService: ConfigService = mockk()
    private val localizationService: LocalizationService = mockk()

    private val configReloadCommand = ConfigReloadCommand(
        configService = configService,
        localizationService = localizationService
    )

    @Test
    fun `Given reload succeeds as owner When onExecute Then show success message`() = runTest {
        val interaction = createMockInteraction(Locale.ENGLISH_UNITED_STATES)

        every { configService.isOwner(any()) } returns true
        every { configService.reload() } returns RemoteResponse.Success(mockk())
        coEvery {
            localizationService.getString(
                LocalizationKeys.CONFIG_RELOAD_SUCCESS,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        } returns "Configuration reloaded successfully"

        configReloadCommand.onExecute(interaction, mockedResponse)

        coVerify(exactly = 1) {
            localizationService.getString(
                LocalizationKeys.CONFIG_RELOAD_SUCCESS,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        }
    }

    @Test
    fun `Given non-owner user When onExecute Then show auth error`() = runTest {
        val interaction = createMockInteraction(Locale.ENGLISH_UNITED_STATES)

        every { configService.isOwner(any()) } returns false
        coEvery {
            localizationService.getString(
                LocalizationKeys.CONFIG_AUTH_REQUIRED,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        } returns "Only the bot owner can use this command"

        configReloadCommand.onExecute(interaction, mockedResponse)

        coVerify(exactly = 1) {
            localizationService.getString(
                LocalizationKeys.CONFIG_AUTH_REQUIRED,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        }
    }

    @Test
    fun `Given reload returns error When onExecute Then show error message`() = runTest {
        val interaction = createMockInteraction(Locale.ENGLISH_UNITED_STATES)

        every { configService.isOwner(any()) } returns true
        every { configService.reload() } returns RemoteResponse.Error(
            ErrorType.UnknownError(RuntimeException("fail"), "Failed to reload config")
        )
        coEvery {
            localizationService.getString(
                LocalizationKeys.ERROR_UNEXPECTED,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        } returns "An unexpected error occurred"

        configReloadCommand.onExecute(interaction, mockedResponse)

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
        val interaction = createMockInteraction(Locale.SPANISH_SPAIN)

        every { configService.isOwner(any()) } returns true
        every { configService.reload() } returns RemoteResponse.Success(mockk())
        coEvery {
            localizationService.getString(
                LocalizationKeys.CONFIG_RELOAD_SUCCESS,
                guildId = null,
                discordLocale = Locale.SPANISH_SPAIN
            )
        } returns "Configuración recargada correctamente"

        configReloadCommand.onExecute(interaction, mockedResponse)

        coVerify(exactly = 1) {
            localizationService.getString(
                LocalizationKeys.CONFIG_RELOAD_SUCCESS,
                guildId = null,
                discordLocale = Locale.SPANISH_SPAIN
            )
        }
    }

    private fun createMockInteraction(locale: Locale): ChatInputCommandInteraction = mockk {
        every { kord } returns mockedKord
        every { guildLocale } returns locale
        every { data } returns mockk {
            every { guildId.value } returns null
        }
        every { user } returns mockk {
            every { id } returns Snowflake(123u)
        }
    }
}
