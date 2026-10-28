package jp.co.soramitsu.feature_select_node_impl

import io.mockk.*
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.account.WalletMutationCoordinator
import jp.co.soramitsu.common.domain.ChainNode
import jp.co.soramitsu.common.domain.FlavorOptionsProvider
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.feature_select_node_api.NodeManagerEvent
import jp.co.soramitsu.feature_select_node_api.data.SelectNodeRepository
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import jp.co.soramitsu.xsubstrate.wsrpc.state.SocketStateMachine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Socket events and in-memory node rows exercise the real selection state machine, without RPC. */
@OptIn(ExperimentalCoroutinesApi::class)
class NodeConnectionFlowTest {
    private val a = node("a", true)
    private val b = node("b")
    private val c = node("c")
    private fun node(name: String, selected: Boolean = false) = ChainNode(
        "SORA", name, "wss://$name.example.test/", selected, true,
    )

    private inner class Fixture(val scope: TestScope, auto: Boolean = false, initial: List<ChainNode> = listOf(a, b, c)) {
        val states = MutableStateFlow<SocketStateMachine.State>(SocketStateMachine.State.Disconnected)
        val nodes = MutableStateFlow(initial)
        val selected = MutableStateFlow<ChainNode?>(initial.firstOrNull { it.isSelected })
        val connected = MutableStateFlow(false)
        val events = mutableListOf<NodeManagerEvent>()
        val switches = mutableListOf<String>()
        var online = true
        val connection = mockk<ConnectionManager>(relaxed = true) {
            every { networkState } returns states
            every { connectionState } returns connected
            every { isNetworkAvailable } answers { online }
            every { switchUrl(any()) } answers { switches += firstArg<String>() }
        }
        val repository = mockk<SelectNodeRepository>(relaxed = true) {
            every { getNodes() } returns nodes
            every { getSelectedNode() } returns selected
            coEvery { fetchDefaultNodes() } returns initial
            coEvery { getBlockHash() } returns "0xaabb"
            coEvery { selectNode(any()) } coAnswers {
                val choice = firstArg<ChainNode>()
                selected.value = choice.copy(isSelected = true)
                nodes.value = nodes.value.map { it.copy(isSelected = it.address == choice.address) }
            }
        }
        val dao = mockk<NodeDao> {
            coEvery { getSelectedNode() } answers { selected.value?.let {
                NodeLocal(it.address, it.chain, it.name, it.isDefault, it.isSelected)
            } }
        }
        val database = mockk<AppDatabase> { every { nodeDao() } returns dao }
        val config = mockk<SoraConfigManager> { coEvery { getGenesis() } returns "aabb" }
        val coroutines = mockk<CoroutineManager> { every { applicationScope } returns scope.backgroundScope }
        val manager = NodeManagerImpl(connection, repository, coroutines, auto, database, config) { true }
        init {
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { manager.events.collect { events += it } }
            scope.runCurrent()
        }
        fun connected(url: String) {
            states.value = mockk<SocketStateMachine.State.Connected> { every { this@mockk.url } returns url }
            scope.runCurrent()
        }
        fun failed(url: String, attempt: Int = 1) {
            states.value = mockk<SocketStateMachine.State.WaitingForReconnect> {
                every { this@mockk.url } returns url
                every { this@mockk.attempt } returns attempt
            }
            scope.runCurrent()
        }
        fun connect(node: ChainNode) { manager.tryToConnect(node); scope.runCurrent() }
        fun check(url: String) { manager.checkGenesisHash(url); scope.runCurrent() }
    }
    private fun TestScope.fixture(auto: Boolean = false, initial: List<ChainNode> = listOf(a, b, c)) = Fixture(this, auto, initial)

