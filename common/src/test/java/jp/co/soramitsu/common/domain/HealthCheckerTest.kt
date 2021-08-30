//package jp.co.soramitsu.common.domain
//
//import jp.co.soramitsu.common.data.network.substrate.ConnectionManager
//import kotlinx.coroutines.ExperimentalCoroutinesApi
//import kotlinx.coroutines.flow.collect
//import kotlinx.coroutines.flow.take
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.runBlocking
//import kotlinx.coroutines.test.TestCoroutineDispatcher
//import org.junit.Assert.assertEquals
//import org.junit.Before
//import org.junit.Test
//import org.mockito.Mock
//
//@ExperimentalCoroutinesApi
//class HealthCheckerTest {
//
//    private lateinit var healthChecker: HealthChecker
//
//    private val dispatcher = TestCoroutineDispatcher()
//
//    @Mock
//    private lateinit var connectionManager: ConnectionManager
//
//    @Before
//    fun setup() {
//        healthChecker = HealthChecker(connectionManager)
//    }
//
//    @Test
//    fun `healh checker test`() = runBlocking {
//        val expected = listOf(true, false, true)
//        val actual = mutableListOf<Boolean>()
//        launch(dispatcher) {
//            healthChecker.observeHealthState().take(3).collect { actual.add(it) }
//        }
//
//        healthChecker.connectionStable()
//        healthChecker.connectionErrorHandled()
//        healthChecker.connectionStable()
//
//        assertEquals(expected, actual)
//    }
//}