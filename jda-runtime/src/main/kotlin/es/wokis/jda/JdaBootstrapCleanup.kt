package es.wokis.jda

import es.wokis.jda.voice.disconnectVoiceConnections
import kotlinx.coroutines.CancellationException
import net.dv8tion.jda.api.JDA
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

private val LOG = LoggerFactory.getLogger("es.wokis.jda.JdaBootstrapCleanup")

private const val VOICE_DISCONNECT_STEP = "voice-disconnect"
private const val ROUTER_STEP = "command-router"
private const val SHUTDOWN_HOOK_STEP = "shutdown-hook"
private const val JDA_SHUTDOWN_STEP = "jda-shutdown"
private const val SHUTDOWN_TIMEOUT_SECONDS = 10L

/**
 * One named cleanup action owned by the isolated bootstrap.
 *
 * Steps are intentionally independent: the bootstrap runs every step even when an earlier one
 * throws, so a failing voice disconnect cannot skip the router, shutdown-hook or JDA cleanup.
 */
internal class BootstrapCleanupStep(
    val name: String,
    private val action: () -> Unit,
) {

    /** Runs the cleanup action; any throwable is collected by [runBootstrapCleanup]. */
    fun run() = action()
}

/**
 * Resources that exist when the bootstrap reaches its cleanup phase.
 *
 * Every handle is nullable because a failure can occur before it is created. A missing handle
 * simply removes its step instead of producing a no-op cleanup.
 */
internal data class BootstrapCleanupTargets(
    val jda: JDA?,
    val router: AutoCloseable?,
    val shutdownHook: Thread?,
)

/**
 * Collected cleanup failures.
 *
 * [failure] is the first collected failure and is used for reporting. [fatalFailure] is the first
 * `Error` or [CancellationException]; fatal failures are rethrown by the bootstrap instead of being
 * reported only as an exit code. [attachTo] adds the remaining failures as suppressed exceptions, so
 * a primary bootstrap failure keeps its type and message.
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
     * Returns a report that records [failure] before this report's failures.
     *
     * Used when a step factory fails but the production steps still run: the factory failure stays
     * visible in the report, so a successful startup cannot be reported as clean, and a successful
     * factory keeps its place as the reported failure.
     */
    fun withLeadingFailure(failure: Throwable): BootstrapCleanupReport =
        BootstrapCleanupReport(listOf(failure) + failures)
}

/**
 * Runs every [steps] entry independently and collects the failures.
 *
 * A failing step is logged and does not stop the remaining steps. Fatal `Error`s and
 * [CancellationException]s are collected like any other failure but are not hidden: the caller
 * rethrows them (see [fatalFailure]) after deciding how to preserve the primary exception.
 *
 * This function never throws because of a cleanup step; a thrown step is recorded instead.
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
 * Production cleanup steps in execution order.
 *
 * The voice disconnect runs before JDA shutdown so an active diagnostic connection is closed while
 * the gateway client is still alive. The command router is closed next, then the shutdown hook is
 * removed, and the JDA client is shut down last with a bounded await. Removing the hook while the
 * JVM is already shutting down is treated as benign by [removeShutdownHook], so that normal Ctrl+C
 * does not turn into a cleanup failure.
 */
internal fun defaultBootstrapCleanupSteps(
    targets: BootstrapCleanupTargets,
): List<BootstrapCleanupStep> = buildList {
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
 * Removes [hook] from the runtime shutdown-hook set as a best-effort cleanup step.
 *
 * `Runtime.removeShutdownHook` documents an [IllegalStateException] for exactly one condition: the
 * JVM's shutdown sequence has already begun, so the JDK forbids both adding and removing hooks. That
 * is benign here: the process is already stopping, so the hook cannot and does not need to be
 * removed. The exception type is enough to identify it, so the message is deliberately not
 * inspected. A [SecurityException] or any other failure is a real cleanup failure and is rethrown so
 * the cleanup report does not hide it.
 */
internal fun removeShutdownHook(hook: Thread) {
    try {
        Runtime.getRuntime().removeShutdownHook(hook)
    } catch (_: IllegalStateException) {
        LOG.info("The JVM is already shutting down; the shutdown hook needs no removal")
    }
}

/**
 * Shuts JDA down with a bounded wait.
 *
 * The wait is capped by [timeoutSeconds]; a client that does not finish is forced with
 * `shutdownNow()` instead of blocking the process exit indefinitely. The timeout is injectable so
 * the timeout path can be verified without waiting the production interval.
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
