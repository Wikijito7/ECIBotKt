package es.wokis.commands.config

import dev.kord.common.Locale
import dev.kord.core.entity.interaction.AutoCompleteInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import es.wokis.commands.config.ConfigGetCommand
import es.wokis.localization.LocalizationKeys
import es.wokis.services.config.Config
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

class ConfigGetCommandTest {

    private val configService: ConfigService = mockk()
    private val localizationService: LocalizationService = mockk()

    private val configGetCommand = ConfigGetCommand(
        configService = configService,
        localizationService = localizationService
    )

    @Test
    fun `Given valid section When onExecute Then display config`() = runTest {
        // Given
        val mockConfig = createMockConfig()
        val interaction = mockk<ChatInputCommandInteraction> {
            every { kord } returns mockedKord
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { data } returns mockk {
                every { guildId.value } returns null
            }
            every { command.strings["section"] } returns "database"
        }

        every { configService.config } returns mockConfig
        coEvery {
            localizationService.getStringFormat(
                LocalizationKeys.CONFIG_GET_DISPLAY,
                any(),
                any(),
                *anyVararg()
            )
        } returns "Database config displayed"

        // When
        configGetCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            localizationService.getStringFormat(
                LocalizationKeys.CONFIG_GET_DISPLAY,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES,
                arguments = arrayOf("database", mockConfig.database.toString())
            )
        }
    }

    @Test
    fun `Given invalid section When onExecute Then show error`() = runTest {
        // Given
        val interaction = mockk<ChatInputCommandInteraction> {
            every { kord } returns mockedKord
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { data } returns mockk {
                every { guildId.value } returns null
            }
            every { command.strings["section"] } returns "invalidsection"
        }

        coEvery {
            localizationService.getString(
                LocalizationKeys.CONFIG_INVALID_SECTION,
                any(),
                any()
            )
        } returns "Invalid section provided"

        // When
        configGetCommand.onExecute(interaction, mockedResponse)

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
    fun `Given null section When onExecute Then show error`() = runTest {
        // Given
        val interaction = mockk<ChatInputCommandInteraction> {
            every { kord } returns mockedKord
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { data } returns mockk {
                every { guildId.value } returns null
            }
            every { command.strings["section"] } returns null
        }

        coEvery {
            localizationService.getString(
                LocalizationKeys.CONFIG_INVALID_SECTION,
                any(),
                any()
            )
        } returns "Invalid section provided"

        // When
        configGetCommand.onExecute(interaction, mockedResponse)

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
    fun `Given autocomplete with empty input When onAutoComplete Then completes successfully`() = runTest {
        // Given
        val interaction = mockk<AutoCompleteInteraction> {
            every { kord } returns mockedKord
            every { command.strings["section"] } returns ""
            every { token } returns "mock_token"
            every { id } returns mockk()
        }

        // When & Then - should complete without exception
        configGetCommand.onAutoComplete(interaction)
    }

    @Test
    fun `Given autocomplete with partial input When onAutoComplete Then completes successfully`() = runTest {
        // Given
        val interaction = mockk<AutoCompleteInteraction> {
            every { kord } returns mockedKord
            every { command.strings["section"] } returns "sp"
            every { token } returns "mock_token"
            every { id } returns mockk()
        }

        // When & Then - should complete without exception
        configGetCommand.onAutoComplete(interaction)
    }

    @Test
    fun `Given autocomplete with no matches When onAutoComplete Then completes successfully`() = runTest {
        // Given
        val interaction = mockk<AutoCompleteInteraction> {
            every { kord } returns mockedKord
            every { command.strings["section"] } returns "xyz"
            every { token } returns "mock_token"
            every { id } returns mockk()
        }

        // When & Then - should complete without exception
        configGetCommand.onAutoComplete(interaction)
    }

    private fun createMockConfig(): Config {
        return Config(
            discordBotToken = "test-token",
            debug = false,
            database = es.wokis.services.config.DatabaseConfig(
                enabled = true,
                username = "testuser",
                password = "testpass",
                database = "testdb"
            ),
            youtube = es.wokis.services.config.YouTubeConfig(
                enabled = true,
                oauth2Token = "yt-token",
                poToken = null,
                visitorData = null,
                remoteCipherUrl = null,
                remoteCipherPassword = null
            ),
            deezer = es.wokis.services.config.DeezerConfig(
                enabled = true,
                masterDecryptionKey = "deezer-key",
                arlToken = "deezer-token"
            ),
            spotify = es.wokis.services.config.SpotifyConfig(
                enabled = true,
                clientId = "spotify-client-id",
                clientSecret = "spotify-client-secret",
                customEndpoint = "https://api.spotify.com"
            ),
            tidal = es.wokis.services.config.TidalConfig(
                enabled = true,
                countryCode = "US",
                token = "tidal-token"
            ),
            kokoro = es.wokis.services.config.KokoroConfig(
                enabled = true,
                baseUrl = "http://localhost:8080",
                defaultVoice = "af_bella",
                defaultSpeed = 1.0f,
                defaultLangCode = "en-us"
            )
        )
    }
}
