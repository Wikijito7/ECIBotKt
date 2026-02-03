package commands.sound

import dev.kord.common.Locale
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import es.wokis.commands.sound.SoundCommand
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import es.wokis.utils.getMemberVoiceChannel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import mock.mockedKord
import mock.mockedResponse
import mock.mockedTextChannel
import mock.mockedVoiceChannel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
        every { localizationService.getString(any(), any()) } returns ""
        every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns ""

        testAudioDir.mkdirs()
        testAudioFile.createNewFile()
    }

    @AfterEach
    fun tearDown() {
        testAudioFile.delete()
        testAudioDir.delete()
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
}
