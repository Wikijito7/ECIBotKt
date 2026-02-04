package commands.next

import dev.kord.common.Locale
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import es.wokis.commands.CommandName
import es.wokis.commands.next.NextCommand
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.lavaplayer.model.TrackBO
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import es.wokis.utils.getMemberVoiceChannel
import io.mockk.*
import kotlinx.coroutines.test.runTest
import mock.*
import org.junit.jupiter.api.Test

class NextCommandTest {

    private val guildQueueService: GuildQueueService = mockk()
    private val localizationService: LocalizationService = mockk()

    private val nextCommand = NextCommand(
        guildQueueService = guildQueueService,
        localizationService = localizationService
    )

    @Test
    fun `Given command with monochrome url When onExecute is called Then transform and load as next`() = runTest {
        // Given
        val url = "https://monochrome.samidy.com/track/97034820"
        val expectedTransformedUrl = "https://tidal.com/track/97034820"
        val mockedStrings: Map<String, String> = mapOf("track" to url)
        val interaction: ChatInputCommandInteraction = mockk {
            every { kord } returns mockedKord
            every { channel } returns mockedTextChannel
            every { command } returns mockk {
                every { strings } returns mockedStrings
            }
        }
        val lavaPlayerService = mockk<GuildLavaPlayerService> {
            justRun { loadAndPlayNext(any()) }
            every { isQueueEmpty() } returns false
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
        every { localizationService.getString(any(), any()) } returns ""
        every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns ""

        // When
        nextCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            guildQueueService.getOrCreateLavaPlayerService(
                interaction = interaction
            )
            lavaPlayerService.loadAndPlayNext(expectedTransformedUrl)
        }
    }

    @Test
    fun `Given command with regular url When onExecute is called Then load as next without transformation`() = runTest {
        // Given
        val url = "https://youtube.com/watch?v=123"
        val mockedStrings: Map<String, String> = mapOf("track" to url)
        val interaction: ChatInputCommandInteraction = mockk {
            every { kord } returns mockedKord
            every { channel } returns mockedTextChannel
            every { command } returns mockk {
                every { strings } returns mockedStrings
            }
        }
        val lavaPlayerService = mockk<GuildLavaPlayerService> {
            justRun { loadAndPlayNext(any()) }
            every { isQueueEmpty() } returns false
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
        every { localizationService.getString(any(), any()) } returns ""
        every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns ""

        // When
        nextCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            guildQueueService.getOrCreateLavaPlayerService(
                interaction = interaction
            )
            lavaPlayerService.loadAndPlayNext(url)
        }
    }

    @Test
    fun `Given command with search term and matching track When onExecute is called Then move track to next`() = runTest {
        // Given
        val searchTerm = "test song"
        val mockedStrings: Map<String, String> = mapOf("track" to searchTerm)
        val interaction: ChatInputCommandInteraction = mockk {
            every { kord } returns mockedKord
            every { channel } returns mockedTextChannel
            every { command } returns mockk {
                every { strings } returns mockedStrings
            }
        }
        val mockTrack: TrackBO = mockk(relaxed = true)
        val lavaPlayerService = mockk<GuildLavaPlayerService> {
            every { isQueueEmpty() } returns false
            every { moveTrackToNext(searchTerm) } returns mockTrack
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
        every { localizationService.getString(any(), any()) } returns ""
        every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns ""

        // When
        nextCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            guildQueueService.getOrCreateLavaPlayerService(
                interaction = interaction
            )
            lavaPlayerService.isQueueEmpty()
            lavaPlayerService.moveTrackToNext(searchTerm)
        }
        coVerify(exactly = 0) {
            lavaPlayerService.loadAndPlayNext(any())
        }
    }

    @Test
    fun `Given command with search term and no matching track When onExecute is called Then warn user and try lavaplayer`() = runTest {
        // Given
        val searchTerm = "nonexistent song"
        val mockedStrings: Map<String, String> = mapOf("track" to searchTerm)
        val interaction: ChatInputCommandInteraction = mockk {
            every { kord } returns mockedKord
            every { channel } returns mockedTextChannel
            every { command } returns mockk {
                every { strings } returns mockedStrings
            }
        }
        val lavaPlayerService = mockk<GuildLavaPlayerService> {
            every { isQueueEmpty() } returns false
            every { moveTrackToNext(searchTerm) } returns null
            justRun { loadAndPlayNext(any()) }
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
        every { localizationService.getString(any(), any()) } returns ""
        every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns ""

        // When
        nextCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            guildQueueService.getOrCreateLavaPlayerService(
                interaction = interaction
            )
            lavaPlayerService.isQueueEmpty()
            lavaPlayerService.moveTrackToNext(searchTerm)
            lavaPlayerService.loadAndPlayNext(searchTerm)
        }
    }

    @Test
    fun `Given command with search term and empty queue When onExecute is called Then show empty queue message`() = runTest {
        // Given
        val searchTerm = "test song"
        val mockedStrings: Map<String, String> = mapOf("track" to searchTerm)
        val interaction: ChatInputCommandInteraction = mockk {
            every { kord } returns mockedKord
            every { channel } returns mockedTextChannel
            every { command } returns mockk {
                every { strings } returns mockedStrings
            }
        }
        val lavaPlayerService = mockk<GuildLavaPlayerService> {
            every { isQueueEmpty() } returns true
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
        every { localizationService.getString(any(), any()) } returns ""
        every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns ""

        // When
        nextCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            guildQueueService.getOrCreateLavaPlayerService(
                interaction = interaction
            )
            lavaPlayerService.isQueueEmpty()
        }
        coVerify(exactly = 0) {
            lavaPlayerService.moveTrackToNext(any())
            lavaPlayerService.loadAndPlayNext(any())
        }
    }

    @Test
    fun `Given command without input When onExecute is called Then show error message`() = runTest {
        // Given
        val mockedStrings: Map<String, String> = emptyMap()
        val interaction: ChatInputCommandInteraction = mockk {
            every { kord } returns mockedKord
            every { channel } returns mockedTextChannel
            every { command } returns mockk {
                every { strings } returns mockedStrings
            }
        }

        every { interaction.guildLocale } returns Locale.ENGLISH_UNITED_STATES
        every { localizationService.getString(any(), any()) } returns ""
        every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns ""

        // When
        nextCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 0) {
            guildQueueService.getOrCreateLavaPlayerService(any())
        }
    }
}
