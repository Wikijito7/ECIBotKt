package es.wokis.jda.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class JdaTtsUriTest {

    @Test
    fun `Given a simple text When the URI is built Then prefix the Kokoro scheme`() {
        assertEquals("kokoro://?text=hola", kokoroTtsUri("hola"))
    }

    @Test
    fun `Given a text with spaces When the URI is built Then encode every space as percent twenty`() {
        val uri = kokoroTtsUri("hola mundo")

        assertEquals("kokoro://?text=hola%20mundo", uri)
        assertFalse(uri.contains("+"))
    }

    @Test
    fun `Given a text with reserved characters When the URI is built Then percent encode them`() {
        assertEquals(
            "kokoro://?text=hola%20mundo%20%26%20adi%C3%B3s%2F%C2%BFqu%C3%A9%3F",
            kokoroTtsUri("hola mundo & adiós/¿qué?"),
        )
    }

    @Test
    fun `Given an empty text When the URI is built Then keep the bare scheme`() {
        assertEquals("kokoro://?text=", kokoroTtsUri(""))
    }
}
