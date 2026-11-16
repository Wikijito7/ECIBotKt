package es.wokis.jda.player

import es.wokis.commands.neutral.CommandContext
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.event.PlayerEvent
import es.wokis.player.engine.model.PlayerSnapshot
import es.wokis.player.engine.model.PlayerTrack
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.GuildVoiceState
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import kotlin.test.assertFailsWith

class JdaPlayerCommandControllerTest {

    @Test
    fun `Given a voice member When play runs Then load the input before connecting and report success`() = runTest {
        val fixture = Fixture()
        val order = mutableListOf<String>()
        coEvery { fixture.engine.loadAndPlay("track") } coAnswers {
            order += "load"
            fixture.presentation.value = JdaPlayerPresentation(revision = 1, activeTrack = true)
        }
        coEvery { fixture.runtime.connect(fixture.guild, fixture.channel) } coAnswers {
            order += "connect"
            true
        }

        val message = fixture.controller.play(context(), "  track  ")

        assertEquals(listOf("load", "connect"), order)
        assertEquals("Loaded and playing; the bot joined your voice channel.", message)
        coVerify(exactly = 1) { fixture.engine.loadAndPlay("track") }
    }

    @Test
    fun `Given a load that never becomes active When play runs Then time out without connecting`() = runTest {
        val fixture = Fixture()
        coJustRun { fixture.engine.loadAndPlay("track") }

        val message = fixture.controller.play(context(), "track")

        assertEquals(
            "The load did not produce an active track in time, so the bot did not connect.",
            message,
        )
        coVerify(exactly = 0) { fixture.runtime.connect(any(), any()) }
    }

