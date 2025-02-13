package dispatchers

import es.wokis.dispatchers.AppDispatchers
import es.wokis.dispatchers.AppDispatchersImpl
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AppDispatchersTests {

    private val appDispatchers: AppDispatchers = AppDispatchersImpl()

    @Test
    fun `When appDispatchers main is called Then return Dispatchers Main`() {
        // When
        val actual = appDispatchers.main

        // Then
        assertEquals(actual, Dispatchers.Main)
    }

    @Test
    fun `When appDispatchers io is called Then return Dispatchers IO`() {
        // When
        val actual = appDispatchers.io

        // Then
        assertEquals(actual, Dispatchers.IO)
    }

    @Test
    fun `When appDispatchers default is called Then return Dispatchers Default`() {
        // When
        val actual = appDispatchers.default

        // Then
        assertEquals(actual, Dispatchers.Default)
    }
}
