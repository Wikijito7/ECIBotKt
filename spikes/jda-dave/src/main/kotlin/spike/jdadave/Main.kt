package spike.jdadave

import moe.kyokobot.libdave.NativeDaveFactory
import moe.kyokobot.libdave.jda.LDJDADaveSessionFactory
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.JDAInfo
import net.dv8tion.jda.api.audio.AudioModuleConfig
import net.dv8tion.jda.api.audio.AudioNatives
import net.dv8tion.jda.api.audio.factory.DefaultSendFactory
import net.dv8tion.jda.api.events.session.ShutdownEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.system.exitProcess

private val LOG = LoggerFactory.getLogger("spike.jdadave.Main")

/**
 * Test-only staging probe for JDA 6.7.0 + libdave 0.2.0 transport.
 *
 * It intentionally does not use Lavaplayer or the production application. It verifies
 * only that JDA can join a voice channel, negotiate DAVE with the native library, send a
 * generated 440 Hz tone, and disconnect cleanly.
 */
fun main() {
    LOG.info("JDA/DAVE staging transport probe (test-only, not production)")
    LOG.info(
        "[env] java={} jda={} os={} arch={}",
        System.getProperty("java.version", "unknown"),
        JDAInfo.VERSION,
        System.getProperty("os.name", "unknown"),
        System.getProperty("os.arch", "unknown"),
    )

    val config = try {
        SpikeConfig.fromEnv()
    } catch (t: ConfigException) {
        LOG.error("[config] {}", t.message)
        exitProcess(EXIT_CONFIG)
    }
    LOG.info("[config] {}", config)

    try {
        requireLinuxX64()
    } catch (t: Throwable) {
        LOG.error("[platform] {}", t.message)
        exitProcess(EXIT_PLATFORM)
    }

    val daveFactory = try {
        loadDaveNative()
    } catch (t: Throwable) {
        LOG.error("[dave] native library is unavailable: {}", t.message, t)
        exitProcess(EXIT_NATIVE)
    }

    try {
        ensureJdaAudioEncoder()
    } catch (t: Throwable) {
        LOG.error("[audio] JDA audio encoder is unavailable: {}", t.message, t)
        exitProcess(EXIT_NATIVE)
    }

    val jda = try {
        buildJda(config, daveFactory)
    } catch (t: Throwable) {
        LOG.error("[gateway] could not build JDA", t)
        exitProcess(EXIT_GATEWAY)
    }

    var exitCode = 0
    val firstRun = CompletableFuture<ProbeOutcome>()
    val probe = VoiceTestProbe(config.testGuildId, config.voiceConnectTimeoutMillis) { outcome ->
        when (outcome) {
            ProbeOutcome.Success -> LOG.info(
                "[probe] result: SUCCESS (transport checks only; still require DAVE Init/Transition " +
                    "evidence and manual audible confirmation; disconnect cause is not attributable " +
                    "via the public JDA API)",
            )

            is ProbeOutcome.Failure -> LOG.error("[probe] result: FAILURE - {}", outcome.userMessage)
        }
        if (!config.keepOnline) {
            firstRun.complete(outcome)
        }
    }
    jda.addEventListener(probe)
    jda.addEventListener(object : ListenerAdapter() {
        override fun onShutdown(event: ShutdownEvent) {
            LOG.warn("[gateway] shutdown event received")
            // Unblocks one-shot mode if JDA dies before /voice-test ever runs.
            firstRun.complete(ProbeOutcome.Failure("JDA shut down before the probe completed."))
        }
    })

    try {
        awaitGatewayReady(jda, config.readyTimeoutMillis)
        LOG.info(
            "[gateway] ready as {} (id={}) in {} guild(s)",
            jda.selfUser.name,
            jda.selfUser.id,
            jda.guilds.size,
        )

        registerVoiceTestCommand(jda, config.testGuildId)
        LOG.info(
            "[probe] waiting for /{} in guild {}; join a regular voice channel and invoke it",
            VoiceTestProbe.COMMAND_NAME,
            config.testGuildId,
        )

        if (config.keepOnline) {
            LOG.info("[probe] SPIKE_KEEP_ONLINE is enabled; stay online until the process is stopped")
            jda.awaitShutdown()
        } else {
            val outcome = firstRun.get()
            exitCode = if (outcome is ProbeOutcome.Success) EXIT_OK else EXIT_PROBE
            LOG.info("[probe] one-shot run finished; shutting down")
        }
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        LOG.error("[probe] interrupted", e)
        exitCode = EXIT_PROBE
    } catch (t: Throwable) {
        LOG.error("[probe] fatal error", t)
        exitCode = EXIT_PROBE
    } finally {
        probe.close()
        try {
            jda.shutdown()
            if (!jda.awaitShutdown(10, TimeUnit.SECONDS)) {
                LOG.warn("[gateway] shutdown did not complete in 10s; forcing")
                jda.shutdownNow()
            }
        } catch (t: Throwable) {
            LOG.warn("[gateway] error while shutting down", t)
        }
    }

    LOG.info("[probe] exiting with code {}", exitCode)
    exitProcess(exitCode)
}

