package services.lavaplayer.manager

import com.sedmelluq.discord.lavaplayer.tools.Units
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.info.AudioTrackInfoProvider
import org.apache.http.client.methods.CloseableHttpResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream

private val log: Logger = LoggerFactory.getLogger(PostAudioStream::class.java)
private const val MAX_SKIP_DISTANCE = 512L * 1024L

/**
 * Simple wrapper for HTTP POST response streams.
 *
 * Unlike PersistentHttpStream, this does NOT support:
 * - Reconnection (POST cannot be resumed)
 * - Range requests (POST doesn't support range headers)
 * - Hard seeking (sequential playback only)
 *
 * This is suitable for TTS services where:
 * - Audio is generated on-demand
 * - Sequential playback is sufficient
 * - Generation time may be long
 */
class PostAudioStream(
    private val response: CloseableHttpResponse,
    contentLength: Long?
) : SeekableInputStream(
    contentLength ?: Units.CONTENT_LENGTH_UNKNOWN,
    MAX_SKIP_DISTANCE
) {
    private val content: InputStream = response.entity.content
    private var internalPosition: Long = 0
    private var closed: Boolean = false

    @Throws(IOException::class)
    override fun read(): Int {
        checkNotClosed()
        val result = content.read()
        if (result >= 0) {
            internalPosition++
        }
        return result
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        checkNotClosed()
        val result = content.read(b, off, len)
        if (result >= 0) {
            internalPosition += result.toLong()
        }
        return result
    }

    @Throws(IOException::class)
    override fun skip(n: Long): Long {
        checkNotClosed()
        val result = content.skip(n)
        if (result > 0) {
            internalPosition += result
        }
        return result
    }

    @Throws(IOException::class)
    override fun available(): Int {
        checkNotClosed()
        return content.available()
    }

    @Throws(IOException::class)
    override fun close() {
        if (!closed) {
            closed = true
            try {
                content.close()
            } catch (e: IOException) {
                log.debug("Failed to close content stream", e)
            }
            try {
                response.close()
            } catch (e: IOException) {
                log.debug("Failed to close response", e)
            }
        }
    }

    override fun getPosition(): Long = internalPosition

    /**
     * Hard seeking is NOT supported for POST streams.
     * This method will throw an IOException if called with a non-zero position.
     */
    @Throws(IOException::class)
    override fun seekHard(position: Long) {
        if (position == 0L && internalPosition == 0L) {
            // Already at start, nothing to do
            return
        }
        if (position == internalPosition) {
            // Already at requested position
            return
        }
        throw IOException(
            "Seeking is not supported for TTS audio streams. " +
                "Current position: $internalPosition, requested: $position"
        )
    }

    override fun canSeekHard(): Boolean {
        // Always return false since we don't support seeking
        return false
    }

    override fun getTrackInfoProviders(): List<AudioTrackInfoProvider?> = emptyList()

    private fun checkNotClosed() {
        if (closed) {
            throw IOException("Stream is closed")
        }
    }
}
