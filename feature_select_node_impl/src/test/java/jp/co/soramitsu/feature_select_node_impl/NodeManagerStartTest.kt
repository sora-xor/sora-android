package jp.co.soramitsu.feature_select_node_impl

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.feature_select_node_api.data.SelectNodeRepository
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import jp.co.soramitsu.xsubstrate.wsrpc.state.SocketStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A blocked upgrade gate must not leave the app without a node connection for the rest of the
 * process: recovery on the splash screen can clear the gate without a restart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NodeManagerStartTest {

    private val connectionManager = mockk<ConnectionManager>(relaxed = true).also {
        every { it.networkState } returns
            MutableStateFlow<SocketStateMachine.State>(SocketStateMachine.State.Disconnected)
    }
    private val repository = mockk<SelectNodeRepository>(relaxed = true).also {
        every { it.getNodes() } returns emptyFlow()
        every { it.getSelectedNode() } returns emptyFlow()
        coEvery { it.fetchDefaultNodes() } returns emptyList()
    }
    private val nodeDao = mockk<NodeDao>().also {
        coEvery { it.getSelectedNode() } returns NodeLocal(
            address = SELECTED_NODE,
            chain = "SORA",
            name = "SORA",
            isDefault = true,
            isSelected = true,
        )
    }
    private val database = mockk<AppDatabase>().also {
        every { it.nodeDao() } returns nodeDao
    }

    private fun TestScope.nodeManager(ready: () -> Boolean): NodeManagerImpl {
        val coroutineManager = mockk<CoroutineManager>().also {
            every { it.applicationScope } returns
                CoroutineScope(StandardTestDispatcher(testScheduler))
        }
        return NodeManagerImpl(
            connectionManager = connectionManager,
            selectNodeRepository = repository,
            coroutineManager = coroutineManager,
            autoSwitch = true,
            appDatabase = database,
            soraConfigManager = mockk(relaxed = true),
            walletDatabaseReady = ready,
        )
    }

    @Test
    fun `node starts after recovery clears the upgrade gate in the same process`() = runTest {
        var gateClear = false
        val manager = nodeManager { gateClear }
        advanceUntilIdle()

        // Blocked: the wallet database is closed, so nothing is read and no socket is set up.
        assertFalse(manager.ensureStarted())
        coVerify(exactly = 0) { nodeDao.getSelectedNode() }
        verify(exactly = 0) { connectionManager.setAddress(any()) }
        verify(exactly = 0) { connectionManager.observeAppState() }

        // The user taps Try again (or Continue read-only) and the gate clears.
        gateClear = true
        assertTrue(manager.ensureStarted())
        advanceUntilIdle()
        verify(exactly = 1) { connectionManager.setAddress(SELECTED_NODE) }
        verify(exactly = 1) { connectionManager.observeAppState() }
        coVerify(exactly = 1) { repository.fetchDefaultNodes() }

        // Later activities do not start a second connection.
        assertTrue(manager.ensureStarted())
        advanceUntilIdle()
        verify(exactly = 1) { connectionManager.setAddress(any()) }
        verify(exactly = 1) { connectionManager.observeAppState() }
    }

    @Test
    fun `node starts at once when the gate is already clear`() = runTest {
        val manager = nodeManager { true }
        advanceUntilIdle()

        assertTrue(manager.ensureStarted())
        verify(exactly = 1) { connectionManager.setAddress(SELECTED_NODE) }
        verify(exactly = 1) { connectionManager.observeAppState() }
    }

    @Test
    fun `saved compromised node falls back before a socket is opened`() = runTest {
        coEvery { nodeDao.getSelectedNode() } returns NodeLocal(
            address = "wss://ws.mof.sora.org/", chain = "SORA", name = "old node",
            isDefault = false, isSelected = true,
        )
        nodeManager { true }
        advanceUntilIdle()
        verify(exactly = 0) { connectionManager.setAddress("wss://ws.mof.sora.org/") }
        verify(exactly = 1) { connectionManager.setAddress(
            jp.co.soramitsu.common.domain.FlavorOptionsProvider.wsHostUrl,
        ) }
    }

    private companion object {
        const val SELECTED_NODE = "wss://mof2.sora.org/"
    }
}
