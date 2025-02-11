package mock

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
object MockedDispatchers {
    val io: CoroutineDispatcher = UnconfinedTestDispatcher()
    val main: CoroutineDispatcher = UnconfinedTestDispatcher()
    val default: CoroutineDispatcher = UnconfinedTestDispatcher()
}
