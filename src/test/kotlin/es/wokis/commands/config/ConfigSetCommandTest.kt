package es.wokis.commands.config

import dev.kord.common.Locale
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import es.wokis.commands.config.ConfigSetCommand
import es.wokis.localization.LocalizationKeys
import es.wokis.services.config.ConfigService
import es.wokis.services.localization.LocalizationService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mock.mockedKord
import mock.mockedResponse
import org.junit.jupiter.api.Test

private const val ARGUMENT_SECTION = "section"
private const val ARGUMENT_KEY = "key"
private const val ARGUMENT_VALUE = "value"

class ConfigSetCommandTest {

    private val configService: ConfigService = mockk()
    private val localizationService: LocalizationService = mockk()

    private val configSetCommand = ConfigSetCommand(
        configService = configService,
        localizationService = localizationService
    )

    // Note: The success path test (Given valid section/key/value) is omitted
    // because updateConfigValue() is a private method that reads from a file.
    // Testing this would require file system setup or refactoring.
    // All error paths are tested below.

    @Test
    fun `Given invalid section When onExecute Then show invalid section error`() = runTest {
        // Given
        val interaction = createMockInteraction(
            section = "invalidsection",
            key = "enabled",
            value = "true"
        )

        coEvery {
            localizationService.getString(
                LocalizationKeys.CONFIG_INVALID_SECTION,
                any(),
                any()
            )
        } returns "Invalid section"

        // When
        configSetCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            localizationService.getString(
                LocalizationKeys.CONFIG_INVALID_SECTION,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        }
    }

    @Test
    fun `Given null section When onExecute Then show invalid section error`() = runTest {
        // Given
        val interaction = createMockInteraction(
            section = null,
            key = "enabled",
            value = "true"
        )

        coEvery {
            localizationService.getString(
                LocalizationKeys.CONFIG_INVALID_SECTION,
                any(),
                any()
            )
        } returns "Invalid section"

        // When
        configSetCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            localizationService.getString(
                LocalizationKeys.CONFIG_INVALID_SECTION,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        }
    }

    @Test
    fun `Given invalid key for section When onExecute Then show invalid key error`() = runTest {
        // Given
        val interaction = createMockInteraction(
            section = "database",
            key = "invalidkey",
            value = "true"
        )

        coEvery {
            localizationService.getString(
                LocalizationKeys.CONFIG_INVALID_KEY,
                any(),
                any()
            )
        } returns "Invalid key"

        // When
        configSetCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            localizationService.getString(
                LocalizationKeys.CONFIG_INVALID_KEY,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        }
    }

    @Test
    fun `Given null key for section When onExecute Then show invalid key error`() = runTest {
        // Given
        val interaction = createMockInteraction(
            section = "database",
            key = null,
            value = "true"
        )

        coEvery {
            localizationService.getString(
                LocalizationKeys.CONFIG_INVALID_KEY,
                any(),
                any()
            )
        } returns "Invalid key"

        // When
        configSetCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            localizationService.getString(
                LocalizationKeys.CONFIG_INVALID_KEY,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        }
    }

    @Test
    fun `Given empty value When onExecute Then show no content error`() = runTest {
        // Given
        val interaction = createMockInteraction(
            section = "database",
            key = "username",
            value = ""
        )

        coEvery {
            localizationService.getString(
                LocalizationKeys.ERROR_NO_CONTENT_PROVIDED,
                any(),
                any()
            )
        } returns "No content provided"

        // When
        configSetCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            localizationService.getString(
                LocalizationKeys.ERROR_NO_CONTENT_PROVIDED,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        }
    }

    @Test
    fun `Given null value When onExecute Then show no content error`() = runTest {
        // Given
        val interaction = createMockInteraction(
            section = "database",
            key = "username",
            value = null
        )

        coEvery {
            localizationService.getString(
                LocalizationKeys.ERROR_NO_CONTENT_PROVIDED,
                any(),
                any()
            )
        } returns "No content provided"

        // When
        configSetCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            localizationService.getString(
                LocalizationKeys.ERROR_NO_CONTENT_PROVIDED,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        }
    }

    @Test
    fun `Given discord_bot_token as section When onExecute Then show invalid section error`() = runTest {
        // Given
        val interaction = createMockInteraction(
            section = "discord_bot_token",
            key = "token",
            value = "newtoken"
        )

        coEvery {
            localizationService.getString(
                LocalizationKeys.CONFIG_INVALID_SECTION,
                any(),
                any()
            )
        } returns "Invalid section"

        // When
        configSetCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            localizationService.getString(
                LocalizationKeys.CONFIG_INVALID_SECTION,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        }
    }

    @Test
    fun `Given database password key When onExecute Then show cannot modify error`() = runTest {
        // Given
        val interaction = createMockInteraction(
            section = "database",
            key = "password",
            value = "newpassword"
        )

        coEvery {
            localizationService.getString(
                LocalizationKeys.CONFIG_CANNOT_MODIFY_TOKEN,
                any(),
                any()
            )
        } returns "Cannot modify token"

        // When
        configSetCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            localizationService.getString(
                LocalizationKeys.CONFIG_CANNOT_MODIFY_TOKEN,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        }
    }

    @Test
    fun `Given exception during reload When onExecute Then show unexpected error`() = runTest {
        // Given
        val interaction = createMockInteraction(
            section = "spotify",
            key = "enabled",
            value = "true"
        )

        coEvery {
            localizationService.getString(
                LocalizationKeys.ERROR_UNEXPECTED,
                any(),
                any()
            )
        } returns "Unexpected error"
        every { configService.reload() } throws RuntimeException("Reload failed")

        // When
        configSetCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            localizationService.getString(
                LocalizationKeys.ERROR_UNEXPECTED,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        }
    }

    private fun createMockInteraction(section: String?, key: String?, value: String?): ChatInputCommandInteraction {
        val stringsMap = mutableMapOf<String, String>().apply {
            section?.let { put(ARGUMENT_SECTION, it) }
            key?.let { put(ARGUMENT_KEY, it) }
            value?.let { put(ARGUMENT_VALUE, it) }
        }

        return mockk {
            every { kord } returns mockedKord
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { data } returns mockk {
                every { guildId.value } returns null
            }
            every { command } returns mockk {
                every { strings } returns stringsMap
            }
            every { user } returns mockk {
                every { id } returns mockk()
            }
        }
    }
}
