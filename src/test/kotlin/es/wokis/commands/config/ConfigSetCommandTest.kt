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

    @Test
    fun `Given valid section key and value When onExecute Then update config successfully`() = runTest {
        val interaction = createMockInteraction(
            section = "database",
            key = "username",
            value = "newuser"
        )

        every { configService.isOwner(any()) } returns true
        every {
            configService.updateConfigValue("database", "username", "newuser")
        } returns RemoteResponse.Success(Unit)
        coEvery {
            localizationService.getStringFormat(
                LocalizationKeys.CONFIG_SET_SUCCESS,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES,
                arguments = arrayOf("database.username", "newuser")
            )
        } returns "Config database.username updated to: newuser"

        configSetCommand.onExecute(interaction, mockedResponse)

        coVerify(exactly = 1) {
            localizationService.getStringFormat(
                LocalizationKeys.CONFIG_SET_SUCCESS,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES,
                arguments = arrayOf("database.username", "newuser")
            )
        }
    }

    @Test
    fun `Given non-owner user When onExecute Then show auth error`() = runTest {
        val interaction = createMockInteraction(
            section = "database",
            key = "username",
            value = "newuser"
        )

        every { configService.isOwner(any()) } returns false
        coEvery {
            localizationService.getString(
                LocalizationKeys.CONFIG_AUTH_REQUIRED,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        } returns "Only the bot owner can use this command"

        configSetCommand.onExecute(interaction, mockedResponse)

        coVerify(exactly = 1) {
            localizationService.getString(
                LocalizationKeys.CONFIG_AUTH_REQUIRED,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        }
    }

    @Test
    fun `Given invalid section When onExecute Then show invalid section error`() = runTest {
        val interaction = createMockInteraction(
            section = "invalidsection",
            key = "enabled",
            value = "true"
        )

        every { configService.isOwner(any()) } returns true
        coEvery {
            localizationService.getString(
                LocalizationKeys.CONFIG_INVALID_SECTION,
                any(),
                any()
            )
        } returns "Invalid section"

        configSetCommand.onExecute(interaction, mockedResponse)

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
        val interaction = createMockInteraction(
            section = null,
            key = "enabled",
            value = "true"
        )

        every { configService.isOwner(any()) } returns true
        coEvery {
            localizationService.getString(
                LocalizationKeys.CONFIG_INVALID_SECTION,
                any(),
                any()
            )
        } returns "Invalid section"

        configSetCommand.onExecute(interaction, mockedResponse)

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
        val interaction = createMockInteraction(
            section = "database",
            key = "invalidkey",
            value = "true"
        )

        every { configService.isOwner(any()) } returns true
        coEvery {
            localizationService.getString(
                LocalizationKeys.CONFIG_INVALID_KEY,
                any(),
                any()
            )
        } returns "Invalid key"

        configSetCommand.onExecute(interaction, mockedResponse)

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
        val interaction = createMockInteraction(
            section = "database",
            key = null,
            value = "true"
        )

        every { configService.isOwner(any()) } returns true
        coEvery {
            localizationService.getString(
                LocalizationKeys.CONFIG_INVALID_KEY,
                any(),
                any()
            )
        } returns "Invalid key"

        configSetCommand.onExecute(interaction, mockedResponse)

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
        val interaction = createMockInteraction(
            section = "database",
            key = "username",
            value = ""
        )

        every { configService.isOwner(any()) } returns true
        coEvery {
            localizationService.getString(
                LocalizationKeys.ERROR_NO_CONTENT_PROVIDED,
                any(),
                any()
            )
        } returns "No content provided"

        configSetCommand.onExecute(interaction, mockedResponse)

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
        val interaction = createMockInteraction(
            section = "database",
            key = "username",
            value = null
        )

        every { configService.isOwner(any()) } returns true
        coEvery {
            localizationService.getString(
                LocalizationKeys.ERROR_NO_CONTENT_PROVIDED,
                any(),
                any()
            )
        } returns "No content provided"

        configSetCommand.onExecute(interaction, mockedResponse)

        coVerify(exactly = 1) {
            localizationService.getString(
                LocalizationKeys.ERROR_NO_CONTENT_PROVIDED,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        }
    }

    @Test
    fun `Given discord_bot_token When onExecute Then show invalid section error`() = runTest {
        val interaction = createMockInteraction(
            section = "discord_bot_token",
            key = "token",
            value = "newtoken"
        )

        every { configService.isOwner(any()) } returns true
        coEvery {
            localizationService.getString(
                LocalizationKeys.CONFIG_INVALID_SECTION,
                any(),
                any()
            )
        } returns "Invalid section"

        configSetCommand.onExecute(interaction, mockedResponse)

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
        val interaction = createMockInteraction(
            section = "database",
            key = "password",
            value = "newpassword"
        )

        every { configService.isOwner(any()) } returns true
        coEvery {
            localizationService.getString(
                LocalizationKeys.CONFIG_CANNOT_MODIFY_TOKEN,
                any(),
                any()
            )
        } returns "Cannot modify token"

        configSetCommand.onExecute(interaction, mockedResponse)

        coVerify(exactly = 1) {
            localizationService.getString(
                LocalizationKeys.CONFIG_CANNOT_MODIFY_TOKEN,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        }
    }

    @Test
    fun `Given error response from service When onExecute Then show unexpected error`() = runTest {
        val interaction = createMockInteraction(
            section = "spotify",
            key = "enabled",
            value = "true"
        )

        every { configService.isOwner(any()) } returns true
        every {
            configService.updateConfigValue("spotify", "enabled", "true")
        } returns RemoteResponse.Error(ErrorType.UnknownError(RuntimeException("fail"), "Failed to update config"))
        coEvery {
            localizationService.getString(
                LocalizationKeys.ERROR_UNEXPECTED,
                any(),
                any()
            )
        } returns "Unexpected error"

        configSetCommand.onExecute(interaction, mockedResponse)

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
                every { id } returns Snowflake(123u)
            }
        }
    }
}
