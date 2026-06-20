package commands.player

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.supplier.EntitySupplyStrategy
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import es.wokis.commands.ComponentsEnum
import es.wokis.commands.player.PlayerCommand
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.lavaplayer.LyricsService
import es.wokis.services.lavaplayer.model.TrackBO
import es.wokis.services.localization.LocalizationService
import es.wokis.services.player.PlayerChannelService
import es.wokis.services.queue.GuildQueueService
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import services.player.result.PlayerChannelResult
import kotlin.test.Ignore

private fun createMockTrackBO(): TrackBO {
    val audioTrack = mockk<AudioTrack> {
        every { info } returns AudioTrackInfo("title", "author", 0L, "", true, "", "", "")
        every { duration } returns 0L
        every { position } returns 0L
    }
    return TrackBO(audioTrack = audioTrack)
}

class PlayerCommandTest {

    private val localizationService: LocalizationService = mockk()
    private val guildQueueService: GuildQueueService = mockk()
    private val playerChannelService: PlayerChannelService = mockk()
    private val lyricsService: LyricsService = mockk()

    private val playerCommand = PlayerCommand(
        localizationService = localizationService,
        guildQueueService = guildQueueService,
        playerChannelService = playerChannelService,
        lyricsService = lyricsService
    )

    @Test
    fun `Given interaction When onExecute Then respond with player message`() = runTest {
        // Given
        val mockKord: Kord = mockk {
            every { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            every { defaultSupplier } returns mockk()
            coEvery { getGuild(any()) } returns mockk {
                every { name } returns "TestGuild"
            }
            every { rest } returns mockk {
                every { interaction } returns mockk(relaxed = true)
            }
        }
        val interaction = mockk<ChatInputCommandInteraction> {
            every { data } returns mockk {
                every { guildId.value } returns Snowflake(123)
            }
            every { kord } returns mockKord
            every { guildLocale } returns Locale.BULGARIAN
        }
        val response = mockk<DeferredPublicMessageInteractionResponseBehavior> {
            every { kord } returns mockKord
            every { applicationId } returns Snowflake(456)
            every { token } returns "testToken"
        }
        val guildLavaPlayerService: GuildLavaPlayerService = mockk {
            every { getQueue() } returns listOf()
            every { getCurrentPlayingTrack() } returns createMockTrackBO()
            every { isPaused() } returns false
            justRun { savePlayerMessage(any()) }
        }
        val mockMessage = mockk<Message>(relaxed = true)
        val mockChannel = mockk<TextChannel>(relaxed = true) {
            every { id } returns Snowflake(123456789)
        }

        val playerChannelResult = PlayerChannelResult(
            message = mockMessage,
            channel = mockChannel,
            isNewChannel = true
        )

        coEvery { guildQueueService.getOrCreateLavaPlayerService(any()) } returns guildLavaPlayerService
        coEvery { playerChannelService.sendPlayerMessage(any(), any()) } returns Result.success(playerChannelResult)
        coEvery { localizationService.getString(any(), any(), any()) } returns "TestMessage"
        coEvery { localizationService.getStringFormat(any(), any(), any(), *anyVararg()) } returns "Format"

        // When
        playerCommand.onExecute(interaction, response)

        // Then
        verify(exactly = 1) {
            guildLavaPlayerService.getQueue()
            guildLavaPlayerService.getCurrentPlayingTrack()
        }

        coVerify(exactly = 1) {
            playerChannelService.sendPlayerMessage(any(), any())
        }
    }

    @Test
    fun `Given resume interaction When onInteract Then respond with player message`() = runTest {
        // Given
        val mockKord: Kord = mockk {
            every { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            every { defaultSupplier } returns mockk()
            coEvery { getGuild(any()) } returns mockk {
                every { name } returns "TestGuild"
            }
            every { rest } returns mockk {
                every { interaction } returns mockk(relaxed = true)
            }
        }
        val interaction = mockk<ButtonInteraction> {
            every { data } returns mockk {
                every { guildId.value } returns Snowflake(123)
            }
            every { kord } returns mockKord
            every { guildLocale } returns Locale.BULGARIAN
            every { component } returns mockk {
                every { customId } returns ComponentsEnum.PLAYER_RESUME.customId
            }
            every { message } returns mockk(relaxed = true)
        }
        val guildLavaPlayerService: GuildLavaPlayerService = mockk {
            every { getQueue() } returns listOf()
            every { getCurrentPlayingTrack() } returns createMockTrackBO()
            every { isPaused() } returns false
            justRun { resume() }
        }

        coEvery { guildQueueService.getLavaPlayerService(any()) } returns guildLavaPlayerService
        coEvery { localizationService.getString(any(), any(), any()) } returns "TestMessage"
        coEvery { localizationService.getStringFormat(any(), any(), any(), *anyVararg()) } returns "Format"

        // When
        playerCommand.onInteract(interaction)

        // Then
        verify(exactly = 1) {
            guildLavaPlayerService.getQueue()
            guildLavaPlayerService.getCurrentPlayingTrack()
            guildLavaPlayerService.isPaused()
            guildLavaPlayerService.resume()
        }
    }

    @Test
    fun `Given skip interaction When onInteract Then respond with player message`() = runTest {
        // Given
        val mockKord: Kord = mockk {
            every { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            every { defaultSupplier } returns mockk()
            coEvery { getGuild(any()) } returns mockk {
                every { name } returns "TestGuild"
            }
            every { rest } returns mockk {
                every { interaction } returns mockk(relaxed = true)
            }
        }
        val interaction = mockk<ButtonInteraction> {
            every { data } returns mockk {
                every { guildId.value } returns Snowflake(123)
            }
            every { kord } returns mockKord
            every { guildLocale } returns Locale.BULGARIAN
            every { component } returns mockk {
                every { customId } returns ComponentsEnum.PLAYER_SKIP.customId
            }
            every { message } returns mockk(relaxed = true)
        }
        val guildLavaPlayerService: GuildLavaPlayerService = mockk {
            every { getQueue() } returns listOf()
            every { getCurrentPlayingTrack() } returns createMockTrackBO()
            every { isPaused() } returns false
            justRun { skip() }
        }

        coEvery { guildQueueService.getLavaPlayerService(any()) } returns guildLavaPlayerService
        coEvery { localizationService.getString(any(), any(), any()) } returns "TestMessage"
        coEvery { localizationService.getStringFormat(any(), any(), any(), *anyVararg()) } returns "Format"

        // When
        playerCommand.onInteract(interaction)

        // Then
        verify(exactly = 1) {
            guildLavaPlayerService.getQueue()
            guildLavaPlayerService.getCurrentPlayingTrack()
            guildLavaPlayerService.isPaused()
            guildLavaPlayerService.skip()
        }
    }

    @Test
    fun `Given shuffle interaction When onInteract Then respond with player message`() = runTest {
        // Given
        val mockKord: Kord = mockk {
            every { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            every { defaultSupplier } returns mockk()
            coEvery { getGuild(any()) } returns mockk {
                every { name } returns "TestGuild"
            }
            every { rest } returns mockk {
                every { interaction } returns mockk(relaxed = true)
            }
        }
        val interaction = mockk<ButtonInteraction> {
            every { data } returns mockk {
                every { guildId.value } returns Snowflake(123)
            }
            every { kord } returns mockKord
            every { guildLocale } returns Locale.BULGARIAN
            every { component } returns mockk {
                every { customId } returns ComponentsEnum.PLAYER_SHUFFLE.customId
            }
            every { message } returns mockk(relaxed = true)
        }
        val guildLavaPlayerService: GuildLavaPlayerService = mockk {
            every { getQueue() } returns listOf()
            every { getCurrentPlayingTrack() } returns createMockTrackBO()
            every { isPaused() } returns false
            justRun { shuffle() }
        }

        coEvery { guildQueueService.getLavaPlayerService(any()) } returns guildLavaPlayerService
        coEvery { localizationService.getString(any(), any(), any()) } returns "TestMessage"
        coEvery { localizationService.getStringFormat(any(), any(), any(), *anyVararg()) } returns "Format"

        // When
        playerCommand.onInteract(interaction)

        // Then
        verify(exactly = 1) {
            guildLavaPlayerService.getQueue()
            guildLavaPlayerService.getCurrentPlayingTrack()
            guildLavaPlayerService.isPaused()
            guildLavaPlayerService.shuffle()
        }
    }

    @Test
    fun `Given disconnect interaction When onInteract Then respond with player message`() = runTest {
        // Given
        val mockKord: Kord = mockk {
            every { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            every { defaultSupplier } returns mockk()
            coEvery { getGuild(any()) } returns mockk {
                every { name } returns "TestGuild"
            }
            every { rest } returns mockk {
                every { interaction } returns mockk(relaxed = true)
            }
        }
        val interaction = mockk<ButtonInteraction> {
            every { data } returns mockk {
                every { guildId.value } returns Snowflake(123)
            }
            every { kord } returns mockKord
            every { guildLocale } returns Locale.BULGARIAN
            every { component } returns mockk {
                every { customId } returns ComponentsEnum.PLAYER_DISCONNECT.customId
            }
            every { message } returns mockk(relaxed = true)
        }
        val guildLavaPlayerService: GuildLavaPlayerService = mockk {
            every { getQueue() } returns listOf()
            every { getCurrentPlayingTrack() } returns createMockTrackBO()
            every { isPaused() } returns false
            coJustRun { stop() }
        }

        coEvery { guildQueueService.getLavaPlayerService(any()) } returns guildLavaPlayerService
        coEvery { localizationService.getString(any(), any(), any()) } returns "TestMessage"
        coEvery { localizationService.getStringFormat(any(), any(), any(), *anyVararg()) } returns "Format"

        // When
        playerCommand.onInteract(interaction)

        // Then
        verify(exactly = 1) {
            guildLavaPlayerService.getQueue()
            guildLavaPlayerService.getCurrentPlayingTrack()
            guildLavaPlayerService.isPaused()
        }

        coVerify(exactly = 1) {
            guildLavaPlayerService.stop()
        }
    }

    @Test
    fun `Given pause interaction When onInteract Then respond with player message`() = runTest {
        // Given
        val mockKord: Kord = mockk {
            every { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            every { defaultSupplier } returns mockk()
            coEvery { getGuild(any()) } returns mockk {
                every { name } returns "TestGuild"
            }
            every { rest } returns mockk {
                every { interaction } returns mockk(relaxed = true)
            }
        }
        val interaction = mockk<ButtonInteraction> {
            every { data } returns mockk {
                every { guildId.value } returns Snowflake(123)
            }
            every { kord } returns mockKord
            every { guildLocale } returns Locale.BULGARIAN
            every { component } returns mockk {
                every { customId } returns ComponentsEnum.PLAYER_PAUSE.customId
            }
            every { message } returns mockk(relaxed = true)
        }
        val guildLavaPlayerService: GuildLavaPlayerService = mockk {
            every { getQueue() } returns listOf()
            every { getCurrentPlayingTrack() } returns createMockTrackBO()
            every { isPaused() } returns false
            justRun { pause() }
        }

        coEvery { guildQueueService.getLavaPlayerService(any()) } returns guildLavaPlayerService
        coEvery { localizationService.getString(any(), any(), any()) } returns "TestMessage"
        coEvery { localizationService.getStringFormat(any(), any(), any(), *anyVararg()) } returns "Format"

        // When
        playerCommand.onInteract(interaction)

        // Then
        verify(exactly = 1) {
            guildLavaPlayerService.getQueue()
            guildLavaPlayerService.getCurrentPlayingTrack()
            guildLavaPlayerService.isPaused()
            guildLavaPlayerService.pause()
        }
    }

    @Test
    fun `Given lyrics interaction with no service When onInteract Then respond ephemeral`() = runTest {
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
        val interaction = mockk<ButtonInteraction> {
            every { id } returns Snowflake(456)
            every { data } returns mockk {
                every { guildId.value } returns null
            }
            every { kord } returns mockKord
            every { guildLocale } returns Locale.BULGARIAN
            every { component } returns mockk {
                every { customId } returns ComponentsEnum.PLAYER_LYRICS.customId
            }
            every { message } returns mockk(relaxed = true)
            every { applicationId } returns Snowflake(456)
            every { token } returns "testToken"
        }

        // When
        playerCommand.onInteract(interaction)

        // Then
        coVerify(exactly = 0) { guildQueueService.getLavaPlayerService(any()) }
    }

    @Test
    fun `Given lyrics interaction with existing message When onInteract Then toggle off lyrics`() = runTest {
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
        val existingMsg = mockk<Message>(relaxed = true)
        val guildLavaPlayerService: GuildLavaPlayerService = mockk {
            every { getLyricsMessage() } returns existingMsg
            justRun { clearLyricsMessage() }
        }
        val interaction = mockk<ButtonInteraction> {
            every { id } returns Snowflake(456)
            every { data } returns mockk {
                every { guildId.value } returns Snowflake(123)
            }
            every { kord } returns mockKord
            every { guildLocale } returns Locale.BULGARIAN
            every { component } returns mockk {
                every { customId } returns ComponentsEnum.PLAYER_LYRICS.customId
            }
            every { message } returns mockk(relaxed = true)
            every { applicationId } returns Snowflake(456)
            every { token } returns "testToken"
        }
        coEvery { guildQueueService.getLavaPlayerService(any()) } returns guildLavaPlayerService
        coEvery { localizationService.getString(any(), any(), any()) } returns "TestMessage"

        // When
        playerCommand.onInteract(interaction)

        // Then
        verify(exactly = 1) { guildLavaPlayerService.getLyricsMessage() }
        coVerify(exactly = 1) { existingMsg.delete() }
        verify(exactly = 1) { guildLavaPlayerService.clearLyricsMessage() }
    }

    @Test
    fun `Given lyrics interaction with no track When onInteract Then respond ephemeral with no track error`() = runTest {
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
        val guildLavaPlayerService: GuildLavaPlayerService = mockk {
            every { getLyricsMessage() } returns null
            every { getCurrentPlayingTrack() } returns null
        }
        val interaction = mockk<ButtonInteraction> {
            every { id } returns Snowflake(456)
            every { data } returns mockk {
                every { guildId.value } returns Snowflake(123)
            }
            every { kord } returns mockKord
            every { guildLocale } returns Locale.BULGARIAN
            every { component } returns mockk {
                every { customId } returns ComponentsEnum.PLAYER_LYRICS.customId
            }
            every { message } returns mockk(relaxed = true)
            every { applicationId } returns Snowflake(456)
            every { token } returns "testToken"
        }
        coEvery { guildQueueService.getLavaPlayerService(any()) } returns guildLavaPlayerService
        coEvery { localizationService.getString(any(), any(), any()) } returns "TestMessage"

        // When
        playerCommand.onInteract(interaction)

        // Then
        verify(exactly = 1) { guildLavaPlayerService.getLyricsMessage() }
        verify(exactly = 1) { guildLavaPlayerService.getCurrentPlayingTrack() }
        coVerify(exactly = 0) { guildLavaPlayerService.saveLyricsMessage(any()) }
    }

    @Test
    fun `Given lyrics interaction with lyrics found When onInteract Then respond with lyrics`() = runTest {
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
        val guildLavaPlayerService: GuildLavaPlayerService = mockk {
            every { getLyricsMessage() } returns null
            every { getCurrentPlayingTrack() } returns createMockTrackBO()
            justRun { saveLyricsMessage(any()) }
        }
        val interaction = mockk<ButtonInteraction> {
            every { id } returns Snowflake(456)
            every { data } returns mockk {
                every { guildId.value } returns Snowflake(123)
            }
            every { kord } returns mockKord
            every { guildLocale } returns Locale.BULGARIAN
            every { component } returns mockk {
                every { customId } returns ComponentsEnum.PLAYER_LYRICS.customId
            }
            every { message } returns mockk(relaxed = true)
            every { applicationId } returns Snowflake(456)
            every { token } returns "testToken"
        }
        coEvery { guildQueueService.getLavaPlayerService(any()) } returns guildLavaPlayerService
        coEvery { localizationService.getString(any(), any(), any()) } returns "TestMessage"
        coEvery { localizationService.getStringFormat(any(), any(), any(), *anyVararg()) } returns "Lyrics Title"
        every { lyricsService.getFormattedLyrics(any()) } returns "Some lyrics"

        val lyricsMsg = mockk<Message>(relaxed = true)
        coEvery { interaction.getOriginalInteractionResponse() } returns lyricsMsg

        // When
        playerCommand.onInteract(interaction)

        // Then
        verify(exactly = 1) { guildLavaPlayerService.getLyricsMessage() }
        verify(exactly = 1) { guildLavaPlayerService.getCurrentPlayingTrack() }
        verify(exactly = 1) { lyricsService.getFormattedLyrics(any()) }
        coVerify(exactly = 1) { guildLavaPlayerService.saveLyricsMessage(lyricsMsg) }
    }

    @Test
    fun `Given lyrics interaction with no lyrics found When onInteract Then respond ephemeral with not found`() = runTest {
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
        val guildLavaPlayerService: GuildLavaPlayerService = mockk {
            every { getLyricsMessage() } returns null
            every { getCurrentPlayingTrack() } returns createMockTrackBO()
        }
        val interaction = mockk<ButtonInteraction> {
            every { id } returns Snowflake(456)
            every { data } returns mockk {
                every { guildId.value } returns Snowflake(123)
            }
            every { kord } returns mockKord
            every { guildLocale } returns Locale.BULGARIAN
            every { component } returns mockk {
                every { customId } returns ComponentsEnum.PLAYER_LYRICS.customId
            }
            every { message } returns mockk(relaxed = true)
            every { applicationId } returns Snowflake(456)
            every { token } returns "testToken"
        }
        coEvery { guildQueueService.getLavaPlayerService(any()) } returns guildLavaPlayerService
        coEvery { localizationService.getString(any(), any(), any()) } returns "TestMessage"
        every { lyricsService.getFormattedLyrics(any()) } returns null

        // When
        playerCommand.onInteract(interaction)

        // Then
        verify(exactly = 1) { guildLavaPlayerService.getLyricsMessage() }
        verify(exactly = 1) { guildLavaPlayerService.getCurrentPlayingTrack() }
        verify(exactly = 1) { lyricsService.getFormattedLyrics(any()) }
        coVerify(exactly = 0) { guildLavaPlayerService.saveLyricsMessage(any()) }
    }
}
