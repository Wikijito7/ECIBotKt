package mock

import es.wokis.dispatchers.AppDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
data class MockedDispatchers (
    override val io: CoroutineDispatcher = UnconfinedTestDispatcher(),
    override val main: CoroutineDispatcher = UnconfinedTestDispatcher(),
    override val default: CoroutineDispatcher = UnconfinedTestDispatcher()
) : AppDispatchers
