package es.wokis.jda.voice

import kotlinx.coroutines.CancellationException
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus
import org.slf4j.LoggerFactory

/**
 * Best-effort bootstrap disconnect of every voice session owned by [jda].
 *
 * The bootstrap runs this before JDA shutdown so the diagnostic connection is closed while the
 * gateway client can still send the voice-state update. It only requests the close: it does not
 * wait for gateway confirmation, so it cannot block process exit, and JDA shutdown remains the final
 * boundary. Every guild is attempted even when one disconnect fails; the failures are combined with
 * the rest attached as suppressed so the bootstrap can report them.
 */
internal fun disconnectVoiceConnections(jda: JDA) {
    if (jda.status == JDA.Status.SHUTDOWN) {
        return
    }

    val failures = mutableListOf<Throwable>()

    jda.guilds.forEach { guild ->
        try {
            val audioManager = guild.audioManager
            if (audioManager.isConnected || audioManager.connectionStatus != ConnectionStatus.NOT_CONNECTED) {
                audioManager.closeAudioConnection()
            }
        } catch (error: Throwable) {
            failures += error
            LOG.warn("Could not request a voice disconnect ({})", error.javaClass.simpleName)
        }
    }

    when {
        failures.isEmpty() -> Unit
        else -> {
            // Prefer a fatal failure as the primary so an Error is never buried under a normal
            // exception with the rest attached as suppressed.
            val primary = failures.firstOrNull { it is Error || it is CancellationException }
                ?: failures.first()
            failures.forEach { failure ->
                if (failure !== primary) {
                    primary.addSuppressed(failure)
                }
            }
            throw primary
        }
    }
}

private val LOG = LoggerFactory.getLogger("es.wokis.jda.voice.JdaVoiceCleanup")