    @Test fun selectionPersistsOnlyAfterMatchingConnection() = runTest {
        val f = fixture(); f.connect(b)
        assertEquals(listOf(b.address), f.switches)
        assertEquals(a.address, f.selected.value?.address)
        f.connected(b.address)
        assertEquals(b.address, f.selected.value?.address)
        assertEquals(b.address, (f.events.single() as NodeManagerEvent.Connected).url)
    }
    @Test fun oldConnectedEventCannotCompleteAnotherSelection() = runTest {
        val f = fixture(); f.connect(b); f.connected(a.address)
        assertTrue(f.events.isEmpty()); assertEquals(a.address, f.selected.value?.address)
        f.connected(b.address); assertEquals(b.address, f.selected.value?.address)
    }
    @Test fun matchingFailureRestoresPreviousNodeAndReportsFailedCandidate() = runTest {
        val f = fixture(); f.connect(b); f.failed(b.address)
        assertEquals(listOf(b.address, a.address), f.switches)
        assertEquals(b.address, (f.events.single() as NodeManagerEvent.ConnectionFailed).url)
    }
    @Test fun oldReconnectCannotAbortNewSelection() = runTest {
        val f = fixture(); f.connect(b); f.failed(a.address)
        assertEquals(listOf(b.address), f.switches); assertTrue(f.events.isEmpty())
        f.connected(b.address); assertEquals(b.address, f.selected.value?.address)
    }
    @Test fun latestManualChoiceIgnoresEarlierCandidateFailure() = runTest {
        val f = fixture(); f.connect(b); f.connect(c); f.failed(b.address)
        assertTrue(f.events.isEmpty()); f.connected(c.address)
        assertEquals(c.address, f.selected.value?.address)
    }
    @Test fun noPreviousNodeStillClosesFailedManualAttempt() = runTest {
        val f = fixture(initial = listOf(b)); f.connect(b); f.failed(b.address); f.failed(b.address, 2)
        assertEquals(1, f.events.size)
    }
    @Test fun completedSelectionEmitsOnlyOnce() = runTest {
        val f = fixture(); f.connect(b); f.connected(b.address); f.connected(b.address)
        assertEquals(1, f.events.size); coVerify(exactly = 1) { f.repository.selectNode(any()) }
    }
    @Test fun offlineManualChoiceDoesNotSwitchOrPersist() = runTest {
        val f = fixture(); f.online = false; f.connect(b)
        assertSame(NodeManagerEvent.NoConnection, f.events.single()); assertTrue(f.switches.isEmpty())
        coVerify(exactly = 0) { f.repository.selectNode(any()) }
    }
    @Test fun disallowedManualEndpointFailsBeforeNetworkAccess() = runTest {
        val f = fixture(); val blocked = b.copy(address = "wss://ws.mof.sora.org/"); f.connect(blocked)
        assertEquals(blocked.address, (f.events.single() as NodeManagerEvent.ConnectionFailed).url)
        assertTrue(f.switches.isEmpty())
    }
    @Test fun disallowedGenesisEndpointFailsWithoutSwitching() = runTest {
        val f = fixture(); f.check("https://b.example.test/")
        assertFalse((f.events.single() as NodeManagerEvent.GenesisValidated).result)
        assertTrue(f.switches.isEmpty())
    }
    @Test fun offlineGenesisCheckReportsConnectivity() = runTest {
        val f = fixture(); f.online = false; f.check("wss://new.example.test/")
        assertSame(NodeManagerEvent.NoConnection, f.events.single()); assertTrue(f.switches.isEmpty())
    }
    @Test fun existingEndpointIgnoresOneTrailingSlash() = runTest {
        val f = fixture(); f.check(b.address.removeSuffix("/"))
        val event = f.events.single() as NodeManagerEvent.NodeExisting
        assertEquals(b.name, event.existedNodeName); assertEquals(a.address, event.currentNodeUrl)
        assertTrue(f.switches.isEmpty())
    }
    @Test fun existingEndpointWithoutSelectedWalletReportsEmptyCurrentUrl() = runTest {
        val f = fixture(initial = listOf(b)); f.check(b.address)
        assertEquals("", (f.events.single() as NodeManagerEvent.NodeExisting).currentNodeUrl)
    }
    @Test fun matchingGenesisWithOptionalPrefixIsAcceptedAndRestoresConnection() = runTest {
        val f = fixture(); val url = "wss://new.example.test/"; f.check(url); f.connected(url)
        assertTrue((f.events.single() as NodeManagerEvent.GenesisValidated).result)
        assertEquals(listOf(url, a.address), f.switches)
        coVerify(exactly = 0) { f.repository.selectNode(any()) }
    }
    @Test fun wrongGenesisIsRejectedAndPreviousNodeRestored() = runTest {
        val f = fixture(); coEvery { f.repository.getBlockHash() } returns "0xccdd"
        val url = "wss://new.example.test/"; f.check(url); f.connected(url)
        assertFalse((f.events.single() as NodeManagerEvent.GenesisValidated).result)
        assertEquals(a.address, f.switches.last())
    }
    @Test fun genesisRpcFailureRejectsCandidateWithoutPersisting() = runTest {
        val f = fixture(); coEvery { f.repository.getBlockHash() } throws IllegalStateException("offline fixture")
        val url = "wss://new.example.test/"; f.check(url); f.connected(url)
        assertFalse((f.events.single() as NodeManagerEvent.GenesisValidated).result)
        assertEquals(a.address, f.selected.value?.address)
    }
    @Test fun unrelatedConnectionDoesNotRunGenesisRpc() = runTest {
        val f = fixture(); f.check("wss://new.example.test/"); f.connected(a.address)
        assertTrue(f.events.isEmpty()); coVerify(exactly = 0) { f.repository.getBlockHash() }
    }
    @Test fun genesisConnectionFailureRetainsCandidateUrlInError() = runTest {
        val f = fixture(); val url = "wss://new.example.test/"; f.check(url); f.failed(url)
        assertEquals(url, (f.events.single() as NodeManagerEvent.ConnectionFailed).url)
        assertEquals(a.address, f.switches.last())
    }
    @Test fun unrelatedReconnectDoesNotEndGenesisCheck() = runTest {
        val f = fixture(); val url = "wss://new.example.test/"; f.check(url); f.failed(a.address)
        assertTrue(f.events.isEmpty()); f.connected(url)
        assertTrue((f.events.single() as NodeManagerEvent.GenesisValidated).result)
    }
    @Test fun newManualChoiceCancelsEarlierGenesisValidation() = runTest {
        val f = fixture(); val url = "wss://new.example.test/"; f.check(url); f.connect(b); f.connected(url)
        assertTrue(f.events.isEmpty()); assertEquals(b.address, f.switches.last())
        f.connected(b.address); assertEquals(b.address, f.selected.value?.address)
    }
    @Test fun newGenesisCheckCancelsEarlierManualSelection() = runTest {
        val f = fixture(); val url = "wss://new.example.test/"; f.connect(b); f.check(url); f.connected(b.address)
        assertTrue(f.events.isEmpty()); assertEquals(a.address, f.selected.value?.address)
        f.connected(url); assertEquals(1, f.events.size)
    }
    @Test fun autoSwitchDisabledLeavesRetryToSocket() = runTest {
        val f = fixture(); f.failed(a.address, 4)
        assertTrue(f.switches.isEmpty())
    }
    @Test fun autoSwitchWaitsForFourthAttempt() = runTest {
        val f = fixture(auto = true); for (i in 1..3) f.failed(a.address, i)
        assertTrue(f.switches.isEmpty()); f.failed(a.address, 4)
        assertEquals(listOf(b.address), f.switches)
    }
    @Test fun autoSwitchWrapsFromLastNodeToFirst() = runTest {
        val f = fixture(auto = true, initial = listOf(a.copy(isSelected = false), b, c.copy(isSelected = true)))
        f.failed(c.address, 4); assertEquals(listOf(a.address), f.switches)
    }
    @Test fun autoSwitchSkipsBlockedRows() = runTest {
        val blocked = b.copy(address = "wss://mof3.sora.org/")
        val f = fixture(auto = true, initial = listOf(a, blocked, c)); f.failed(a.address, 4)
        assertEquals(listOf(c.address), f.switches)
    }
    @Test fun noAvailableNodeDoesNotAttemptAnotherConnection() = runTest {
        val f = fixture(auto = true, initial = emptyList()); f.failed(a.address, 4)
        assertTrue(f.switches.isEmpty())
    }
    @Test fun allUnavailableNodesStopAfterOneCycleAndNotifyOnce() = runTest {
        val f = fixture(auto = true)
        f.failed(a.address, 4); f.failed(b.address, 4); f.failed(c.address, 4)
        f.failed(a.address, 8); f.failed(a.address, 12); f.failed(a.address, 16)
        assertEquals(listOf(b.address, c.address, a.address), f.switches)
        assertEquals(1, f.events.count { it === NodeManagerEvent.AllNodesUnavailable })
    }
    @Test fun successfulConnectionResetsExhaustedAutoSwitchCycle() = runTest {
        val f = fixture(auto = true)
        f.failed(a.address, 4); f.failed(b.address, 4); f.failed(c.address, 4); f.failed(a.address, 8)
        f.connected(a.address); f.failed(a.address, 4)
        assertEquals(b.address, f.switches.last())
    }
    @Test fun oldEndpointRetryCannotRotateCurrentAutoSelection() = runTest {
        val f = fixture(auto = true); f.failed(a.address, 4); f.failed(a.address, 8)
        assertEquals(listOf(b.address), f.switches)
    }
    @Test fun customGenesisCheckDoesNotTriggerDefaultAutoRotation() = runTest {
        val f = fixture(auto = true); val url = "wss://new.example.test/"; f.check(url); f.failed(url, 4)
        assertEquals(listOf(url, a.address), f.switches)
        assertEquals(a.address, f.selected.value?.address)
    }
    @Test fun manualSwitchWaitsForWalletMutationToFinish() = runTest {
        val f = fixture(); val release = CompletableDeferred<Unit>()
        val held = backgroundScope.launch { WalletMutationCoordinator.withLock { release.await() } }
        runCurrent(); f.connect(b); assertTrue(f.switches.isEmpty())
        release.complete(Unit); runCurrent(); held.join()
        assertEquals(listOf(b.address), f.switches)
    }
    @Test fun genesisProbeWaitsForWalletMutationToFinish() = runTest {
        val f = fixture(); val release = CompletableDeferred<Unit>()
        val held = backgroundScope.launch { WalletMutationCoordinator.withLock { release.await() } }
        runCurrent(); val url = "wss://new.example.test/"; f.check(url); assertTrue(f.switches.isEmpty())
        release.complete(Unit); runCurrent(); held.join()
        assertEquals(listOf(url), f.switches)
    }
    @Test fun lateGenesisResponseCannotRestoreOverNewManualChoice() = runTest {
        val f = fixture(); val response = CompletableDeferred<String>()
        coEvery { f.repository.getBlockHash() } coAnswers { response.await() }
        val url = "wss://new.example.test/"; f.check(url); f.connected(url)
        f.connect(b); response.complete("aabb"); runCurrent()
        assertTrue(f.events.isEmpty()); assertEquals(listOf(url, b.address), f.switches)
        f.connected(b.address); assertEquals(b.address, f.selected.value?.address)
    }
    @Test fun lateGenesisResponseCannotCompleteReplacementProbe() = runTest {
        val f = fixture(); val response = CompletableDeferred<String>()
        coEvery { f.repository.getBlockHash() } coAnswers { response.await() }
        val old = "wss://old.example.test/"; val current = "wss://new.example.test/"
        f.check(old); f.connected(old); f.check(current); response.complete("aabb"); runCurrent()
        assertTrue(f.events.isEmpty()); assertEquals(listOf(old, current), f.switches)
        f.connected(current); assertTrue((f.events.single() as NodeManagerEvent.GenesisValidated).result)
    }
    @Test fun queuedManualSelectionUsesLatestRequestAfterMutationBarrier() = runTest {
        val f = fixture(); val release = CompletableDeferred<Unit>()
        val held = backgroundScope.launch { WalletMutationCoordinator.withLock { release.await() } }
        runCurrent(); f.connect(b); f.connect(c); release.complete(Unit); runCurrent(); held.join()
        assertEquals(listOf(c.address), f.switches)
    }
    @Test fun queuedProbeCannotReplaceNewManualChoiceAfterMutationBarrier() = runTest {
        val f = fixture(); val release = CompletableDeferred<Unit>()
        val held = backgroundScope.launch { WalletMutationCoordinator.withLock { release.await() } }
        runCurrent(); f.check("wss://new.example.test/"); f.connect(b); release.complete(Unit); runCurrent(); held.join()
        assertEquals(listOf(b.address), f.switches)
    }

