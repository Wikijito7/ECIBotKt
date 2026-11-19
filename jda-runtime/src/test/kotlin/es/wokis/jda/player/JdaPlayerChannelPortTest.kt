package es.wokis.jda.player

import es.wokis.commands.player.PlayerChannelPort
import es.wokis.commands.player.PlayerChannelSetup
import es.wokis.domain.model.ChannelId
import es.wokis.message.MessageView
import es.wokis.player.PlayerMessageId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.exceptions.MissingAccessException
import net.dv8tion.jda.api.exceptions.ObfuscatedChannelException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/**
 * Delegation contract of [JdaPlayerChannelPort].
 *
 * The REST work stays in [JdaPlayerChannelService]; the port only translates its nullable result
 * into the neutral [PlayerChannelSetup] the coordinator acts on, carrying the handle ids through.
 */
class JdaPlayerChannelPortTest {

    private val guild: Guild = mockk {
        every { name } returns "Jungle"
    }
    private val service: JdaPlayerChannelService = mockk()
    private val port: PlayerChannelPort = JdaPlayerChannelPort(guild, service)
    private val view = MessageView(content = "player")

    /** The obfuscated/missing-access exceptions read these members when they are constructed. */
    private val hiddenChannel: TextChannel = mockk {
        every { guild } returns mockk { every { idLong } returns HIDDEN_GUILD_ID }
        every { idLong } returns HIDDEN_CHANNEL_ID
        every { type } returns ChannelType.TEXT
    }

    @Test
    fun `Given a freshly created channel When the view is set up Then report Created with both ids`() = runTest {
        coEvery { service.setUp(guild, view) } returns result(isNewChannel = true)

        val setup = port.setUp(view)

        assertEquals(PlayerChannelSetup.Created(channelId, messageId), setup)
        coVerify(exactly = 1) { service.setUp(guild, view) }
    }

    @Test
    fun `Given a reused channel When the view is set up Then report Reused with both ids`() = runTest {
        coEvery { service.setUp(guild, view) } returns result(isNewChannel = false)

        val setup = port.setUp(view)

        assertEquals(PlayerChannelSetup.Reused(channelId, messageId), setup)
        coVerify(exactly = 1) { service.setUp(guild, view) }
    }

    @Test
    fun `Given a channel that cannot be found or written When the view is set up Then report Failed`() = runTest {
        coEvery { service.setUp(guild, view) } returns null

        val setup = port.setUp(view)

        assertEquals(PlayerChannelSetup.Failed, setup)
    }

    @Test
    fun `Given a service that fails unexpectedly When the view is set up Then let the failure escape`() = runTest {
        coEvery { service.setUp(guild, view) } throws IllegalStateException("rest boom")

        assertFailsWith<IllegalStateException> { port.setUp(view) }
    }

    @Test
    fun `Given a cancelled setup When the view is set up Then rethrow the cancellation`() = runTest {
        coEvery { service.setUp(guild, view) } throws CancellationException("shutdown")

        assertFailsWith<CancellationException> { port.setUp(view) }
    }

    @Test
    fun `Given an obfuscated player channel When the view is set up Then report Failed without ids`() = runTest {
        coEvery { service.setUp(guild, view) } throws ObfuscatedChannelException(hiddenChannel)

        val setup = port.setUp(view)

        assertEquals(PlayerChannelSetup.Failed, setup)
        coVerify(exactly = 1) { service.setUp(guild, view) }
    }

    @Test
    fun `Given a missing access failure When the view is set up Then report Failed without ids`() = runTest {
        coEvery { service.setUp(guild, view) } throws MissingAccessException(hiddenChannel, Permission.VIEW_CHANNEL)

        val setup = port.setUp(view)

        assertEquals(PlayerChannelSetup.Failed, setup)
        coVerify(exactly = 1) { service.setUp(guild, view) }
    }

    private fun result(isNewChannel: Boolean): JdaPlayerChannelResult = JdaPlayerChannelResult(
        channelId = channelId,
        messageId = messageId,
        isNewChannel = isNewChannel,
    )

    private companion object {
        private const val PLAYER_CHANNEL_ID = 77L
        private const val PLAYER_MESSAGE_ID = 900L
        private const val HIDDEN_GUILD_ID = 42L
        private const val HIDDEN_CHANNEL_ID = 55L

        private val channelId = ChannelId(PLAYER_CHANNEL_ID.toULong())
        private val messageId = PlayerMessageId(PLAYER_MESSAGE_ID.toULong())
    }
}
