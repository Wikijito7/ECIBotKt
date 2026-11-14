package es.wokis.jda

import es.wokis.jda.voice.DaveAudioModule
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.audio.AudioModuleConfig
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag

/**
 * Narrow, injectable DAVE preflight seam for the isolated bootstrap.
 *
 * Production uses [NativeDavePreflight], which performs the native load, creates the 0.2.0
 * factory, validates its protocol version and builds the immutable [AudioModuleConfig]. Tests
 * can substitute a failing or recording implementation without loading a native library, and
 * the gateway client is only created after this call returns successfully.
 */
internal fun interface DavePreflight {

    /**
     * Performs the DAVE preflight and returns the immutable JDA audio configuration.
     *
     * Implementations must throw when DAVE cannot be used; the bootstrap must then abort before
     * any client is built or any gateway login is attempted.
     */
    fun audioModuleConfig(): AudioModuleConfig
}

/** Narrow, injectable JDA client-construction seam used after a successful DAVE preflight. */
internal fun interface JdaClientFactory {

    /** Builds the gateway client with the already prepared `audioModuleConfig`. */
    fun create(config: JdaRuntimeConfig, audioModuleConfig: AudioModuleConfig): JDA
}

/** Production DAVE preflight: native load, protocol validation and immutable audio config. */
internal object NativeDavePreflight : DavePreflight {
    override fun audioModuleConfig(): AudioModuleConfig =
        DaveAudioModule.audioModuleConfig(DaveAudioModule.requireNative())
}

/**
 * Production client factory.
 *
 * Only the non-privileged `GUILD_VOICE_STATES` intent and the voice-state cache are enabled;
 * no privileged intent is ever requested. The preflight-produced [AudioModuleConfig] is passed
 * to the immutable builder chain before `JDABuilder.build`.
 */
internal object DefaultJdaClientFactory : JdaClientFactory {
    override fun create(config: JdaRuntimeConfig, audioModuleConfig: AudioModuleConfig): JDA =
        JDABuilder.createLight(config.token, VOICE_GATEWAY_INTENTS)
            .enableCache(CacheFlag.VOICE_STATE)
            .setMemberCachePolicy(MemberCachePolicy.VOICE)
            .setAudioModuleConfig(audioModuleConfig)
            .build()
}

/** Gateway intents enabled by the isolated runtime: voice states only, none privileged. */
internal val VOICE_GATEWAY_INTENTS: Set<GatewayIntent> = setOf(GatewayIntent.GUILD_VOICE_STATES)

/**
 * Runs the DAVE preflight and only then creates the gateway client.
 *
 * This is the single composition point that guarantees a failed preflight aborts before
 * [JdaClientFactory.create] (and therefore before `JDABuilder.build`/gateway login).
 */
internal fun buildGatewayClient(
    config: JdaRuntimeConfig,
    davePreflight: DavePreflight = NativeDavePreflight,
    clientFactory: JdaClientFactory = DefaultJdaClientFactory,
): JDA {
    val audioModuleConfig = davePreflight.audioModuleConfig()
    return clientFactory.create(config, audioModuleConfig)
}