    @Test fun connectionSuccessIsNotPublishedBeforeSelectionPersists() = runTest {
        val f = fixture(); val release = CompletableDeferred<Unit>()
        coEvery { f.repository.selectNode(b) } coAnswers { release.await() }
        f.connect(b); f.connected(b.address)
        try { assertTrue(f.events.isEmpty()) } finally { release.complete(Unit); runCurrent() }
        assertEquals(b.address, (f.events.single() as NodeManagerEvent.Connected).url)
    }
    @Test fun failedSelectionPersistenceReportsFailureAndRestoresPreviousNode() = runTest {
        val f = fixture(); coEvery { f.repository.selectNode(b) } throws IllegalStateException("fixture database failure")
        f.connect(b); f.connected(b.address)
        assertEquals(b.address, (f.events.single() as NodeManagerEvent.ConnectionFailed).url)
        assertEquals(a.address, f.switches.last()); assertEquals(a.address, f.selected.value?.address)
    }
    @Test fun cancelledGenesisReadDoesNotBecomeInvalidGenesisResult() = runTest {
        val f = fixture(); coEvery { f.repository.getBlockHash() } throws kotlinx.coroutines.CancellationException("fixture shutdown")
        val url = "wss://new.example.test/"; f.check(url); f.connected(url)
        assertTrue(f.events.isEmpty())
    }

