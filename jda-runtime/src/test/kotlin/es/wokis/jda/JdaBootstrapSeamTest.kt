package es.wokis.jda

import es.wokis.domain.model.GuildId
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.audio.AudioModuleConfig
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JdaBootstrapSeamTest {

    private val config = JdaRuntimeConfig(
        token = "fake-token",
        guildId = GuildId(987654321uL),
    )

    @Test
    fun `Given a DAVE preflight failure When the client is built Then the factory is never invoked`() {
        // Given: a preflight that fails exactly like a missing native would, without loading it.
        val preflightError = UnsatisfiedLinkError("libdave native is not available")
        val preflight = DavePreflight { throw preflightError }
        var factoryCalls = 0
        val factory = JdaClientFactory { _, _ ->
            factoryCalls++
            error("a client must never be built after a failed DAVE preflight")
        }

        // When
        val thrown = assertThrows(UnsatisfiedLinkError::class.java) {
            buildGatewayClient(config, preflight, factory)
        }

        // Then: no JDABuilder.build and therefore no gateway login can have happened.
        assertSame(preflightError, thrown)
        assertEquals(0, factoryCalls)
    }

    @Test
    fun `Given a DAVE preflight failure When bootstrapping Then the runtime exits as failed without a client`() {
        // Given
        val preflight = DavePreflight { throw IllegalStateException("DAVE cannot be negotiated") }
        var factoryCalls = 0
        val factory = JdaClientFactory { _, _ ->
            factoryCalls++
            error("a client must never be built after a failed DAVE preflight")
        }

        // When
        val exitCode = runBootstrap(config, preflight, factory)

        // Then: EXIT_FAILURE, and no factory invocation means no build/login was attempted.
        assertEquals(1, exitCode)
        assertEquals(0, factoryCalls)
    }

    @Test
    fun `Given a valid preflight When the client is built Then the preflight runs once and its config reaches the factory`() {
        // Given
        val audioModuleConfig = AudioModuleConfig()
        val client = mockk<JDA>()
        val order = mutableListOf<String>()
        var preflightCalls = 0
        val preflight = DavePreflight {
            preflightCalls++
            order += "preflight"
            audioModuleConfig
        }
        var factoryCalls = 0
        var receivedConfig: AudioModuleConfig? = null
        val factory = JdaClientFactory { _, audio ->
            factoryCalls++
            order += "client"
            receivedConfig = audio
            client
        }

        // When
        val result = buildGatewayClient(config, preflight, factory)

        // Then
        assertSame(client, result)
        assertEquals(1, preflightCalls)
        assertEquals(1, factoryCalls)
        assertEquals(listOf("preflight", "client"), order, "the DAVE preflight must run before the client factory")
        assertSame(audioModuleConfig, receivedConfig)
    }

    @Test
    fun `Given the voice gateway intents When inspected Then only the non-privileged voice-state intent is enabled`() {
        // Then
        assertEquals(setOf(GatewayIntent.GUILD_VOICE_STATES), VOICE_GATEWAY_INTENTS)
        assertEquals(1, VOICE_GATEWAY_INTENTS.size)

        val privileged = setOf(
            GatewayIntent.GUILD_MEMBERS,
            GatewayIntent.GUILD_PRESENCES,
            GatewayIntent.MESSAGE_CONTENT,
        )
        assertTrue(
            VOICE_GATEWAY_INTENTS.none(privileged::contains),
            "the isolated runtime must not request a privileged intent",
        )
    }

    @Test
    fun `Given the default factory When creating a client Then it configures only the voice cache and voice member policy`() {
        // Given: static mocking keeps the test offline and never loads the native DAVE modules.
        val builder = mockk<JDABuilder>()
        val client = mockk<JDA>()
        val audioModuleConfig = AudioModuleConfig()

        mockkStatic(JDABuilder::class)
        try {
            every { JDABuilder.createLight(any<String>(), any<Collection<GatewayIntent>>()) } returns builder
            every { builder.enableCache(any(), *anyVararg()) } returns builder
            every { builder.setMemberCachePolicy(any()) } returns builder
            every { builder.setAudioModuleConfig(any()) } returns builder
            every { builder.build() } returns client

            // When
            val result = DefaultJdaClientFactory.create(config, audioModuleConfig)

            // Then
            assertSame(client, result)
            verify(exactly = 1) { JDABuilder.createLight(config.token, VOICE_GATEWAY_INTENTS) }
            verify(exactly = 1) { builder.enableCache(CacheFlag.VOICE_STATE) }
            verify(exactly = 1) { builder.setMemberCachePolicy(MemberCachePolicy.VOICE) }
            verify(exactly = 1) { builder.setAudioModuleConfig(audioModuleConfig) }
            verify(exactly = 1) { builder.build() }
        } finally {
            unmockkStatic(JDABuilder::class)
        }
    }
}
