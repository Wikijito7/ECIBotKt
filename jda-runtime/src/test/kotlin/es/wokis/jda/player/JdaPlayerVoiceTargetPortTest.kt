package es.wokis.jda.player

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.player.PlayerVoiceTarget
import es.wokis.commands.player.PlayerVoiceTargetPort
import es.wokis.commands.player.PlayerVoiceTargetResult
import es.wokis.commands.player.PlayerVoiceUnavailableReason
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.GuildVoiceState
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/**
 * Gateway resolution contract of [JdaPlayerVoiceTargetPort].
 *
 * The port must answer the two neutral unavailability reasons instead of throwing, and must still
 * refuse an id JDA cannot represent rather than truncating it.
 */
class JdaPlayerVoiceTargetPortTest {

    private val jda: JDA = mockk()
    private val guild: Guild = mockk()
    private val member: Member = mockk()
    private val voiceState: GuildVoiceState = mockk()
    private val channelUnion: AudioChannelUnion = mockk<AudioChannelUnion>(
        moreInterfaces = arrayOf(VoiceChannel::class),
    )
    private val voiceChannel: VoiceChannel = channelUnion as VoiceChannel
    private val port: PlayerVoiceTargetPort = JdaPlayerVoiceTargetPort(jda)

    @Test
    fun `Given a member in a guild voice channel When the target resolves Then report the channel id and name`() =
        runTest {
            givenGuildAndMember()

            val result = port.resolve(context())

            assertEquals(
                PlayerVoiceTargetResult.Ready(
                    PlayerVoiceTarget(channelId = ChannelId(VOICE_CHANNEL_ID.toULong()), channelName = "General"),
                ),
                result,
            )
        }

    @Test
    fun `Given a direct message context When the target resolves Then report no guild without a lookup`() = runTest {
        val result = port.resolve(context(guildId = null))

        assertEquals(
            PlayerVoiceTargetResult.Unavailable(PlayerVoiceUnavailableReason.NO_GUILD),
            result,
        )
        verify(exactly = 0) { jda.getGuildById(any<Long>()) }
    }

    @Test
    fun `Given an unknown guild When the target resolves Then report no guild`() = runTest {
        every { jda.getGuildById(GUILD_ID) } returns null

        val result = port.resolve(context())

        assertEquals(
            PlayerVoiceTargetResult.Unavailable(PlayerVoiceUnavailableReason.NO_GUILD),
            result,
        )
    }

    @Test
    fun `Given a caller that is not a guild member When the target resolves Then report no voice channel`() = runTest {
        every { jda.getGuildById(GUILD_ID) } returns guild
        every { guild.getMemberById(USER_ID) } returns null

        val result = port.resolve(context())

        assertEquals(
            PlayerVoiceTargetResult.Unavailable(PlayerVoiceUnavailableReason.NO_VOICE_CHANNEL),
            result,
        )
    }

    @Test
    fun `Given a member outside voice When the target resolves Then report no voice channel`() = runTest {
        givenGuildAndMember()
        every { voiceState.channel } returns null

        val result = port.resolve(context())

        assertEquals(
            PlayerVoiceTargetResult.Unavailable(PlayerVoiceUnavailableReason.NO_VOICE_CHANNEL),
            result,
        )
    }

    @Test
    fun `Given a non voice channel When the target resolves Then report no voice channel`() = runTest {
        givenGuildAndMember()
        every { voiceState.channel } returns mockk<AudioChannelUnion>()

        val result = port.resolve(context())

        assertEquals(
            PlayerVoiceTargetResult.Unavailable(PlayerVoiceUnavailableReason.NO_VOICE_CHANNEL),
            result,
        )
    }

    @Test
    fun `Given an out of range user id When the target resolves Then fail without echoing the invalid id`() = runTest {
        // Given: a user id that cannot fit JDA's signed 64-bit range.
        every { jda.getGuildById(GUILD_ID) } returns guild
        val oversizedUserId = UserId(ULong.MAX_VALUE)

        val error = assertFailsWith<IllegalArgumentException> {
            port.resolve(context(userId = oversizedUserId))
        }

        // Then: the failure stays generic and never echoes the rejected snowflake.
        val message = error.message.orEmpty()
        assertEquals("User id does not fit in JDA's signed 64-bit id range", message)
        assertFalse(
            message.contains(oversizedUserId.value.toString()),
            "the failure message leaked the invalid user id: $message",
        )
        verify(exactly = 0) { guild.getMemberById(any<Long>()) }
    }

    @Test
    fun `Given an existing voice channel When its name is read Then report the current name`() = runTest {
        every { jda.getVoiceChannelById(VOICE_CHANNEL_ID) } returns voiceChannel
        every { voiceChannel.name } returns "Renamed"

        assertEquals("Renamed", port.channelName(ChannelId(VOICE_CHANNEL_ID.toULong())))
    }

    @Test
    fun `Given a deleted voice channel When its name is read Then report null`() = runTest {
        every { jda.getVoiceChannelById(VOICE_CHANNEL_ID) } returns null

        assertNull(port.channelName(ChannelId(VOICE_CHANNEL_ID.toULong())))
    }

    @Test
    fun `Given an out of range channel id When its name is read Then report null without a lookup`() = runTest {
        assertNull(port.channelName(ChannelId(ULong.MAX_VALUE)))
        verify(exactly = 0) { jda.getVoiceChannelById(any<Long>()) }
    }

    private fun givenGuildAndMember() {
        every { jda.getGuildById(GUILD_ID) } returns guild
        every { guild.getMemberById(USER_ID) } returns member
        every { member.voiceState } returns voiceState
        every { voiceState.channel } returns channelUnion
        every { voiceChannel.idLong } returns VOICE_CHANNEL_ID
        every { voiceChannel.name } returns "General"
    }

    private fun context(
        guildId: GuildId? = GuildId(GUILD_ID.toULong()),
        userId: UserId = UserId(USER_ID.toULong()),
    ): CommandContext = CommandContext(
        commandName = "play",
        guildId = guildId,
        channelId = ChannelId(CHANNEL_ID.toULong()),
        userId = userId,
        locale = null,
    )

    private companion object {
        private const val GUILD_ID = 42L
        private const val CHANNEL_ID = 43L
        private const val USER_ID = 44L
        private const val VOICE_CHANNEL_ID = 99L
    }
}
