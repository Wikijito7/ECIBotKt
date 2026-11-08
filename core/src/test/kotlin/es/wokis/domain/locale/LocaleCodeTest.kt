package es.wokis.domain.locale

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class LocaleCodeTest {

    @Test
    fun `Given Discord locale code When represented Then expose the exact neutral value`() {
        // When
        val localeCode = LocaleCode("es-ES")

        // Then
        assertEquals("es-ES", localeCode.value)
        assertEquals("es-ES", localeCode.toString())
    }

    @Test
    fun `Given equal locale codes When compared Then use value equality`() {
        // Then
        assertEquals(LocaleCode("de"), LocaleCode("de"))
        assertNotEquals(LocaleCode("de"), LocaleCode("en-US"))
    }

    @Test
    fun `Given unknown locale marker When represented Then keep the raw marker as a neutral value`() {
        // When
        val localeCode = LocaleCode("unknown")

        // Then
        assertEquals("unknown", localeCode.toString())
    }
}
