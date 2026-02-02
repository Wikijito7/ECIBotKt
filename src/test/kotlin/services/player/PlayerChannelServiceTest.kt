package services.player

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.createTextChannel
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import es.wokis.services.player.PlayerChannelService
import io.mockk.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import mock.mockedGuildId
import mock.mockedKord
import mock.mockedMessage
import org.junit.jupiter.api.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerChannelServiceTest {

    private val playerChannelService = PlayerChannelService()

    @Test
    fun `Given existing player channel When sendPlayerMessage Then return success with isNewChannel false`() = runTest {
        // Given
        val existingChannel = mockk<TextChannel>(relaxed = true) {
            every { name } returns "player"
            every { id } returns Snowflake(456)
            every { messages } returns flowOf()
        }
        val guild = mockk<Guild> {
            every { id } returns mockedGuildId
            every { name } returns "TestGuild"
            every { channels } returns flowOf(existingChannel)
        }
        val interaction = mockk<ApplicationCommandInteraction> {
            every { data } returns mockk {
                every { guildId.value } returns mockedGuildId
            }
            every { kord } returns mockedKord
        }

        coEvery { mockedKord.getGuildOrNull(mockedGuildId) } returns guild

        // When
        val result = playerChannelService.sendPlayerMessage(interaction) { }

        // Then
        assertTrue(result.isSuccess)
        result.getOrNull()?.let { playerResult ->
            assertFalse(playerResult.isNewChannel)
        }
    }

    @Test
    @Ignore("check this test afterwards")
    fun `Given no player channel exists When sendPlayerMessage Then create channel and return success with isNewChannel true`() = runTest {
        // Given
        val otherChannel = mockk<TextChannel> {
            every { name } returns "general"
        }
        val guild = mockk<Guild>(relaxed = true) {
            every { id } returns mockedGuildId
            every { name } returns "TestGuild"
            every { channels } returns flowOf(otherChannel)
        }

        val interaction = mockk<ApplicationCommandInteraction> {
            every { data } returns mockk {
                every { guildId.value } returns mockedGuildId
            }
            every { kord } returns mockedKord
        }

        coEvery { mockedKord.getGuildOrNull(mockedGuildId) } returns guild

        // When
        val result = playerChannelService.sendPlayerMessage(interaction) { }

        // Then
        assertTrue(result.isSuccess)
        result.getOrNull()?.let { playerResult ->
            assertTrue(playerResult.isNewChannel)
        }
        coVerify(exactly = 1) { guild.createTextChannel("player", any()) }
    }

    @Test
    fun `Given null guildId When sendPlayerMessage Then return failure with IllegalStateException`() = runTest {
        // Given
        val interaction = mockk<ApplicationCommandInteraction> {
            every { data } returns mockk {
                every { guildId.value } returns null
            }
            every { kord } returns mockedKord
        }

        // When
        val result = playerChannelService.sendPlayerMessage(interaction) { }

        // Then
        assertTrue(result.isFailure)
        result.exceptionOrNull()?.let { error ->
            assertTrue(error is IllegalStateException)
            assertEquals("Guild ID is null", error.message)
        }
    }

    @Test
    fun `Given guild not found When sendPlayerMessage Then return failure with IllegalStateException`() = runTest {
        // Given
        val interaction = mockk<ApplicationCommandInteraction> {
            every { data } returns mockk {
                every { guildId.value } returns mockedGuildId
            }
            every { kord } returns mockedKord
        }

        coEvery { mockedKord.getGuildOrNull(mockedGuildId) } returns null

        // When
        val result = playerChannelService.sendPlayerMessage(interaction) { }

        // Then
        assertTrue(result.isFailure)
        result.exceptionOrNull()?.let { error ->
            assertTrue(error is IllegalStateException)
            assertEquals("Guild not found", error.message)
        }
    }

    @Test
    @Ignore("check this test afterwards")
    fun `Given channel creation fails When sendPlayerMessage Then return failure with IllegalStateException`() = runTest {
        // Given
        val otherChannel = mockk<TextChannel> {
            every { name } returns "general"
        }
        val guild = mockk<Guild>(relaxed = true) {
            every { id } returns mockedGuildId
            every { name } returns "TestGuild"
            every { channels } returns flowOf(otherChannel)
        }
        coEvery { guild.createTextChannel(any(), any()) } throws Exception("Permission denied")
        val interaction = mockk<ApplicationCommandInteraction> {
            every { data } returns mockk {
                every { guildId.value } returns mockedGuildId
            }
            every { kord } returns mockedKord
        }

        coEvery { mockedKord.getGuildOrNull(mockedGuildId) } returns guild

        // When
        val result = playerChannelService.sendPlayerMessage(interaction) { }

        // Then
        assertTrue(result.isFailure)
        result.exceptionOrNull()?.let { error ->
            assertTrue(error is IllegalStateException)
            assertTrue(error.message?.contains("Failed to find or create #player channel") == true)
        }
    }

    @Test
    fun `Given unexpected exception When sendPlayerMessage Then catch and return failure`() = runTest {
        // Given
        val interaction = mockk<ApplicationCommandInteraction> {
            every { data } throws RuntimeException("Unexpected error")
            every { kord } returns mockedKord
        }

        // When
        val result = playerChannelService.sendPlayerMessage(interaction) { }

        // Then
        assertTrue(result.isFailure)
        result.exceptionOrNull()?.let { error ->
            assertTrue(error is RuntimeException)
            assertEquals("Unexpected error", error.message)
        }
    }

    @Test
    fun `Given messages in channel When sendPlayerMessage Then clear messages via bulkDelete`() = runTest {
        // Given
        val messageId1 = Snowflake(100)
        val messageId2 = Snowflake(200)
        val message1 = mockk<Message> {
            every { id } returns messageId1
        }
        val message2 = mockk<Message> {
            every { id } returns messageId2
        }
        val existingChannel = mockk<TextChannel>(relaxed = true) {
            every { name } returns "player"
            every { id } returns Snowflake(456)
            every { messages } returns flowOf(message1, message2)
            coEvery { bulkDelete(any()) } returns Unit
        }
        val guild = mockk<Guild> {
            every { id } returns mockedGuildId
            every { name } returns "TestGuild"
            every { channels } returns flowOf(existingChannel)
        }
        val interaction = mockk<ApplicationCommandInteraction> {
            every { data } returns mockk {
                every { guildId.value } returns mockedGuildId
            }
            every { kord } returns mockedKord
        }

        coEvery { mockedKord.getGuildOrNull(mockedGuildId) } returns guild

        // When
        val result = playerChannelService.sendPlayerMessage(interaction) { }

        // Then
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { existingChannel.bulkDelete(any()) }
    }

    @Test
    fun `Given bulkDelete throws exception When sendPlayerMessage Then fallback to individual message delete`() = runTest {
        // Given
        val existingChannel = mockk<TextChannel>(relaxed = true) {
            every { kord } returns mockedKord
            every { name } returns "player"
            every { id } returns Snowflake(456)
            every { messages } returns flowOf(mockedMessage)
            coEvery { bulkDelete(any()) } throws Exception("Bulk delete failed")
        }
        val guild = mockk<Guild> {
            every { id } returns mockedGuildId
            every { name } returns "TestGuild"
            every { channels } returns flowOf(existingChannel)
        }
        val interaction = mockk<ApplicationCommandInteraction> {
            every { data } returns mockk {
                every { guildId.value } returns mockedGuildId
            }
            every { kord } returns mockedKord
        }

        coEvery { mockedKord.getGuildOrNull(mockedGuildId) } returns guild

        // When
        val result = playerChannelService.sendPlayerMessage(interaction) { }

        // Then
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { mockedMessage.delete() }
    }

    @Test
    fun `Given individual message delete fails When sendPlayerMessage Then log error and continue`() = runTest {
        // Given
        val messageId1 = Snowflake(100)
        val message1 = mockk<Message> {
            every { id } returns messageId1
            coEvery { delete() } throws Exception("Delete failed")
        }
        val existingChannel = mockk<TextChannel>(relaxed = true) {
            every { name } returns "player"
            every { id } returns Snowflake(456)
            every { messages } returns flowOf(message1)
            coEvery { bulkDelete(any()) } throws Exception("Bulk delete failed")
        }
        val guild = mockk<Guild> {
            every { id } returns mockedGuildId
            every { name } returns "TestGuild"
            every { channels } returns flowOf(existingChannel)
        }
        val interaction = mockk<ApplicationCommandInteraction> {
            every { data } returns mockk {
                every { guildId.value } returns mockedGuildId
            }
            every { kord } returns mockedKord
        }

        coEvery { mockedKord.getGuildOrNull(mockedGuildId) } returns guild

        // When
        val result = playerChannelService.sendPlayerMessage(interaction) { }

        // Then
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { message1.delete() }
    }

    @Test
    fun `Given channel with no messages When sendPlayerMessage Then skip clearing`() = runTest {
        // Given
        val existingChannel = mockk<TextChannel>(relaxed = true) {
            every { name } returns "player"
            every { id } returns Snowflake(456)
            every { messages } returns flowOf()
        }
        val guild = mockk<Guild> {
            every { id } returns mockedGuildId
            every { name } returns "TestGuild"
            every { channels } returns flowOf(existingChannel)
        }
        val interaction = mockk<ApplicationCommandInteraction> {
            every { data } returns mockk {
                every { guildId.value } returns mockedGuildId
            }
            every { kord } returns mockedKord
        }

        coEvery { mockedKord.getGuildOrNull(mockedGuildId) } returns guild

        // When
        val result = playerChannelService.sendPlayerMessage(interaction) { }

        // Then
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { existingChannel.bulkDelete(any()) }
    }

    @Test
    fun `Given more than 90 messages When sendPlayerMessage Then chunk messages for bulk delete`() = runTest {
        // Given
        // Create 100 messages to test chunking
        val mockedMessages = (1..100).map { index ->
            mockk<Message> {
                every { id } returns Snowflake(index.toLong())
            }
        }
        val existingChannel = mockk<TextChannel>(relaxed = true) {
            every { name } returns "player"
            every { id } returns Snowflake(456)
            every { messages } returns mockedMessages.asFlow()
            coEvery { bulkDelete(any()) } returns Unit
        }
        val guild = mockk<Guild> {
            every { id } returns mockedGuildId
            every { name } returns "TestGuild"
            every { channels } returns flowOf(existingChannel)
        }
        val interaction = mockk<ApplicationCommandInteraction> {
            every { data } returns mockk {
                every { guildId.value } returns mockedGuildId
            }
            every { kord } returns mockedKord
        }

        coEvery { mockedKord.getGuildOrNull(mockedGuildId) } returns guild

        // When
        val result = playerChannelService.sendPlayerMessage(interaction) { }

        // Then
        assertTrue(result.isSuccess)
        // Should call bulkDelete twice - once for 90 messages, once for 10 messages
        coVerify(exactly = 2) { existingChannel.bulkDelete(any()) }
    }
}