private fun buildJda(config: SpikeConfig, daveFactory: NativeDaveFactory): JDA {
    LOG.info(
        "[gateway] logging in with intents=[{}], cache=[{}], audioSendFactory={}, daveSessionFactory={}",
        GatewayIntent.GUILD_VOICE_STATES,
        CacheFlag.VOICE_STATE,
        DefaultSendFactory::class.java.simpleName,
        LDJDADaveSessionFactory::class.java.simpleName,
    )

    return JDABuilder.createLight(config.botToken, GatewayIntent.GUILD_VOICE_STATES)
        .enableCache(CacheFlag.VOICE_STATE)
        .setAudioModuleConfig(
            AudioModuleConfig()
                .withAudioSendFactory(DefaultSendFactory())
                // daveFactory is the NativeDaveFactory preflighted above with
                // NativeDaveFactory.ensureAvailable() and maxSupportedProtocolVersion().
                .withDaveSessionFactory(LDJDADaveSessionFactory(daveFactory)),
        )
        .build()
}

private fun loadDaveNative(): NativeDaveFactory {
    LOG.info("[dave] loading native library for linux-x86-64")
    NativeDaveFactory.ensureAvailable()

    val factory = NativeDaveFactory()
    val maxProtocolVersion = factory.maxSupportedProtocolVersion()
    if (maxProtocolVersion <= 0) {
        throw IllegalStateException(
            "DAVE native loaded but reported maxSupportedProtocolVersion=$maxProtocolVersion; " +
                "DAVE cannot be negotiated",
        )
    }

    LOG.info("[dave] native loaded; maxSupportedProtocolVersion={}", maxProtocolVersion)
    return factory
}

/**
 * The tone is supplied as PCM, so JDA must load its Opus encoder. Without this check a
 * missing/broken opus native would silently turn every frame into a silence packet and
 * the probe could report a false success.
 */
private fun ensureJdaAudioEncoder() {
    LOG.info("[audio] loading JDA Opus encoder")
    if (!AudioNatives.ensureOpus()) {
        throw IllegalStateException(
            "JDA reported that its Opus encoder could not be loaded; PCM frames would be silent",
        )
    }
    LOG.info("[audio] JDA Opus encoder is available")
}

private fun registerVoiceTestCommand(jda: JDA, guildId: Long) {
    val guild = jda.getGuildById(guildId)
        ?: throw IllegalStateException(
            "The bot is not a member of guild $guildId. Invite the staging bot before running the probe.",
        )

    val command = Commands.slash(
        VoiceTestProbe.COMMAND_NAME,
        "JDA/DAVE staging probe: connect, play a 1.5 s 440 Hz tone, then disconnect",
    )
    val registered = guild.upsertCommand(command)
        .timeout(30, TimeUnit.SECONDS)
        .submit()
        .get(30, TimeUnit.SECONDS)

    LOG.info(
        "[command] guild-scoped /{} is ready (guild={}, commandId={})",
        registered.name,
        guildId,
        registered.id,
    )
}

/**
 * JDA's status wait is race-free and interruptible. It is run on a short-lived thread so
 * the probe can apply a wall-clock deadline without relying on event timing.
 */
private fun awaitGatewayReady(jda: JDA, timeoutMillis: Long) {
    val waiter = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "jda-ready-waiter").apply { isDaemon = true }
    }

    try {
        val future = waiter.submit {
            jda.awaitStatus(JDA.Status.CONNECTED, JDA.Status.SHUTDOWN)
        }
        try {
            future.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw IllegalStateException("JDA did not become ready within ${timeoutMillis}ms", e)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    } finally {
        waiter.shutdownNow()
    }
}

private fun requireLinuxX64() {
    val os = System.getProperty("os.name", "unknown").lowercase(Locale.ROOT)
    val arch = System.getProperty("os.arch", "unknown").lowercase(Locale.ROOT)
    val supportedArch = arch == "amd64" || arch == "x86_64"

    if (!os.contains("linux") || !supportedArch) {
        throw IllegalStateException(
            "This probe requires Linux x86_64 (found os=$os arch=$arch). " +
                "The bundled DAVE native is linux-x86-64 only, so do not run it on macOS or arm64.",
        )
    }
}

private const val EXIT_OK = 0
private const val EXIT_PROBE = 1
private const val EXIT_CONFIG = 2
private const val EXIT_PLATFORM = 2
private const val EXIT_NATIVE = 3
private const val EXIT_GATEWAY = 4
