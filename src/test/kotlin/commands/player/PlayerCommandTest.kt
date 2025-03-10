package commands.player

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.supplier.EntitySupplyStrategy
import es.wokis.commands.ComponentsEnum
import es.wokis.commands.player.PlayerCommand
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PlayerCommandTest {

    private val localizationService: LocalizationService = mockk()
    private val guildQueueService: GuildQueueService = mockk()

    private val playerCommand = PlayerCommand(
        localizationService = localizationService,
        guildQueueService = guildQueueService
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
            every { getCurrentPlayingTrack() } returns mockk {
                every { info } returns mockk()
                every { duration } returns 0L
                every { position } returns 0L
            }
            every { isPaused() } returns false
            justRun { savePlayerMessage(any()) }
        }

        coEvery { guildQueueService.getOrCreateLavaPlayerService(any()) } returns guildLavaPlayerService
        every { localizationService.getString(any(), any()) } returns "TestMessage"
        every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns "Format"

        // When
        playerCommand.onExecute(interaction, response)

        // Then
        verify(exactly = 1) {
            guildLavaPlayerService.getQueue()
            guildLavaPlayerService.getCurrentPlayingTrack()
            guildLavaPlayerService.isPaused()
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
            every { getCurrentPlayingTrack() } returns mockk {
                every { info } returns mockk()
                every { duration } returns 0L
                every { position } returns 0L
            }
            every { isPaused() } returns false
            justRun { resume() }
        }

        coEvery { guildQueueService.getLavaPlayerService(any()) } returns guildLavaPlayerService
        every { localizationService.getString(any(), any()) } returns "TestMessage"
        every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns "Format"

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
            every { getCurrentPlayingTrack() } returns mockk {
                every { info } returns mockk()
                every { duration } returns 0L
                every { position } returns 0L
            }
            every { isPaused() } returns false
            justRun { skip() }
        }

        coEvery { guildQueueService.getLavaPlayerService(any()) } returns guildLavaPlayerService
        every { localizationService.getString(any(), any()) } returns "TestMessage"
        every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns "Format"

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
            every { getCurrentPlayingTrack() } returns mockk {
                every { info } returns mockk()
                every { duration } returns 0L
                every { position } returns 0L
            }
            every { isPaused() } returns false
            justRun { shuffle() }
        }

        coEvery { guildQueueService.getLavaPlayerService(any()) } returns guildLavaPlayerService
        every { localizationService.getString(any(), any()) } returns "TestMessage"
        every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns "Format"

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
            every { getCurrentPlayingTrack() } returns mockk {
                every { info } returns mockk()
                every { duration } returns 0L
                every { position } returns 0L
            }
            every { isPaused() } returns false
            coJustRun { stop() }
        }

        coEvery { guildQueueService.getLavaPlayerService(any()) } returns guildLavaPlayerService
        every { localizationService.getString(any(), any()) } returns "TestMessage"
        every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns "Format"

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
            every { getCurrentPlayingTrack() } returns mockk {
                every { info } returns mockk()
                every { duration } returns 0L
                every { position } returns 0L
            }
            every { isPaused() } returns false
            justRun { pause() }
        }

        coEvery { guildQueueService.getLavaPlayerService(any()) } returns guildLavaPlayerService
        every { localizationService.getString(any(), any()) } returns "TestMessage"
        every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns "Format"

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
}