    @Test fun automaticSelectionPersistenceFailureRestoresConnectionAndKeepsObserverAlive() = runTest {
        val f = fixture(auto = true)
        coEvery { f.repository.selectNode(b) } throws IllegalStateException("fixture disk failure")
        f.failed(a.address, 4)
        assertEquals(listOf(b.address, a.address), f.switches)
        assertEquals(b.address, (f.events.single() as NodeManagerEvent.ConnectionFailed).url)
        assertEquals(a.address, f.selected.value?.address)
        f.connect(c); f.connected(c.address)
        assertEquals(c.address, f.selected.value?.address)
    }
    @Test fun queuedAutomaticSwitchCannotReplaceLaterManualChoice() = runTest {
        val f = fixture(auto = true); val release = CompletableDeferred<Unit>()
        val held = backgroundScope.launch { WalletMutationCoordinator.withLock { release.await() } }
        runCurrent(); f.failed(a.address, 4); f.connect(c)
        release.complete(Unit); runCurrent(); held.join()
        assertEquals(listOf(c.address), f.switches)
        coVerify(exactly = 0) { f.repository.selectNode(b) }
    }
    @Test fun removedSelectionIsNotRestoredAfterAnotherCandidateFails() = runTest {
        val f = fixture(); f.selected.value = null; f.nodes.value = listOf(b, c); runCurrent()
        f.connect(b); f.failed(b.address)
        assertEquals(listOf(b.address), f.switches)
        assertEquals(b.address, (f.events.single() as NodeManagerEvent.ConnectionFailed).url)
    }

    @Test fun manualFailureRestoresSelectionThatCompletedWhileRequestWaited() = runTest {
        val f = fixture(auto = true); val release = CompletableDeferred<Unit>()
        coEvery { f.repository.selectNode(b) } coAnswers {
            release.await()
            f.selected.value = b.copy(isSelected = true)
            f.nodes.value = listOf(a.copy(isSelected = false), b.copy(isSelected = true), c)
        }
        f.failed(a.address, 4); f.connect(c)
        assertEquals(listOf(b.address), f.switches)
        release.complete(Unit); runCurrent(); f.failed(c.address)
        assertEquals(listOf(b.address, c.address, b.address), f.switches)
        assertEquals(b.address, f.selected.value?.address)
        assertEquals(c.address, (f.events.single() as NodeManagerEvent.ConnectionFailed).url)
    }

}
