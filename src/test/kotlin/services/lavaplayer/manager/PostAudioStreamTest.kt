package services.lavaplayer.manager

import com.sedmelluq.discord.lavaplayer.tools.Units
import org.apache.http.Header
import org.apache.http.HeaderIterator
import org.apache.http.HttpEntity
import org.apache.http.ProtocolVersion
import org.apache.http.StatusLine
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.params.HttpParams
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PostAudioStreamTest {

    private fun createHeaderIterator(): HeaderIterator = object : HeaderIterator {
        override fun hasNext() = false
        override fun next() = throw NoSuchElementException()
        override fun nextHeader() = throw NoSuchElementException()
        override fun remove() = throw UnsupportedOperationException()
    }

    private fun createResponse(entity: HttpEntity): CloseableHttpResponse = object : CloseableHttpResponse {
        private var closed = false

        override fun close() {
            closed = true
        }
        override fun getStatusLine(): StatusLine? = null
        override fun setStatusLine(statusLine: StatusLine?) {}
        override fun setStatusLine(ver: ProtocolVersion?, code: Int) {}
        override fun setStatusLine(ver: ProtocolVersion?, code: Int, reason: String?) {}
        override fun setReasonPhrase(reason: String?) {}
        override fun getEntity(): HttpEntity = entity
        override fun setEntity(entity: HttpEntity?) {}
        override fun getLocale(): Locale? = null
        override fun setLocale(loc: Locale?) {}
        override fun getProtocolVersion(): ProtocolVersion? = null
        override fun containsHeader(name: String?): Boolean = false
        override fun getHeaders(name: String?): Array<Header> = emptyArray()
        override fun getFirstHeader(name: String?): Header? = null
        override fun getLastHeader(name: String?): Header? = null
        override fun getAllHeaders(): Array<Header> = emptyArray()
        override fun addHeader(header: Header?) {}
        override fun addHeader(name: String?, value: String?) {}
        override fun setHeader(header: Header?) {}
        override fun setHeader(name: String?, value: String?) {}
        override fun setHeaders(headers: Array<out Header>?) {}
        override fun removeHeader(header: Header?) {}
        override fun removeHeaders(name: String?) {}
        override fun headerIterator(): HeaderIterator = createHeaderIterator()
        override fun headerIterator(name: String?): HeaderIterator = createHeaderIterator()
        override fun getParams(): HttpParams? = null
        override fun setParams(params: HttpParams?) {}
        override fun setStatusCode(code: Int) {}
    }

    private fun createEntity(testData: ByteArray): HttpEntity = object : HttpEntity {
        override fun isRepeatable() = false
        override fun isChunked() = false
        override fun getContentLength() = testData.size.toLong()
        override fun getContentType() = null
        override fun getContentEncoding() = null
        override fun getContent() = ByteArrayInputStream(testData)
        override fun writeTo(out: OutputStream?) {}
        override fun isStreaming() = true

        @Deprecated("Deprecated in Java")
        override fun consumeContent() {}
    }

    private fun createEntityWithCustomStream(stream: InputStream): HttpEntity = object : HttpEntity {
        override fun isRepeatable() = false
        override fun isChunked() = false
        override fun getContentLength() = 100L
        override fun getContentType() = null
        override fun getContentEncoding() = null
        override fun getContent() = stream
        override fun writeTo(out: OutputStream?) {}
        override fun isStreaming() = true

        @Deprecated("Deprecated in Java")
        override fun consumeContent() {}
    }

    @Test
    fun `When stream is created with content length Then store content length`() {
        // Given
        val testData = "Hello".toByteArray()
        val response = createResponse(createEntity(testData))
        val contentLength = 1024L

        // When
        val stream = PostAudioStream(response, contentLength)

        // Then
        assertEquals(contentLength, stream.contentLength)
    }

    @Test
    fun `When stream is created with null content length Then use unknown content length`() {
        // Given
        val testData = "Hello".toByteArray()
        val response = createResponse(createEntity(testData))

        // When
        val stream = PostAudioStream(response, null)

        // Then
        assertEquals(Units.CONTENT_LENGTH_UNKNOWN, stream.contentLength)
    }

    @Test
    fun `When read is called Then return byte from content`() {
        // Given
        val testData = byteArrayOf(0x01, 0x02, 0x03)
        val response = createResponse(createEntity(testData))
        val stream = PostAudioStream(response, testData.size.toLong())

        // When
        val firstByte = stream.read()
        val secondByte = stream.read()
        val thirdByte = stream.read()
        val eof = stream.read()

        // Then
        assertEquals(0x01, firstByte)
        assertEquals(0x02, secondByte)
        assertEquals(0x03, thirdByte)
        assertEquals(-1, eof)
        assertEquals(3, stream.position)
    }

    @Test
    fun `When read byte array is called Then fill buffer from content`() {
        // Given
        val testData = "Hello World".toByteArray()
        val response = createResponse(createEntity(testData))
        val stream = PostAudioStream(response, testData.size.toLong())
        val buffer = ByteArray(5)

        // When
        val bytesRead = stream.read(buffer, 0, 5)

        // Then
        assertEquals(5, bytesRead)
        assertEquals("Hello", String(buffer))
        assertEquals(5, stream.position)
    }

    @Test
    fun `When skip is called Then skip bytes in content`() {
        // Given
        val testData = "Hello World".toByteArray()
        val response = createResponse(createEntity(testData))
        val stream = PostAudioStream(response, testData.size.toLong())

        // When
        val skipped = stream.skip(6)
        val buffer = ByteArray(5)
        val bytesRead = stream.read(buffer, 0, 5)

        // Then
        assertEquals(6, skipped)
        assertEquals(5, bytesRead)
        assertEquals("World", String(buffer))
        assertEquals(11, stream.position)
    }

    @Test
    fun `When available is called Then return available bytes from content`() {
        // Given
        val testData = "Hello World".toByteArray()
        val response = createResponse(createEntity(testData))
        val stream = PostAudioStream(response, testData.size.toLong())

        // When
        val available = stream.available()

        // Then
        assertEquals(11, available)
    }

    @Test
    fun `When close is called Then close content and response`() {
        // Given
        var contentClosed = false
        val testData = "Hello".toByteArray()
        val customStream = object : ByteArrayInputStream(testData) {
            override fun close() {
                contentClosed = true
                super.close()
            }
        }
        var responseClosed = false
        val response = object : CloseableHttpResponse {
            override fun close() {
                responseClosed = true
            }
            override fun getStatusLine(): StatusLine? = null
            override fun setStatusLine(statusLine: StatusLine?) {}
            override fun setStatusLine(ver: ProtocolVersion?, code: Int) {}
            override fun setStatusLine(ver: ProtocolVersion?, code: Int, reason: String?) {}
            override fun setReasonPhrase(reason: String?) {}
            override fun getEntity() = createEntityWithCustomStream(customStream)
            override fun setEntity(entity: HttpEntity?) {}
            override fun getLocale(): Locale? = null
            override fun setLocale(loc: Locale?) {}
            override fun getProtocolVersion(): ProtocolVersion? = null
            override fun containsHeader(name: String?): Boolean = false
            override fun getHeaders(name: String?): Array<Header> = emptyArray()
            override fun getFirstHeader(name: String?): Header? = null
            override fun getLastHeader(name: String?): Header? = null
            override fun getAllHeaders(): Array<Header> = emptyArray()
            override fun addHeader(header: Header?) {}
            override fun addHeader(name: String?, value: String?) {}
            override fun setHeader(header: Header?) {}
            override fun setHeader(name: String?, value: String?) {}
            override fun setHeaders(headers: Array<out Header>?) {}
            override fun removeHeader(header: Header?) {}
            override fun removeHeaders(name: String?) {}
            override fun headerIterator(): HeaderIterator = createHeaderIterator()
            override fun headerIterator(name: String?): HeaderIterator = createHeaderIterator()
            override fun getParams(): HttpParams? = null
            override fun setParams(params: HttpParams?) {}
            override fun setStatusCode(code: Int) {}
        }
        val stream = PostAudioStream(response, 100L)

        // When
        stream.close()

        // Then
        assertTrue(contentClosed)
        assertTrue(responseClosed)
    }

    @Test
    fun `When close is called twice Then do not throw`() {
        // Given
        val testData = "Hello".toByteArray()
        val response = createResponse(createEntity(testData))
        val stream = PostAudioStream(response, 100L)

        // When/Then - should not throw
        stream.close()
        stream.close()
    }

    @Test
    fun `When read is called after close Then throw IOException`() {
        // Given
        val testData = "Hello".toByteArray()
        val response = createResponse(createEntity(testData))
        val stream = PostAudioStream(response, 100L)
        stream.close()

        // When/Then
        assertThrows<IOException> {
            stream.read()
        }
    }

    @Test
    fun `When read byte array is called after close Then throw IOException`() {
        // Given
        val testData = "Hello".toByteArray()
        val response = createResponse(createEntity(testData))
        val stream = PostAudioStream(response, 100L)
        stream.close()

        // When/Then
        assertThrows<IOException> {
            stream.read(ByteArray(10), 0, 10)
        }
    }

    @Test
    fun `When skip is called after close Then throw IOException`() {
        // Given
        val testData = "Hello".toByteArray()
        val response = createResponse(createEntity(testData))
        val stream = PostAudioStream(response, 100L)
        stream.close()

        // When/Then
        assertThrows<IOException> {
            stream.skip(10)
        }
    }

    @Test
    fun `When available is called after close Then throw IOException`() {
        // Given
        val testData = "Hello".toByteArray()
        val response = createResponse(createEntity(testData))
        val stream = PostAudioStream(response, 100L)
        stream.close()

        // When/Then
        assertThrows<IOException> {
            stream.available()
        }
    }

    @Test
    fun `When canSeekHard is called Then return false`() {
        // Given
        val testData = "Hello".toByteArray()
        val response = createResponse(createEntity(testData))
        val stream = PostAudioStream(response, 100L)

        // When
        val actual = stream.canSeekHard()

        // Then
        assertFalse(actual)
    }

    @Test
    fun `When getTrackInfoProviders is called Then return empty list`() {
        // Given
        val testData = "Hello".toByteArray()
        val response = createResponse(createEntity(testData))
        val stream = PostAudioStream(response, 100L)

        // When
        val actual = stream.trackInfoProviders

        // Then
        assertNotNull(actual)
        assertTrue(actual.isEmpty())
    }

    @Test
    fun `When close encounters exception in content Then still close response`() {
        // Given
        var responseClosed = false
        val customStream = object : InputStream() {
            override fun read() = -1

            override fun close() = throw IOException("Content close failed")
        }
        val response = object : CloseableHttpResponse {
            override fun close() {
                responseClosed = true
            }
            override fun getStatusLine(): StatusLine? = null
            override fun setStatusLine(statusLine: StatusLine?) {}
            override fun setStatusLine(ver: ProtocolVersion?, code: Int) {}
            override fun setStatusLine(ver: ProtocolVersion?, code: Int, reason: String?) {}
            override fun setReasonPhrase(reason: String?) {}
            override fun getEntity() = createEntityWithCustomStream(customStream)
            override fun setEntity(entity: HttpEntity?) {}
            override fun getLocale(): Locale? = null
            override fun setLocale(loc: Locale?) {}
            override fun getProtocolVersion(): ProtocolVersion? = null
            override fun containsHeader(name: String?): Boolean = false
            override fun getHeaders(name: String?): Array<Header> = emptyArray()
            override fun getFirstHeader(name: String?): Header? = null
            override fun getLastHeader(name: String?): Header? = null
            override fun getAllHeaders(): Array<Header> = emptyArray()
            override fun addHeader(header: Header?) {}
            override fun addHeader(name: String?, value: String?) {}
            override fun setHeader(header: Header?) {}
            override fun setHeader(name: String?, value: String?) {}
            override fun setHeaders(headers: Array<out Header>?) {}
            override fun removeHeader(header: Header?) {}
            override fun removeHeaders(name: String?) {}
            override fun headerIterator(): HeaderIterator = createHeaderIterator()
            override fun headerIterator(name: String?): HeaderIterator = createHeaderIterator()
            override fun getParams(): HttpParams? = null
            override fun setParams(params: HttpParams?) {}
            override fun setStatusCode(code: Int) {}
        }
        val stream = PostAudioStream(response, 100L)

        // When/Then
        stream.close()

        // Then
        assertTrue(responseClosed)
    }

    @Test
    fun `When close encounters exception in response Then do not throw`() {
        // Given
        val customStream = object : InputStream() {
            override fun read() = -1

            override fun close() {}
        }
        val response = object : CloseableHttpResponse {
            override fun close() = throw IOException("Response close failed")
            override fun getStatusLine(): StatusLine? = null
            override fun setStatusLine(statusLine: StatusLine?) {}
            override fun setStatusLine(ver: ProtocolVersion?, code: Int) {}
            override fun setStatusLine(ver: ProtocolVersion?, code: Int, reason: String?) {}
            override fun setReasonPhrase(reason: String?) {}
            override fun getEntity() = createEntityWithCustomStream(customStream)
            override fun setEntity(entity: HttpEntity?) {}
            override fun getLocale(): Locale? = null
            override fun setLocale(loc: Locale?) {}
            override fun getProtocolVersion(): ProtocolVersion? = null
            override fun containsHeader(name: String?): Boolean = false
            override fun getHeaders(name: String?): Array<Header> = emptyArray()
            override fun getFirstHeader(name: String?): Header? = null
            override fun getLastHeader(name: String?): Header? = null
            override fun getAllHeaders(): Array<Header> = emptyArray()
            override fun addHeader(header: Header?) {}
            override fun addHeader(name: String?, value: String?) {}
            override fun setHeader(header: Header?) {}
            override fun setHeader(name: String?, value: String?) {}
            override fun setHeaders(headers: Array<out Header>?) {}
            override fun removeHeader(header: Header?) {}
            override fun removeHeaders(name: String?) {}
            override fun headerIterator(): HeaderIterator = createHeaderIterator()
            override fun headerIterator(name: String?): HeaderIterator = createHeaderIterator()
            override fun getParams(): HttpParams? = null
            override fun setParams(params: HttpParams?) {}
            override fun setStatusCode(code: Int) {}
        }
        val stream = PostAudioStream(response, 100L)

        // When/Then - should not throw
        stream.close()
    }

    @Test
    fun `When position is updated through multiple operations Then track correctly`() {
        // Given
        val testData = "Hello World This Is A Test".toByteArray()
        val response = createResponse(createEntity(testData))
        val stream = PostAudioStream(response, testData.size.toLong())

        // When/Then
        assertEquals(0, stream.position)

        stream.read()
        assertEquals(1, stream.position)

        stream.read(ByteArray(5), 0, 5)
        assertEquals(6, stream.position)

        stream.skip(5)
        assertEquals(11, stream.position)
    }
}
