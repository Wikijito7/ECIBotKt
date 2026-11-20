package es.wokis.jda.player

import es.wokis.commands.player.NextTrackResult
import es.wokis.commands.player.PlayerActor
import es.wokis.commands.player.PlayerMessageCoordinator
import es.wokis.commands.player.PlayerQueueSnapshot
import es.wokis.commands.player.PlayerReconnectResult
import es.wokis.commands.player.PlayerVoiceTarget
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.model.PlayerSnapshot
import es.wokis.player.engine.model.PlayerTrack
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class JdaPlayerActionsPortTest {

    private val jda: JDA = mockk()
    private val guild: Guild = mockk()
    private val member: Member = mockk()
    private val voiceState: GuildVoiceState = mockk()
    private val channelUnion: AudioChannelUnion = mockk<AudioChannelUnion>(
        moreInterfaces = arrayOf(VoiceChannel::class),
    )
    private val voiceChannel: VoiceChannel = channelUnion as VoiceChannel
    private val runtime: JdaPlayerRuntime = mockk()
    private val engine: PlayerEngine = mockk()
    private val coordinator: PlayerMessageCoordinator = mockk()
    private val port = JdaPlayerActionsPort(jda, runtime)

    private fun givenGuildAndVoice() {
        every { jda.getGuildById(GUILD_ID) } returns guild
        every { guild.idLong } returns GUILD_ID
        every { guild.getMemberById(USER_ID) } returns member
        every { member.voiceState } returns voiceState
        every { voiceState.channel } returns channelUnion
        every { voiceChannel.idLong } returns VOICE_CHANNEL_ID
        every { voiceChannel.name } returns "General"
        every { runtime.engineFor(guild) } returns engine
        every { runtime.messagesFor(guild) } returns coordinator
    }

    /** The content commands announce first, then join the caller's channel, then load. */
    private fun givenContentCommandSeams() {
        justRun { coordinator.recordCommand(any(), any(), any(), any()) }
        coEvery { coordinator.connectVoice(any()) } returns Unit
    }

    private fun voiceTarget(): PlayerVoiceTarget = PlayerVoiceTarget(
        channelId = ChannelId(VOICE_CHANNEL_ID.toULong()),
        channelName = "General",
    )

    private fun givenUnknownGuild() {
        every { jda.getGuildById(GUILD_ID) } returns null
    }

    @Test
    fun `Given a caller in a guild voice channel When voice availability is asked Then report true`() = runTest {
        givenGuildAndVoice()

        assertTrue(port.isVoiceAvailable(actor()))
    }

    @Test
    fun `Given a reachable guild regardless of voice state When guild availability is asked Then report true`() = runTest {
        givenGuildAndVoice()
        assertTrue(port.isGuildAvailable(actor()))

        // Then: the caller's voice state is irrelevant, so /player may still render for a caller outside voice.
        every { voiceState.channel } returns null
        assertTrue(port.isGuildAvailable(actor()))
    }

    @Test
    fun `Given an unknown guild When guild availability is asked Then report false without touching the member cache`() = runTest {
        givenUnknownGuild()

        assertFalse(port.isGuildAvailable(actor()))
        verify(exactly = 0) { guild.getMemberById(any<Long>()) }
    }

    @Test
    fun `Given an unknown guild a missing member or a non voice channel When asked Then report false`() = runTest {
        givenUnknownGuild()
        assertFalse(port.isVoiceAvailable(actor()))

        givenGuildAndVoice()
        every { guild.getMemberById(USER_ID) } returns null
        assertFalse(port.isVoiceAvailable(actor()))

        every { guild.getMemberById(USER_ID) } returns member
        every { voiceState.channel } returns null
        assertFalse(port.isVoiceAvailable(actor()))
    }

    @Test
    fun `Given an active track When pause is requested Then pause the engine once`() = runTest {
        givenGuildAndVoice()
        every { engine.snapshot() } returns PlayerSnapshot(hasActiveTrack = true)
        coEvery { engine.pause() } returns true

        assertTrue(port.pause(actor()))
        coVerify(exactly = 1) { engine.pause() }
    }

    @Test
    fun `Given nothing playing When pause is requested Then report false without touching the engine`() = runTest {
        givenGuildAndVoice()
        every { engine.snapshot() } returns PlayerSnapshot(hasActiveTrack = false)

        assertFalse(port.pause(actor()))
        coVerify(exactly = 0) { engine.pause() }
    }

    @Test
    fun `Given a paused track When resume is requested Then resume the engine once`() = runTest {
        givenGuildAndVoice()
        every { engine.snapshot() } returns PlayerSnapshot(hasActiveTrack = true, paused = true)
        coEvery { engine.resume() } returns true

        assertTrue(port.resume(actor()))
        coVerify(exactly = 1) { engine.resume() }
    }

    @Test
    fun `Given an unreachable guild When playback is toggled or skipped Then report false without an engine`() = runTest {
        givenUnknownGuild()

        assertFalse(port.pause(actor()))
        assertFalse(port.resume(actor()))
        assertFalse(port.skip(actor()))
        assertFalse(port.shuffle(actor()))
        assertFalse(port.stop(actor()))
        verify(exactly = 0) { runtime.engineFor(any()) }
        coVerify(exactly = 0) { engine.skip() }
        coVerify(exactly = 0) { engine.shuffle() }
        coVerify(exactly = 0) { engine.stop() }
    }

    @Test
    fun `Given a reachable guild When skip shuffle and stop run Then each delegates exactly once`() = runTest {
        givenGuildAndVoice()
        coEvery { engine.skip() } returns true
        coEvery { engine.shuffle() } returns false
        coEvery { engine.stop() } returns true

        assertTrue(port.skip(actor()))
        assertFalse(port.shuffle(actor()))
        assertTrue(port.stop(actor()))

        coVerify(exactly = 1) { engine.skip() }
        coVerify(exactly = 1) { engine.shuffle() }
        coVerify(exactly = 1) { engine.stop() }
    }

    @Test
    fun `Given an unreachable guild When next asks to move a track Then fall back to the search result`() = runTest {
        givenUnknownGuild()

        assertEquals(NextTrackResult.NotInQueue, port.moveNext(actor(), "song"))
        verify(exactly = 0) { runtime.engineFor(any()) }
    }

    @Test
    fun `Given an empty queue When next asks to move a track Then report the empty queue`() = runTest {
        givenGuildAndVoice()
        every { engine.isQueueEmpty() } returns true

        assertEquals(NextTrackResult.QueueEmpty, port.moveNext(actor(), "song"))
        coVerify(exactly = 0) { engine.moveTrackToNext(any()) }
    }

    @Test
    fun `Given a matching queued track When next asks to move it Then report the moved display name`() = runTest {
        givenGuildAndVoice()
        every { engine.isQueueEmpty() } returns false
        coEvery { engine.moveTrackToNext("song") } returns track("Artist - Song")

        assertEquals(NextTrackResult.Moved("Artist - Song"), port.moveNext(actor(), "song"))
    }

    @Test
    fun `Given no queued match When next asks to move it Then report the search fallback`() = runTest {
        givenGuildAndVoice()
        every { engine.isQueueEmpty() } returns false
        coEvery { engine.moveTrackToNext("song") } returns null

        assertEquals(NextTrackResult.NotInQueue, port.moveNext(actor(), "song"))
    }

    @Test
    fun `Given a voice caller When next loads an input Then announce connect and load to front in order`() = runTest {
        givenGuildAndVoice()
        givenContentCommandSeams()
        coEvery { engine.loadAndPlay(any(), any()) } returns Unit

        port.loadNext(actor(GUILD_LOCALE), "https://example.invalid/track")

        coVerifyOrder {
            coordinator.recordCommand(
                ChannelId(CHANNEL_ID.toULong()),
                GuildId(GUILD_ID.toULong()),
                GUILD_LOCALE,
                "General",
            )
            coordinator.connectVoice(voiceTarget())
            engine.loadAndPlay("https://example.invalid/track", true)
        }
    }

    @Test
    fun `Given an unreachable guild When next loads an input Then load nothing`() = runTest {
        givenUnknownGuild()

        port.loadNext(actor(), "track")

        verify(exactly = 0) { runtime.messagesFor(any()) }
        verify(exactly = 0) { runtime.engineFor(any()) }
    }

    @Test
    fun `Given a voice caller When a sound plays Then announce in the command channel before the custom-name load`() = runTest {
        givenGuildAndVoice()
        givenContentCommandSeams()
        coEvery { engine.loadAndPlayMultipleWithCustomName(any(), any()) } returns Unit

        port.playSound(actor(GUILD_LOCALE), "rain")

        coVerifyOrder {
            coordinator.recordCommand(
                ChannelId(CHANNEL_ID.toULong()),
                GuildId(GUILD_ID.toULong()),
                GUILD_LOCALE,
                "General",
            )
            coordinator.connectVoice(voiceTarget())
            engine.loadAndPlayMultipleWithCustomName(listOf("./audio/rain.mp3"), "rain")
        }
    }

    @Test
    fun `Given an unreachable guild When a sound plays Then load nothing`() = runTest {
        givenUnknownGuild()

        port.playSound(actor(), "rain")

        verify(exactly = 0) { runtime.messagesFor(any()) }
        verify(exactly = 0) { runtime.engineFor(any()) }
    }

    @Test
    fun `Given a voice caller When tts loads Then announce connect and the encoded Kokoro load in order`() = runTest {
        givenGuildAndVoice()
        givenContentCommandSeams()
        coEvery { engine.loadAndPlayTts(any()) } returns Unit

        port.loadTts(actor(GUILD_LOCALE), "hola mundo & adiós")

        coVerifyOrder {
            coordinator.recordCommand(
                ChannelId(CHANNEL_ID.toULong()),
                GuildId(GUILD_ID.toULong()),
                GUILD_LOCALE,
                "General",
            )
            coordinator.connectVoice(voiceTarget())
            engine.loadAndPlayTts("kokoro://?text=hola%20mundo%20%26%20adi%C3%B3s")
        }
    }

    @Test
    fun `Given a voice caller When a radio plays Then announce connect and the stream load in order`() = runTest {
        givenGuildAndVoice()
        givenContentCommandSeams()
        coEvery { engine.playRadio(any(), any(), any()) } returns Unit

        port.playRadio(
            actor(GUILD_LOCALE),
            radioName = "Jungle FM",
            streamUrl = "https://radio.example/live",
            faviconUrl = "https://radio.example/icon.png",
        )

        coVerifyOrder {
            coordinator.recordCommand(
                ChannelId(CHANNEL_ID.toULong()),
                GuildId(GUILD_ID.toULong()),
                GUILD_LOCALE,
                "General",
            )
            coordinator.connectVoice(voiceTarget())
            engine.playRadio("Jungle FM", "https://radio.example/live", "https://radio.example/icon.png")
        }
    }

    @Test
    fun `Given a caller outside voice When a content command runs Then skip the connect seam and still load`() = runTest {
        givenGuildAndVoice()
        every { voiceState.channel } returns null
        givenContentCommandSeams()
        coEvery { engine.loadAndPlayMultipleWithCustomName(any(), any()) } returns Unit

        port.playSound(actor(), "rain")

        coVerify(exactly = 0) { coordinator.connectVoice(any()) }
        coVerify(exactly = 1) { engine.loadAndPlayMultipleWithCustomName(listOf("./audio/rain.mp3"), "rain") }
    }

    @Test
    fun `Given an unreachable guild When tts loads Then load nothing`() = runTest {
        givenUnknownGuild()

        port.loadTts(actor(), "hola")

        verify(exactly = 0) { runtime.messagesFor(any()) }
        verify(exactly = 0) { runtime.engineFor(any()) }
    }

    @Test
    fun `Given a reachable guild When disconnect runs Then delegate to the runtime once`() = runTest {
        givenGuildAndVoice()
        coEvery { runtime.disconnect(guild) } returns Unit

        port.disconnect(actor())

        coVerify(exactly = 1) { runtime.disconnect(guild) }
        verify(exactly = 0) { runtime.engineFor(any()) }
    }

    @Test
    fun `Given an unreachable guild When disconnect runs Then do nothing`() = runTest {
        givenUnknownGuild()

        port.disconnect(actor())

        coVerify(exactly = 0) { runtime.disconnect(any()) }
    }

    @Test
    fun `Given every reconnect outcome When reconnecting Then map it to the neutral result`() = runTest {
        givenGuildAndVoice()
        val expectations = mapOf(
            JdaPlayerReconnectResult.Reconnected to PlayerReconnectResult.RECONNECTED,
            JdaPlayerReconnectResult.NotConnected to PlayerReconnectResult.NOT_CONNECTED,
            JdaPlayerReconnectResult.ForeignSession to PlayerReconnectResult.FOREIGN_SESSION,
            JdaPlayerReconnectResult.InProgress to PlayerReconnectResult.IN_PROGRESS,
            JdaPlayerReconnectResult.TimedOut to PlayerReconnectResult.TIMED_OUT,
        )

        expectations.forEach { (jdaResult, neutral) ->
            coEvery { runtime.reconnect(guild, voiceChannel) } returns jdaResult

            assertEquals(neutral, port.reconnect(actor()), jdaResult::class.simpleName.toString())
        }
        coVerify(exactly = expectations.size) { runtime.reconnect(guild, voiceChannel) }
    }

    @Test
    fun `Given no session or no voice channel When reconnecting Then report not connected`() = runTest {
        givenUnknownGuild()
        assertEquals(PlayerReconnectResult.NOT_CONNECTED, port.reconnect(actor()))

        givenGuildAndVoice()
        every { voiceState.channel } returns null
        assertEquals(PlayerReconnectResult.NOT_CONNECTED, port.reconnect(actor()))
        coVerify(exactly = 0) { runtime.reconnect(any(), any()) }
    }

    @Test
    fun `Given a failing reconnect When reconnecting Then report a failed attempt`() = runTest {
        givenGuildAndVoice()
        coEvery { runtime.reconnect(guild, voiceChannel) } throws IllegalStateException("gateway refused")

        assertEquals(PlayerReconnectResult.FAILED, port.reconnect(actor()))
    }

    @Test
    fun `Given a cancelled reconnect When reconnecting Then rethrow the cancellation`() = runTest {
        givenGuildAndVoice()
        coEvery { runtime.reconnect(guild, voiceChannel) } throws CancellationException("cancelled")

        assertFailsWith<CancellationException> { port.reconnect(actor()) }
    }

    @Test
    fun `Given a reachable guild When the queue snapshot is asked Then include the guild name and engine state`() = runTest {
        givenGuildAndVoice()
        val snapshot = PlayerSnapshot(queue = listOf(track("queued")))
        every { guild.name } returns "Jungle"
        every { engine.snapshot() } returns snapshot

        val result = port.snapshot(actor())

        assertEquals(PlayerQueueSnapshot(guildName = "Jungle", snapshot = snapshot), result)
    }

    @Test
    fun `Given an unreachable guild When the queue snapshot is asked Then report null`() = runTest {
        givenUnknownGuild()

        assertNull(port.snapshot(actor()))
    }

    private fun actor(guildLocale: LocaleCode? = null): PlayerActor = PlayerActor(
        guildId = GuildId(GUILD_ID.toULong()),
        userId = UserId(USER_ID.toULong()),
        channelId = ChannelId(CHANNEL_ID.toULong()),
        guildLocale = guildLocale,
    )

    private fun track(displayName: String): PlayerTrack = PlayerTrack(
        identifier = displayName,
        title = displayName,
        author = "Artist",
        customName = displayName,
    )

    private companion object {
        const val GUILD_ID = 42L
        const val USER_ID = 44L
        const val CHANNEL_ID = 43L
        const val VOICE_CHANNEL_ID = 45L
        val GUILD_LOCALE = LocaleCode("es-ES")
    }
}
