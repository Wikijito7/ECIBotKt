package commands.sound

import dev.kord.common.Locale
import dev.kord.core.entity.interaction.AutoCompleteInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import es.wokis.commands.sound.SoundCommand
import es.wokis.localization.LocalizationKeys
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import es.wokis.utils.getFolderContent
import es.wokis.utils.getMemberVoiceChannel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import mock.mockedKord
import mock.mockedResponse
import mock.mockedTextChannel
import mock.mockedVoiceChannel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class SoundCommandTest {

    private val guildQueueService: GuildQueueService = mockk()
    private val localizationService: LocalizationService = mockk()
    private val testAudioDir = File("./audio")
    private val testAudioFile = File("./audio/manolete.mp3")

    private val soundCommand = SoundCommand(
        guildQueueService = guildQueueService,
        localizationService = localizationService
    )

    @BeforeEach
    fun setup() {
        mockkStatic(ChatInputCommandInteraction::getMemberVoiceChannel)
        coEvery { localizationService.getString(any(), any(), any()) } returns ""
        coEvery { localizationService.getStringFormat(any(), any(), any(), *anyVararg()) } returns ""

        testAudioDir.mkdirs()
        testAudioFile.createNewFile()
    }

    @AfterEach
    fun tearDown() {
        testAudioFile.delete()
        testAudioDir.delete()
        unmockkStatic(::getFolderContent)
    }

    @Test
    fun `Given sound name When onExecute is called Then play local file with custom name`() = runTest {
        // Given
        val soundName = "manolete"
        val mockedStrings: Map<String, String> = mapOf("name" to soundName)
        val interaction: ChatInputCommandInteraction = mockk {
            every { kord } returns mockedKord
            every { channel } returns mockedTextChannel
            every { command } returns mockk {
                every { strings } returns mockedStrings
            }
            every { data } returns mockk {
                every { guildId.value } returns null
            }
        }
        val lavaPlayerService = mockk<GuildLavaPlayerService> {
            justRun { loadAndPlayMultipleWithCustomName(any(), any()) }
        }

        coEvery {
            interaction.getMemberVoiceChannel(mockedKord)
        } returns mockedVoiceChannel

        coEvery {
            guildQueueService.getOrCreateLavaPlayerService(
                interaction = interaction
            )
        } returns lavaPlayerService
        every { interaction.guildLocale } returns Locale.ENGLISH_UNITED_STATES

        // When
        soundCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            guildQueueService.getOrCreateLavaPlayerService(
                interaction = interaction
            )
            lavaPlayerService.loadAndPlayMultipleWithCustomName(listOf("./audio/manolete.mp3"), soundName)
        }
    }

    @Test
    fun `Given empty sound name When onExecute is called Then return error`() = runTest {
        // Given
        val mockedStrings: Map<String, String> = mapOf("name" to "")
        val interaction: ChatInputCommandInteraction = mockk {
            every { kord } returns mockedKord
            every { channel } returns mockedTextChannel
            every { command } returns mockk {
                every { strings } returns mockedStrings
            }
            every { data } returns mockk {
                every { guildId.value } returns null
            }
        }

        coEvery {
            interaction.getMemberVoiceChannel(mockedKord)
        } returns mockedVoiceChannel
        every { interaction.guildLocale } returns Locale.ENGLISH_UNITED_STATES

        // When
        soundCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 0) {
            guildQueueService.getOrCreateLavaPlayerService(any())
        }
    }

    @Test
    fun `Given missing sound name When onExecute is called Then return error`() = runTest {
        // Given
        val mockedStrings: Map<String, String> = emptyMap()
        val interaction: ChatInputCommandInteraction = mockk {
            every { kord } returns mockedKord
            every { channel } returns mockedTextChannel
            every { command } returns mockk {
                every { strings } returns mockedStrings
            }
            every { data } returns mockk {
                every { guildId.value } returns null
            }
        }

        coEvery {
            interaction.getMemberVoiceChannel(mockedKord)
        } returns mockedVoiceChannel
        every { interaction.guildLocale } returns Locale.ENGLISH_UNITED_STATES

        // When
        soundCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 0) {
            guildQueueService.getOrCreateLavaPlayerService(any())
        }
    }

    @Test
    fun `Given non-existent sound name When onExecute is called Then return no matches error`() = runTest {
        // Given
        val soundName = "nonexistent"
        val mockedStrings: Map<String, String> = mapOf("name" to soundName)
        val interaction: ChatInputCommandInteraction = mockk {
            every { kord } returns mockedKord
            every { channel } returns mockedTextChannel
            every { command } returns mockk {
                every { strings } returns mockedStrings
            }
            every { data } returns mockk {
                every { guildId.value } returns null
            }
        }

        coEvery {
            interaction.getMemberVoiceChannel(mockedKord)
        } returns mockedVoiceChannel

        val lavaPlayerService = mockk<GuildLavaPlayerService>()
        coEvery {
            guildQueueService.getOrCreateLavaPlayerService(interaction = interaction)
        } returns lavaPlayerService
        every { interaction.guildLocale } returns Locale.ENGLISH_UNITED_STATES

        // When
        soundCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            guildQueueService.getOrCreateLavaPlayerService(interaction = interaction)
        }
        coVerify(exactly = 0) {
            lavaPlayerService.loadAndPlayMultipleWithCustomName(any(), any())
        }
        coVerify(exactly = 1) {
            localizationService.getStringFormat(
                key = LocalizationKeys.NO_MATCHES,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES,
                arguments = any()
            )
        }
        coVerify(exactly = 0) {
            localizationService.getString(
                key = LocalizationKeys.SEARCHING_SONG,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        }
    }

    @Test
    fun `Given non-existent sound name When onExecute is called Then only one respond and never SEARCHING_SONG`() = runTest {
        // Regression test: ensures the file check happens before the "searching" response,
        // so only NO_MATCHES is sent (not both SEARCHING_SONG and NO_MATCHES).
        // Given
        val soundName = "nonexistent"
        val mockedStrings: Map<String, String> = mapOf("name" to soundName)
        val interaction: ChatInputCommandInteraction = mockk {
            every { kord } returns mockedKord
            every { channel } returns mockedTextChannel
            every { command } returns mockk {
                every { strings } returns mockedStrings
            }
            every { data } returns mockk {
                every { guildId.value } returns null
            }
        }

        coEvery {
            interaction.getMemberVoiceChannel(mockedKord)
        } returns mockedVoiceChannel

        val lavaPlayerService = mockk<GuildLavaPlayerService>()
        coEvery {
            guildQueueService.getOrCreateLavaPlayerService(interaction = interaction)
        } returns lavaPlayerService
        every { interaction.guildLocale } returns Locale.ENGLISH_UNITED_STATES

        // When
        soundCommand.onExecute(interaction, mockedResponse)

        // Then: only NO_MATCHES is localised, never SEARCHING_SONG
        coVerify(exactly = 1) {
            localizationService.getStringFormat(
                key = LocalizationKeys.NO_MATCHES,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES,
                arguments = any()
            )
        }
        coVerify(exactly = 0) {
            localizationService.getString(
                key = LocalizationKeys.SEARCHING_SONG,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        }
    }

    @Test
    fun `Given autocomplete with matching input When onAutoComplete is called Then return filtered sounds`() = runTest {
        // Given
        mockkStatic(::getFolderContent)
        val testFile = mockk<File> {
            every { nameWithoutExtension } returns "manolete"
        }
        every { getFolderContent("./audio/") } returns listOf(testFile)

        val interaction = mockk<AutoCompleteInteraction>(relaxed = true) {
            every { command.strings["name"] } returns "man"
        }

        // When
        soundCommand.onAutoComplete(interaction)

        // Then
        verify(exactly = 1) {
            getFolderContent("./audio/")
        }
    }

    @Test
    fun `Given autocomplete with empty input When onAutoComplete is called Then return empty list`() = runTest {
        // Given
        mockkStatic(::getFolderContent)
        val interaction = mockk<AutoCompleteInteraction>(relaxed = true) {
            every { command.strings["name"] } returns ""
        }

        // When
        soundCommand.onAutoComplete(interaction)

        // Then
        verify(exactly = 0) {
            getFolderContent(any<String>())
        }
    }

    @Test
    fun `Given exception thrown When onExecute is called Then throw exception`() = runTest {
        // Given
        val soundName = "manolete"
        val mockedStrings: Map<String, String> = mapOf("name" to soundName)
        val interaction: ChatInputCommandInteraction = mockk {
            every { kord } returns mockedKord
            every { channel } returns mockedTextChannel
            every { command } returns mockk {
                every { strings } returns mockedStrings
            }
            every { data } returns mockk {
                every { guildId.value } returns null
            }
        }

        coEvery {
            interaction.getMemberVoiceChannel(mockedKord)
        } returns mockedVoiceChannel

        coEvery {
            guildQueueService.getOrCreateLavaPlayerService(interaction = interaction)
        } throws IllegalStateException("Test exception")
        every { interaction.guildLocale } returns Locale.ENGLISH_UNITED_STATES

        // When/Then - exception is thrown (handled by CommandHandlerService)
        assertThrows<IllegalStateException> {
            soundCommand.onExecute(interaction, mockedResponse)
        }

        // Then
        coVerify(exactly = 1) {
            guildQueueService.getOrCreateLavaPlayerService(interaction = interaction)
        }
    }
}
