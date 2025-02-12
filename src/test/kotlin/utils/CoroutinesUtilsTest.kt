package utils

import es.wokis.utils.createCoroutineScope
import kotlinx.coroutines.CoroutineScope
import mock.TestDispatchers
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class CoroutinesUtilsTest {

    @Test
    fun `Given tag and app dispatchers When createCoroutineScope is called Then return CoroutineScope`() {
        // Given
        val tag = "Manolete"
        val dispatchers = TestDispatchers()

        // When
        val actual = createCoroutineScope(tag, dispatchers)

        // Then
        assertTrue(actual is CoroutineScope)
    }
}