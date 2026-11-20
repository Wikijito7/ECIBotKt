package es.wokis.jda

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch

class StagingLogTeeTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var originalOut: PrintStream
    private lateinit var originalErr: PrintStream
    private lateinit var capturedOut: ByteArrayOutputStream
    private lateinit var capturedErr: ByteArrayOutputStream
    private lateinit var consoleOut: PrintStream
    private lateinit var consoleErr: PrintStream

    @BeforeEach
    fun captureConsoles() {
        originalOut = System.out
        originalErr = System.err
        capturedOut = ByteArrayOutputStream()
        capturedErr = ByteArrayOutputStream()
        consoleOut = PrintStream(capturedOut, true, StandardCharsets.UTF_8)
        consoleErr = PrintStream(capturedErr, true, StandardCharsets.UTF_8)
        System.setOut(consoleOut)
        System.setErr(consoleErr)
    }

    @AfterEach
    fun restoreConsoles() {
        try {
            System.out.flush()
            System.err.flush()
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }
    }

    @Test
    fun `Given an installed tee When stdout writes Then the console keeps the bytes and the file mirrors them`() {
        val logFile = tempDir.resolve("logs/jda-runtime.log")

        StagingLogTee.install(logFile.toString())
        System.out.println("mirrored-stdout-line")
        System.out.flush()

        val consoleText = capturedOut.toString(StandardCharsets.UTF_8)
        val fileText = Files.readString(logFile)
        val separator = requireNotNull(SEPARATOR_REGEX.find(fileText)) {
            "the file must hold a run separator: $fileText"
        }

        assertEquals("mirrored-stdout-line\n", consoleText)
        assertEquals(separator.value + consoleText, fileText)
    }

    @Test
    fun `Given an installed tee When stderr writes Then the file captures the error stream too`() {
        val logFile = tempDir.resolve("logs/stderr.log")

        StagingLogTee.install(logFile.toString())
        System.err.println("mirrored-stderr-line")
        System.err.flush()

        val consoleText = capturedErr.toString(StandardCharsets.UTF_8)
        val fileText = Files.readString(logFile)

        assertEquals("mirrored-stderr-line\n", consoleText)
        assertTrue(fileText.endsWith(consoleText), "the file must mirror stderr: $fileText")
    }

    @Test
    fun `Given a blank target When installing Then no file is created and the consoles stay untouched`() {
        listOf("", "   ", "\n\t ").forEach { blank ->
            StagingLogTee.install(blank)

            assertSame(consoleOut, System.out, "a blank target must not replace stdout: '$blank'")
            assertSame(consoleErr, System.err, "a blank target must not replace stderr: '$blank'")
        }

        assertTrue(Files.list(tempDir).use { stream -> stream.findAny().isEmpty }, "no file may be created")
    }

    @Test
    fun `Given an unopenable target When installing Then warn on the untouched console and leave both streams alone`() {
        val blocker = tempDir.resolve("blocking-file")
        Files.writeString(blocker, "not a directory")
        val target = blocker.resolve("jda-runtime.log")

        StagingLogTee.install(target.toString())

        val warning = capturedErr.toString(StandardCharsets.UTF_8)
        assertTrue(warning.contains("Staging log tee disabled"), warning)
        assertTrue(warning.contains(target.toString()), warning)
        assertTrue(warning.contains("FileAlreadyExistsException"), warning)
        assertSame(consoleOut, System.out)
        assertSame(consoleErr, System.err)
        assertFalse(Files.exists(target))
        assertEquals("not a directory", Files.readString(blocker))
    }

    @Test
    fun `Given an installed tee When installing again Then keep a single separator and write each line once`() {
        val logFile = tempDir.resolve("logs/idempotent.log")

        StagingLogTee.install(logFile.toString())
        val firstTee = System.out
        System.out.println("before-second-install")
        StagingLogTee.install(logFile.toString())
        System.out.println("after-second-install")
        System.out.flush()

        val fileText = Files.readString(logFile)

        assertEquals(1, SEPARATOR_REGEX.findAll(fileText).count(), fileText)
        assertEquals(1, occurrences(fileText, "before-second-install"), fileText)
        assertEquals(1, occurrences(fileText, "after-second-install"), fileText)
        assertSame(firstTee, System.out)
    }

    @Test
    fun `Given two console writers When they write concurrently Then the file holds only whole lines`() {
        val logFile = tempDir.resolve("logs/concurrent.log")
        StagingLogTee.install(logFile.toString())
        val start = CountDownLatch(1)
        val writers = (0 until WRITERS).map { writer ->
            Thread {
                start.await()
                repeat(LINES_PER_WRITER) { index -> System.out.println(line(writer, index)) }
            }
        }

        writers.forEach(Thread::start)
        start.countDown()
        writers.forEach { writer -> writer.join(JOIN_TIMEOUT_MILLIS) }

        val expected = (0 until WRITERS).flatMap { writer ->
            (0 until LINES_PER_WRITER).map { index -> line(writer, index) }
        }
        val fileLines = Files.readAllLines(logFile)
            .filter { line -> line.isNotBlank() && !line.contains(SEPARATOR_MARKER) }

        assertTrue(writers.none(Thread::isAlive), "every writer must finish")
        assertEquals(expected.size, fileLines.size, "every line must be written exactly once: $fileLines")
        assertTrue(fileLines.containsAll(expected), "the file must contain only whole lines: $fileLines")
    }

    @Test
    fun `Given stdout and stderr writers When they write at once Then the shared tee keeps every line whole`() {
        val logFile = tempDir.resolve("logs/cross-stream.log")
        StagingLogTee.install(logFile.toString())
        val start = CountDownLatch(1)
        val streamWriters = listOf(
            Thread {
                start.await()
                repeat(LINES_PER_WRITER) { index -> System.out.println(crossStreamLine(OUT_PREFIX, index)) }
                System.out.flush()
            },
            Thread {
                start.await()
                repeat(LINES_PER_WRITER) { index -> System.err.println(crossStreamLine(ERR_PREFIX, index)) }
                System.err.flush()
            },
        )

        streamWriters.forEach(Thread::start)
        start.countDown()
        streamWriters.forEach { writer -> writer.join(JOIN_TIMEOUT_MILLIS) }

        val expected = listOf(OUT_PREFIX, ERR_PREFIX).flatMap { prefix ->
            (0 until LINES_PER_WRITER).map { index -> crossStreamLine(prefix, index) }
        }
        val fileLines = Files.readAllLines(logFile)
            .filter { line -> line.isNotBlank() && !line.contains(SEPARATOR_MARKER) }

        assertTrue(streamWriters.none(Thread::isAlive), "every writer must finish")
        assertEquals(expected.size, fileLines.size, "every line must be written exactly once: $fileLines")
        assertTrue(fileLines.containsAll(expected), "the file must hold only whole lines: $fileLines")
        assertTrue(
            fileLines.all(WHOLE_LINE_REGEX::matches),
            "no line may interleave the stdout and stderr chunks: $fileLines",
        )
    }

    private fun line(writer: Int, index: Int): String = "writer-$writer-line-$index-${"x".repeat(PADDING)}"

    private fun crossStreamLine(prefix: String, index: Int): String =
        "$prefix-line-$index-${"x".repeat(PADDING)}"

    private fun occurrences(text: String, needle: String): Int = Regex(Regex.escape(needle)).findAll(text).count()

    private companion object {
        // The tee state persists across tests by design; isolation comes from the fresh consoles
        // installed in @BeforeEach, which always differ from the tee left by the previous test.
        private val SEPARATOR_REGEX = Regex("\\n\\[.+\\] --- staging run ---\\n")
        private const val SEPARATOR_MARKER = "--- staging run ---"
        private val WHOLE_LINE_REGEX = Regex("(out|err)-line-\\d+-x+")
        private const val OUT_PREFIX = "out"
        private const val ERR_PREFIX = "err"
        private const val WRITERS = 2
        private const val LINES_PER_WRITER = 20
        private const val PADDING = 64
        private const val JOIN_TIMEOUT_MILLIS = 5_000L
    }
}