    @Test
    fun `Given a provider load failure When play runs Then return a failure reply without connecting`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.engine.loadAndPlay("missing") } coAnswers {
            fixture.presentation.value = JdaPlayerPresentation(
                revision = 1,
                lastEvent = PlayerEvent.LoadFailed("not found"),
            )
        }

        val message = fixture.controller.play(context(), "missing")

        assertTrue(message.isNotBlank(), "a provider load failure must still produce a user-facing reply")
        coVerify(exactly = 0) { fixture.runtime.connect(any(), any()) }
    }

    @Test
    fun `Given a provider failure carrying a synthetic secret URL When play runs Then the reply does not echo it`() = runTest {
        // Given: a provider reason shaped like a credentialed stream URL that a log or chat reply must never leak.
        val fixture = Fixture()
        val providerReason =
            "https://user:provider-secret-sentinel@stream.example.invalid/audio?token=provider-secret-sentinel"
        coEvery { fixture.engine.loadAndPlay("track") } coAnswers {
            fixture.presentation.value = JdaPlayerPresentation(
                revision = 1,
                lastEvent = PlayerEvent.LoadFailed(providerReason),
            )
        }

        val message = fixture.controller.play(context(), "track")

        // Then: the user-facing reply must not carry the provider reason or any part of the URL.
        assertFalse(
            message.contains("provider-secret-sentinel"),
            "the user-facing reply leaked the provider secret: $message",
        )
        assertFalse(
            message.contains("stream.example.invalid"),
            "the user-facing reply leaked the provider endpoint: $message",
        )
        assertFalse(
            message.contains("https://"),
            "the user-facing reply leaked the provider URL: $message",
        )
        coVerify(exactly = 0) { fixture.runtime.connect(any(), any()) }
    }

    @Test
    fun `Given a voice session owned by another path When play runs Then report the refusal after loading`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.engine.loadAndPlay("track") } coAnswers {
            fixture.presentation.value = JdaPlayerPresentation(revision = 1, activeTrack = true)
        }
        coEvery { fixture.runtime.connect(fixture.guild, fixture.channel) } returns false

        val message = fixture.controller.play(context(), "track")

        assertEquals(
            "Playback started, but the voice session is owned by another voice path. " +
                "Disconnect it and use reconnect.",
            message,
        )
    }

    @Test
    fun `Given a connection exception When play runs Then report a safe permission message`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.engine.loadAndPlay("track") } coAnswers {
            fixture.presentation.value = JdaPlayerPresentation(revision = 1, activeTrack = true)
        }
        coEvery { fixture.runtime.connect(fixture.guild, fixture.channel) } throws IllegalStateException("gateway boom")

        val message = fixture.controller.play(context(), "track")

        assertEquals("The voice connection could not be opened; check the bot's voice permissions.", message)
    }

    @Test
    fun `Given an empty input When play runs Then reject it before resolving the guild`() = runTest {
        val fixture = Fixture()

        val message = fixture.controller.play(context(), "   ")

        assertEquals("Provide a track name, URL or playlist for the player.", message)
        verify(exactly = 0) { fixture.runtime.engineFor(any()) }
        verify(exactly = 0) { fixture.runtime.voiceAdapterFor(any()) }
    }

    @Test
    fun `Given a direct message or an unknown guild When play runs Then reject it safely`() = runTest {
        val fixture = Fixture()

        val directMessage = fixture.controller.play(context(guildId = null), "track")
        every { fixture.jda.getGuildById(GUILD_ID) } returns null
        val unknownGuild = fixture.controller.play(context(), "track")

        assertEquals(GUILD_ONLY_MESSAGE, directMessage)
        assertEquals(GUILD_ONLY_MESSAGE, unknownGuild)
        coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
    }

    @Test
    fun `Given a non member or a member outside voice When play runs Then reject without loading`() = runTest {
        val fixture = Fixture()
        every { fixture.guild.getMemberById(USER_ID) } returns null
        val nonMember = fixture.controller.play(context(), "track")

        every { fixture.guild.getMemberById(USER_ID) } returns fixture.member
        every { fixture.voiceState.channel } returns mockk<AudioChannelUnion>()
        val outsideVoice = fixture.controller.play(context(), "track")

        assertEquals(NOT_IN_VOICE_MESSAGE, nonMember)
        assertEquals(NOT_IN_VOICE_MESSAGE, outsideVoice)
        coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
    }

    @Test
    fun `Given an out of range user id When play runs Then fail without echoing the invalid id`() = runTest {
        // Given: a user id that cannot fit JDA's signed 64-bit range.
        val fixture = Fixture()
        val oversizedUserId = UserId(ULong.MAX_VALUE)

        val error = assertFailsWith<IllegalArgumentException> {
            fixture.controller.play(context(userId = oversizedUserId), "track")
        }

        // Then: the failure stays generic and never echoes the rejected snowflake.
        val message = error.message.orEmpty()
        assertEquals("User id does not fit in JDA's signed 64-bit id range", message)
        assertFalse(
            message.contains(oversizedUserId.value.toString()),
            "the failure message echoed the invalid user id: $message",
        )
        coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
        coVerify(exactly = 0) { fixture.runtime.connect(any(), any()) }
    }

    @Test
    fun `Given an unsupported action When control runs Then reject it before touching the guild`() = runTest {
        val fixture = Fixture()

        val message = fixture.controller.control(context(), "DANCE")

        assertEquals(
            "Unknown action. Use one of: queue, pause, resume, skip, stop, disconnect, reconnect.",
            message,
        )
        verify(exactly = 0) { fixture.runtime.engineFor(any()) }
        verify(exactly = 0) { fixture.jda.getGuildById(any<Long>()) }
    }

    @Test
    fun `Given a direct message or an unknown guild When control runs Then reject it safely`() = runTest {
        val fixture = Fixture()

        val directMessage = fixture.controller.control(context(guildId = null), "queue")
        every { fixture.jda.getGuildById(GUILD_ID) } returns null
        val unknownGuild = fixture.controller.control(context(), "pause")

        assertEquals(GUILD_ONLY_MESSAGE, directMessage)
        assertEquals(GUILD_ONLY_MESSAGE, unknownGuild)
        verify(exactly = 0) { fixture.runtime.engineFor(any()) }
    }

    @Test
    fun `Given an active queue When control asks for it Then describe the current track and pending items`() = runTest {
        val fixture = Fixture()
        every { fixture.engine.snapshot() } returns PlayerSnapshot(
            current = track("current", "Song"),
            queue = listOf(track("one", "One"), track("two", "Two")),
            paused = false,
            hasActiveTrack = true,
        )

        val message = fixture.controller.control(context(), "queue")

        assertEquals(
            "Current (playing): Artist - Song | queued: 2\n1. Artist - One\n2. Artist - Two",
            message,
        )
    }

    @Test
    fun `Given transport actions When control runs Then pause resume skip and stop delegate exactly`() = runTest {
        // Given: an active track so the controller-level guard lets the transport commands through.
        val fixture = Fixture()
        every { fixture.engine.snapshot() } returns activeSnapshot()
        coEvery { fixture.engine.pause() } returns true
        coEvery { fixture.engine.resume() } returns true
        coEvery { fixture.engine.skip() } returns true
        coJustRun { fixture.engine.stop() }

        val paused = fixture.controller.control(context(), "pause")
        val resumed = fixture.controller.control(context(), "resume")
        val skipped = fixture.controller.control(context(), "skip")
        val stopped = fixture.controller.control(context(), "stop")

        assertEquals("Playback paused.", paused)
        assertEquals("Playback resumed.", resumed)
        assertEquals("Skipped to the next track.", skipped)
        assertEquals("Queue cleared and playback stopped.", stopped)
        coVerify(exactly = 1) { fixture.engine.pause() }
        coVerify(exactly = 1) { fixture.engine.resume() }
        coVerify(exactly = 1) { fixture.engine.skip() }
        coVerify(exactly = 1) { fixture.engine.stop() }
    }

    @Test
    fun `Given an active track whose pause the engine refuses When control runs Then never report playback as paused`() = runTest {
        // Given: a track is active, so the guard passes, but the engine rejects the transition.
        val fixture = Fixture()
        coEvery { fixture.engine.pause() } returns false
        every { fixture.engine.snapshot() } returns activeSnapshot()

        val message = fixture.controller.control(context(), "pause")

        // Then: the reply may not claim the pause succeeded, and no voice side effect may imply it.
        assertNotEquals(
            PAUSED_SUCCESS_MESSAGE,
            message,
            "a refused pause must not be reported as a successful pause",
        )
        assertTrue(message.isNotBlank(), "a refused pause must still produce a user-facing reply")
        coVerify(exactly = 1) { fixture.engine.pause() }
        coVerify(exactly = 0) { fixture.engine.resume() }
        coVerify(exactly = 0) { fixture.engine.stop() }
        coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
        coVerify(exactly = 0) { fixture.runtime.connect(any(), any()) }
        coVerify(exactly = 0) { fixture.runtime.disconnect(any()) }
        verify(exactly = 0) { fixture.runtime.voiceAdapterFor(any()) }
    }

    @Test
    fun `Given an active track whose resume the engine refuses When control runs Then never report playback as resumed`() = runTest {
        // Given: a track is active, so the guard passes, but the engine rejects the transition.
        val fixture = Fixture()
        coEvery { fixture.engine.resume() } returns false
        every { fixture.engine.snapshot() } returns activeSnapshot()

        val message = fixture.controller.control(context(), "resume")

        // Then: the reply may not claim the resume succeeded, and no voice side effect may imply it.
        assertNotEquals(
            RESUMED_SUCCESS_MESSAGE,
            message,
            "a refused resume must not be reported as a successful resume",
        )
        assertTrue(message.isNotBlank(), "a refused resume must still produce a user-facing reply")
        coVerify(exactly = 1) { fixture.engine.resume() }
        coVerify(exactly = 0) { fixture.engine.pause() }
        coVerify(exactly = 0) { fixture.engine.stop() }
        coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
        coVerify(exactly = 0) { fixture.runtime.connect(any(), any()) }
        coVerify(exactly = 0) { fixture.runtime.disconnect(any()) }
        verify(exactly = 0) { fixture.runtime.voiceAdapterFor(any()) }
    }

    @Test
    fun `Given an idle player When control pauses Then answer with no active playback and never transition the engine`() = runTest {
        // Given: no current track, even though the engine would accept a pause.
        val fixture = Fixture()
        every { fixture.engine.snapshot() } returns PlayerSnapshot()
        coEvery { fixture.engine.pause() } returns true

        val message = fixture.controller.control(context(), "pause")

        // Then: the guard rejects the transition before the engine and never claims success.
        assertEquals(NO_ACTIVE_PLAYBACK_MESSAGE, message)
        assertNotEquals(PAUSED_SUCCESS_MESSAGE, message)
        assertNotEquals(PLAYBACK_STATE_UNCHANGED_MESSAGE, message)
        assertTrue(message.isNotBlank(), "an idle pause must still produce a user-facing reply")
        verify(exactly = 1) { fixture.engine.snapshot() }
        coVerify(exactly = 0) { fixture.engine.pause() }
        coVerify(exactly = 0) { fixture.engine.resume() }
        coVerify(exactly = 0) { fixture.engine.skip() }
        coVerify(exactly = 0) { fixture.engine.stop() }
        coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
        coVerify(exactly = 0) { fixture.runtime.connect(any(), any()) }
        coVerify(exactly = 0) { fixture.runtime.disconnect(any()) }
        verify(exactly = 0) { fixture.runtime.voiceAdapterFor(any()) }
    }

    @Test
    fun `Given an idle player When control resumes Then answer with no active playback and never transition the engine`() = runTest {
        // Given: no current track, even though the engine would accept a resume.
        val fixture = Fixture()
        every { fixture.engine.snapshot() } returns PlayerSnapshot()
        coEvery { fixture.engine.resume() } returns true

        val message = fixture.controller.control(context(), "resume")

        // Then: the guard rejects the transition before the engine and never claims success.
        assertEquals(NO_ACTIVE_PLAYBACK_MESSAGE, message)
        assertNotEquals(RESUMED_SUCCESS_MESSAGE, message)
        assertNotEquals(PLAYBACK_STATE_UNCHANGED_MESSAGE, message)
        assertTrue(message.isNotBlank(), "an idle resume must still produce a user-facing reply")
        verify(exactly = 1) { fixture.engine.snapshot() }
        coVerify(exactly = 0) { fixture.engine.resume() }
        coVerify(exactly = 0) { fixture.engine.pause() }
        coVerify(exactly = 0) { fixture.engine.skip() }
        coVerify(exactly = 0) { fixture.engine.stop() }
        coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
        coVerify(exactly = 0) { fixture.runtime.connect(any(), any()) }
        coVerify(exactly = 0) { fixture.runtime.disconnect(any()) }
        verify(exactly = 0) { fixture.runtime.voiceAdapterFor(any()) }
    }

    @Test
    fun `Given a skip with nothing playing When control runs Then report it`() = runTest {
        val fixture = Fixture()
        coEvery { fixture.engine.skip() } returns false

        val message = fixture.controller.control(context(), "skip")

        assertEquals("Nothing is playing right now.", message)
    }

    @Test
    fun `Given a disconnect When control runs Then stop the engine and close the voice connection`() = runTest {
        val fixture = Fixture()
        coJustRun { fixture.engine.stop() }
        coJustRun { fixture.runtime.disconnect(fixture.guild) }

        val message = fixture.controller.control(context(), "disconnect")

        assertEquals("Queue cleared and voice disconnected.", message)
        coVerify(exactly = 1) { fixture.engine.stop() }
        coVerify(exactly = 1) { fixture.runtime.disconnect(fixture.guild) }
        coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
    }

    @Test
    fun `Given an active track When reconnect runs Then reconnect without stopping or clearing it`() = runTest {
        val fixture = Fixture(started = true)
        coEvery { fixture.runtime.connect(fixture.guild, fixture.channel) } returns true
        every { fixture.engine.snapshot() } returns PlayerSnapshot(
            current = track("current", "Song"),
            queue = listOf(track("queued", "Queued")),
            hasActiveTrack = true,
        )

        val message = fixture.controller.control(context(), "reconnect")

        assertEquals(
            "Reconnected to your voice channel; the current track and queue were preserved.",
            message,
        )
        coVerify(exactly = 1) { fixture.runtime.connect(fixture.guild, fixture.channel) }
        coVerify(exactly = 0) { fixture.engine.stop() }
        coVerify(exactly = 0) { fixture.engine.loadAndPlay(any()) }
        coVerify(exactly = 0) { fixture.runtime.disconnect(any()) }
    }

    @Test
    fun `Given an idle player When reconnect runs Then report the reconnect without a track`() = runTest {
        val fixture = Fixture(started = true)
        coEvery { fixture.runtime.connect(fixture.guild, fixture.channel) } returns true
        every { fixture.engine.snapshot() } returns PlayerSnapshot()

        val message = fixture.controller.control(context(), "reconnect")

        assertEquals("Reconnected to your voice channel; nothing is playing right now.", message)
    }

    private fun context(
        guildId: GuildId? = GuildId(GUILD_ID.toULong()),
        userId: UserId = UserId(USER_ID.toULong()),
    ): CommandContext = CommandContext(
        commandName = JdaPlayerControlCommand.COMMAND_NAME,
        guildId = guildId,
        channelId = ChannelId(43uL),
        userId = userId,
        locale = LocaleCode("en-US"),
    )

    private fun track(identifier: String, title: String): PlayerTrack = PlayerTrack(
        identifier = identifier,
        title = title,
        author = "Artist",
    )

    private fun activeSnapshot(): PlayerSnapshot = PlayerSnapshot(
        current = track("current", "Song"),
        hasActiveTrack = true,
    )

    private fun voiceChannel(): AudioChannelUnion = Proxy.newProxyInstance(
        javaClass.classLoader,
        arrayOf(VoiceChannel::class.java, AudioChannelUnion::class.java),
    ) { proxy, method, arguments ->
        when (method.name) {
            "getIdLong" -> 42L
            "toString" -> "test-voice-channel"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === arguments?.firstOrNull()
            else -> null
        }
    } as AudioChannelUnion

    private inner class Fixture(started: Boolean = false) {
        val jda: JDA = mockk()
        val guild: Guild = mockk()
        val member: Member = mockk()
        val voiceState: GuildVoiceState = mockk()
        val runtime: JdaPlayerRuntime = mockk()
        val engine: PlayerEngine = mockk()
        val adapter: JdaGuildPlayerVoiceAdapter = mockk()
        val presentation = MutableStateFlow(
            JdaPlayerPresentation(
                revision = if (started) 1 else 0,
                activeTrack = started,
            ),
        )
        val channelUnion: AudioChannelUnion = voiceChannel()
        val channel: VoiceChannel = channelUnion as VoiceChannel
        val controller = JdaPlayerCommandController(jda, runtime)

        init {
            every { jda.getGuildById(GUILD_ID) } returns guild
            every { guild.getMemberById(USER_ID) } returns member
            every { member.voiceState } returns voiceState
            every { voiceState.channel } returns channelUnion
            every { runtime.engineFor(guild) } returns engine
            every { runtime.voiceAdapterFor(guild) } returns adapter
            every { adapter.presentation } returns presentation
        }
    }

    private companion object {
        private const val GUILD_ID = 42L
        private const val USER_ID = 44L
        private const val GUILD_ONLY_MESSAGE =
            "Run this in a guild, from the voice channel the bot should join."
        private const val NOT_IN_VOICE_MESSAGE =
            "Join a regular voice channel first; stage channels are not supported by the player."
        private const val PAUSED_SUCCESS_MESSAGE = "Playback paused."
        private const val RESUMED_SUCCESS_MESSAGE = "Playback resumed."
        private const val NO_ACTIVE_PLAYBACK_MESSAGE =
            "Nothing is playing right now, so there is no playback to pause or resume."
        private const val PLAYBACK_STATE_UNCHANGED_MESSAGE =
            "Playback state did not change; start a track first or check the current state."
    }
}
