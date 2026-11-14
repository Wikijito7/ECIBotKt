package es.wokis.jda.voice

import moe.kyokobot.libdave.DaveFactory
import moe.kyokobot.libdave.NativeDaveFactory
import moe.kyokobot.libdave.jda.LDJDADaveSessionFactory
import net.dv8tion.jda.api.audio.AudioModuleConfig

/**
 * DAVE 0.2.0 wiring for the isolated JDA runtime.
 *
 * This is the only production code that knows the libdave modules and their JDA adapter.
 * The native library and JNI implementation are confined to `:jda-runtime`; no libdave type
 * reaches `:core`.
 */
internal object DaveAudioModule {

    /**
     * Loads the DAVE JNI native and checks the negotiated protocol version before any gateway
     * login.
     *
     * There is deliberately no passthrough fallback: a missing native or a non-positive
     * protocol version must stop the runtime instead of silently sending unencrypted audio.
     *
     * A DAVE native/preflight failure is not caught here: libdave 0.2.0's `ensureAvailable()` may
     * rethrow a native loading error or wrap it, so no single exception class is guaranteed for a
     * native loading error.
     *
     * @throws IllegalStateException when the native loads but reports no usable protocol.
     */
    fun requireNative(): NativeDaveFactory {
        NativeDaveFactory.ensureAvailable()
        val factory = NativeDaveFactory()
        requirePositiveProtocolVersion(factory.maxSupportedProtocolVersion())
        return factory
    }

    /** Validates a DAVE protocol version reported by [DaveFactory.maxSupportedProtocolVersion]. */
    fun requirePositiveProtocolVersion(version: Int): Int {
        check(version > 0) {
            "DAVE native reported maxSupportedProtocolVersion=$version; DAVE cannot be negotiated"
        }
        return version
    }

    /**
     * Builds the immutable JDA audio configuration for [daveFactory].
     *
     * `AudioModuleConfig` is immutable, so the returned config is the one that must be passed
     * to `JDABuilder.setAudioModuleConfig`.
     */
    fun audioModuleConfig(daveFactory: DaveFactory): AudioModuleConfig =
        AudioModuleConfig()
            .withDaveSessionFactory(LDJDADaveSessionFactory(daveFactory))
}
