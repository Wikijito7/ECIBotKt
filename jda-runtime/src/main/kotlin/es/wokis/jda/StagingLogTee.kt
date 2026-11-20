package es.wokis.jda

import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Appends JVM-level writes to `System.out` and `System.err` to the configured staging log; a relative
 * path resolves against the process working directory.
 *
 * Only JVM-level console writes are intercepted: native/fd-level output (JNI libraries, e.g. the DAVE
 * natives) bypasses the tee. Each installation writes a UTC-timestamped run separator, so the
 * append-only file keeps every staging run. A file that cannot be opened leaves the consoles untouched.
 */
internal object StagingLogTee {

    private const val SEPARATOR_MARKER = "--- staging run ---"

    private val installLock = Any()
    private val writeLock = Any()

    @Volatile
    private var teeOut: PrintStream? = null

    @Volatile
    private var teeErr: PrintStream? = null

    /**
     * Installs the tee on `System.out` and `System.err`; a blank [logFile] leaves today's behavior
     * untouched, and an already-teed console is left as is.
     */
    fun install(logFile: String) {
        if (logFile.isBlank()) return
        synchronized(installLock) {
            if (System.out === teeOut && System.err === teeErr) return
            val file = openAppend(logFile) ?: return
            teeOut = tee(System.out, file)
            teeErr = tee(System.err, file)
            System.setOut(teeOut)
            System.setErr(teeErr)
        }
    }

    /** Opens the append stream and writes the run separator; never throws, only warns. */
    private fun openAppend(logFile: String): FileOutputStream? = try {
        val target = Path.of(logFile).toAbsolutePath()
        target.parent?.let(Files::createDirectories)
        FileOutputStream(target.toFile(), true).also { file ->
            file.write(separatorLine())
            file.flush()
        }
    } catch (error: Exception) {
        System.err.println(
            "Staging log tee disabled: cannot append to '$logFile' (${error.javaClass.simpleName})",
        )
        null
    }

    private fun separatorLine(): ByteArray {
        val timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        return "\n[$timestamp] $SEPARATOR_MARKER\n".toByteArray(Charsets.UTF_8)
    }

    private fun tee(original: PrintStream, file: OutputStream): PrintStream =
        PrintStream(TeeOutputStream(original, file, writeLock), true, original.charset())

    private class TeeOutputStream(
        private val original: OutputStream,
        private val file: OutputStream,
        private val lock: Any,
    ) : OutputStream() {

        override fun write(byte: Int) {
            synchronized(lock) {
                original.write(byte)
                original.flush()
                file.write(byte)
                file.flush()
            }
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            synchronized(lock) {
                original.write(bytes, offset, length)
                original.flush()
                file.write(bytes, offset, length)
                file.flush()
            }
        }

        override fun flush() {
            synchronized(lock) {
                original.flush()
                file.flush()
            }
        }

        /** Closing a console must not close the real process streams nor the shared log file. */
        override fun close() {
            flush()
        }
    }
}
