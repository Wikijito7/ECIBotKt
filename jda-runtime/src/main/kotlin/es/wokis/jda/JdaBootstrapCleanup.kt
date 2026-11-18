package es.wokis.jda

import es.wokis.jda.player.JdaPlayerRuntime
import es.wokis.jda.voice.disconnectVoiceConnections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.JDA
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

private val LOG = LoggerFactory.getLogger("es.wokis.jda.JdaBootstrapCleanup")

private const val PLAYER_RUNTIME_STEP = "player-runtime"
private const val VOICE_DISCONNECT_STEP = "voice-disconnect"
private const val ROUTER_STEP = "command-router"
private const val SHUTDOWN_HOOK_STEP = "shutdown-hook"
private const val JDA_SHUTDOWN_STEP = "jda-shutdown"
private const val SHUTDOWN_TIMEOUT_SECONDS = 10L

/**
 * One named cleanup action. Steps are independent: the bootstrap runs every step even when an
 * earlier one throws, so a failing disconnect cannot skip the later cleanup.
 */
internal class BootstrapCleanupStep(
    val name: String,
    private val action: () -> Unit,
) {

    /** Runs the cleanup action; any throwable is collected by [runBootstrapCleanup]. */
    fun run() = action()
}

/** Resources present at cleanup; a handle missing because of an early failure just removes its step. */
internal data class BootstrapCleanupTargets(
    val jda: JDA?,
    val router: AutoCloseable?,
    val shutdownHook: Thread?,
    /** Shared player engines and long-lived guild voice adapters, when created. */
    val playerRuntime: AutoCloseable? = null,
)

/**
 * Collected cleanup failures. [fatalFailure] (`Error` or cancellation) is rethrown by the bootstrap
 * instead of becoming only an exit code; [attachTo] adds the rest as suppressed exceptions so a
 * primary failure keeps its type and message.
 */
internal class BootstrapCleanupReport internal constructor(
    private val failures: List<Throwable>,
) {

    /** True when at least one cleanup step failed. */
    val failed: Boolean get() = failures.isNotEmpty()

    /** First cleanup failure in step order, or null when every step succeeded. */
    val failure: Throwable? get() = failures.firstOrNull()

    /** First fatal cleanup failure, or null when no step threw an `Error` or cancellation. */
    val fatalFailure: Throwable? get() = failures.firstOrNull(Throwable::isFatalBootstrapFailure)

    /** Attaches every cleanup failure to [primary] without duplicating an existing suppression. */
    fun attachTo(primary: Throwable) {
        failures.forEach { failure ->
            if (failure !== primary && !primary.suppressed.contains(failure)) {
                primary.addSuppressed(failure)
            }
        }
    }

    /**
     * Records [failure] before this report's failures, so a step-factory failure stays visible even
     * when the production steps still run and succeed.
     */
    fun withLeadingFailure(failure: Throwable): BootstrapCleanupReport =
        BootstrapCleanupReport(listOf(failure) + failures)
}

/**
 * Runs every [steps] entry independently, logging and recording failures instead of throwing. Fatal
 * failures are collected too, so the caller can rethrow them via [BootstrapCleanupReport.fatalFailure].
 */
internal fun runBootstrapCleanup(
    steps: List<BootstrapCleanupStep>,
): BootstrapCleanupReport {
    val failures = mutableListOf<Throwable>()

    steps.forEach { step ->
        try {
            step.run()
        } catch (error: Throwable) {
            failures += error
            LOG.warn(
                "Bootstrap cleanup step '{}' failed ({})",
                step.name,
                error.javaClass.simpleName,
            )
        }
    }

    return BootstrapCleanupReport(failures)
}

/**
 * Production cleanup steps in execution order. The voice disconnect runs before JDA shutdown so an
 * active connection closes while the client is alive; the client shuts down last with a bounded wait.
 */
internal fun defaultBootstrapCleanupSteps(
    targets: BootstrapCleanupTargets,
): List<BootstrapCleanupStep> = buildList {
    targets.playerRuntime?.let { runtime ->
        add(
            BootstrapCleanupStep(PLAYER_RUNTIME_STEP) {
                // Cleanup runs on the bootstrap/shutdown thread, never a JDA event or audio thread.
                // The production runtime needs an awaited, suspend teardown; the fallback keeps
                // lightweight test doubles working through AutoCloseable.
                if (runtime is JdaPlayerRuntime) {
                    runBlocking { runtime.shutdown() }
                } else {
                    runtime.close()
                }
            }
        )
    }
    targets.jda?.let { client ->
        add(BootstrapCleanupStep(VOICE_DISCONNECT_STEP) { disconnectVoiceConnections(client) })
    }
    targets.router?.let { router ->
        add(BootstrapCleanupStep(ROUTER_STEP) { router.close() })
    }
    targets.shutdownHook?.let { hook ->
        add(BootstrapCleanupStep(SHUTDOWN_HOOK_STEP) { removeShutdownHook(hook) })
    }
    targets.jda?.let { client ->
        add(BootstrapCleanupStep(JDA_SHUTDOWN_STEP) { shutdownJda(client) })
    }
}

/**
 * Removes [hook] as a best-effort cleanup step. [IllegalStateException] means the JVM is already
 * shutting down, which is benign (the process is stopping and the hook needs no removal); any other
 * failure is a real cleanup failure and is rethrown.
 */
internal fun removeShutdownHook(hook: Thread) {
    try {
        Runtime.getRuntime().removeShutdownHook(hook)
    } catch (_: IllegalStateException) {
        LOG.info("The JVM is already shutting down; the shutdown hook needs no removal")
    }
}

/**
 * Shuts JDA down with a wait capped by [timeoutSeconds], forcing `shutdownNow()` rather than blocking
 * process exit indefinitely. The timeout is injectable so the timeout path is testable.
 */
internal fun shutdownJda(
    client: JDA,
    timeoutSeconds: Long = SHUTDOWN_TIMEOUT_SECONDS,
) {
    if (client.status == JDA.Status.SHUTDOWN) {
        return
    }
    client.shutdown()
    if (!client.awaitShutdown(timeoutSeconds, TimeUnit.SECONDS)) {
        client.shutdownNow()
    }
}

/** True for failures that must not be converted into an ordinary exit code. */
internal fun Throwable.isFatalBootstrapFailure(): Boolean =
    this is Error || this is CancellationException
