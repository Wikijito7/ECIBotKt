package spike.jdadave

import dev.minn.jda.ktx.events.await
import moe.kyokobot.libdave.NativeDaveFactory
import moe.kyokobot.libdave.jda.LDJDADaveSessionFactory
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.audio.AudioModuleConfig
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.requests.GatewayIntent

/**
 * Compile-only API canary for the JDA 6.7.0 / jda-ktx 0.14.2 / libdave 0.2.0 matrix.
 *
 * This object is not on the runtime path. It exists so a plain `compileKotlin` still
 * proves that the published `adapter-jda` factory is accepted by JDA 6.7.0's
 * `AudioModuleConfig` and that a jda-ktx coroutine extension resolves against the
 * forced JDA version. The executable probe lives in [MainKt].
 */
@Suppress("unused")
object DaveJdaCompileProbe {

    /**
     * Proves that the published `adapter-jda` session factory is accepted by JDA 6.7.0's
     * `AudioModuleConfig.withDaveSessionFactory(DaveSessionFactory)`.
     */
    fun builder(token: String): JDABuilder =
        JDABuilder.createLight(token, GatewayIntent.GUILD_VOICE_STATES)
            .setAudioModuleConfig(
                AudioModuleConfig()
                    .withDaveSessionFactory(LDJDADaveSessionFactory(NativeDaveFactory())),
            )

    /**
     * Proves that at least one jda-ktx coroutine extension is resolvable and usable
     * against the explicit JDA 6.7.0 artifact.
     */
    suspend fun awaitReady(jda: JDA): ReadyEvent = jda.await()
}
