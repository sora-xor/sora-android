package jp.co.soramitsu.feature_wallet_impl.data.nexus

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.nexus.IrohaAddressCodec
import jp.co.soramitsu.common.nexus.NexusAssetBalance
import jp.co.soramitsu.common.nexus.NexusNetworks
import jp.co.soramitsu.common.nexus.NexusToriiException
import jp.co.soramitsu.common.nexus.NexusToriiReadClient
import jp.co.soramitsu.common.nexus.NexusTransferHistoryItem
import jp.co.soramitsu.common.nexus.NexusTransferHistorySlice
import jp.co.soramitsu.common.nexus.TairaTestnetContract
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
import jp.co.soramitsu.core_db.model.WalletIdentityLocal
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.ProductionFeatureManager
import jp.co.soramitsu.feature_blockexplorer_api.data.ProductionFeatureState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NexusPortfolioTairaVisibilityTest {
    private val database = mockk<AppDatabase>()
    private val dao = mockk<WalletIdentityDao>()
    private val userRepository = mockk<UserRepository>()
    private val torii = mockk<NexusToriiReadClient>()
    private val sendQualification = mockk<NexusSendQualification>()
    private val featureManager = mockk<ProductionFeatureManager>()
    private val features = MutableStateFlow(ProductionFeatureState(true, false, true, false, true, true, true))
    private val repository = NexusPortfolioRepository(database, userRepository, torii, sendQualification, featureManager)
    private val taira = account(WalletNetworkId.TAIRA)
    private val production = account(WalletNetworkId.MINAMOTO)

    private fun fixture(accounts: List<NetworkAccountLocal> = listOf(taira)) {
        every { database.walletIdentityDao() } returns dao
        every { userRepository.flowCurSoraAccount() } returns flowOf(SoraAccount("wallet", "Wallet"))
        every { featureManager.observeState() } returns features
        coEvery { featureManager.getState() } answers { features.value }
        coEvery { dao.getMigrationJournal(any()) } returns null
        coEvery { dao.getWallet("wallet") } returns WalletIdentityLocal("wallet", "Wallet", "MNEMONIC", "VERIFIED", 1)
        every { dao.observeEnabledNetworkAccounts() } returns flowOf(accounts)
        every { dao.observePendingTransactions("wallet") } returns flowOf(emptyList())
        coEvery { torii.getXorBalance(NexusNetworks.taira, taira.address) } returns
            NexusAssetBalance(asset = TairaTestnetContract.XOR_ASSET_DEFINITION_ID, quantity = "1")
        coEvery { torii.committedXorTransferPage(any(), any(), any(), any()) } returns
            NexusTransferHistorySlice(emptyList(), null)
        every { sendQualification.capabilitiesFor(any()) } returns NexusSendCapabilities.unavailable()
    }

    @Test
    fun `hidden canonical Taira row is removed before Torii reads`() = runTest {
        fixture()
        features.value = features.value.copy(tairaVisible = false)
        val results = mutableListOf<List<NexusPortfolioBalance>>()
        backgroundScope.launch { repository.observeCurrentWallet().collect { results += it } }
        runCurrent()
        assertTrue(results.all { it.isEmpty() })
        coVerify(exactly = 0) { torii.getXorBalance(any(), any()) }
    }

    @Test
    fun `a Taira balance shows while the capability request has not answered`() = runTest {
        fixture()
        every { sendQualification.capabilitiesFor(any()) } returns QUALIFIED
        // The PI indexer never answers (it has failed closed since 2026-10-31).
        coEvery { featureManager.getState() } coAnswers { awaitCancellation() }
        val emitted = mutableListOf<List<NexusPortfolioBalance>>()
        backgroundScope.launch { repository.observeWallet("wallet", history = false).collect { emitted += it } }
        runCurrent()
        val row = emitted.single().single()
        assertEquals("1", row.xorQuantity)
        assertEquals(taira.address, row.address)
        // Send is the one thing that waits for the capability switches, as it did before.
        assertFalse(row.sendAvailable)
        coVerify(exactly = 1) { torii.getXorBalance(any(), any()) }
    }

    @Test
    fun `Send appears when the capability request answers, without another balance read`() = runTest {
        fixture()
        every { sendQualification.capabilitiesFor(any()) } returns QUALIFIED
        val answer = CompletableDeferred<ProductionFeatureState>()
        coEvery { featureManager.getState() } coAnswers { answer.await() }
        val emitted = mutableListOf<List<NexusPortfolioBalance>>()
        backgroundScope.launch { repository.observeWallet("wallet", history = false).collect { emitted += it } }
        runCurrent()
        assertFalse(emitted.single().single().sendAvailable)
        answer.complete(features.value)
        runCurrent()
        assertEquals(2, emitted.size)
        assertEquals(emitted[0].single().copy(sendAvailable = true), emitted[1].single())
        coVerify(exactly = 1) { torii.getXorBalance(any(), any()) }
    }

    @Test
    fun `a capability request that fails lets Send through from the last capability set`() = runTest {
        fixture()
        every { sendQualification.capabilitiesFor(any()) } returns QUALIFIED
        val answer = CompletableDeferred<ProductionFeatureState>()
        coEvery { featureManager.getState() } coAnswers { answer.await() }
        var rows = emptyList<NexusPortfolioBalance>()
        backgroundScope.launch { repository.observeCurrentWallet().collect { rows = it } }
        runCurrent()
        assertEquals("1", rows.single().xorQuantity)
        assertFalse(rows.single().sendAvailable)
        answer.completeExceptionally(IllegalStateException("PI_INDEXER_NOT_READY"))
        runCurrent()
        assertTrue(rows.single().sendAvailable)
    }

    @Test
    fun `a capability answer that turns the network off never offers Send on rows shown before it`() = runTest {
        fixture()
        every { sendQualification.capabilitiesFor(any()) } returns QUALIFIED
        val answer = CompletableDeferred<ProductionFeatureState>()
        coEvery { featureManager.getState() } coAnswers {
            answer.await().also { features.value = it }
        }
        val emitted = mutableListOf<List<NexusPortfolioBalance>>()
        backgroundScope.launch { repository.observeWallet("wallet", history = false).collect { emitted += it } }
        runCurrent()
        assertEquals("1", emitted.single().single().xorQuantity)
        answer.complete(features.value.copy(nexusAvailable = false, tairaVisible = false))
        runCurrent()
        assertTrue(emitted.last().isEmpty())
        // The row read before the answer would have offered Send under the old capability set.
        assertTrue(emitted.flatten().none { it.sendAvailable })
    }

    @Test
    fun `Send is not given to rows whose journal has changed while the capability request was pending`() = runTest {
        fixture()
        every { sendQualification.capabilitiesFor(any()) } returns QUALIFIED
        val answer = CompletableDeferred<ProductionFeatureState>()
        coEvery { featureManager.getState() } coAnswers { answer.await() }
        val journal = MutableStateFlow(emptyList<jp.co.soramitsu.core_db.model.PendingNetworkTransactionLocal>())
        every { dao.observePendingTransactions("wallet") } returns journal
        val emitted = mutableListOf<List<NexusPortfolioBalance>>()
        backgroundScope.launch { repository.observeWallet("wallet", history = false).collect { emitted += it } }
        runCurrent()
        assertFalse(emitted.single().single().sendAvailable)
        // The journal now needs recovery, and the balance read that follows does not answer.
        val replacement = CompletableDeferred<NexusAssetBalance>()
        coEvery { torii.getXorBalance(NexusNetworks.taira, taira.address) } coAnswers { replacement.await() }
        journal.value = listOf(pendingSend().copy(chainId = null))
        runCurrent()
        answer.complete(features.value)
        runCurrent()
        // The row on screen was read before the journal changed: the capability answer must not
        // turn its Send on.
        assertTrue(emitted.flatten().none { it.sendAvailable })
        replacement.complete(NexusAssetBalance(asset = TairaTestnetContract.XOR_ASSET_DEFINITION_ID, quantity = "1"))
        runCurrent()
        assertEquals("NEXUS_PENDING_RECOVERY_REQUIRED", emitted.last().single().errorCode)
        assertTrue(emitted.flatten().none { it.sendAvailable })
    }

    @Test
    fun `a capability request the indexer rejects keeps Send as the last capability set decides`() = runTest {
        fixture()
        every { sendQualification.capabilitiesFor(any()) } returns QUALIFIED
        // The read answers "everything off" for itself; the saved capability set is unchanged.
        coEvery { featureManager.getState() } returns
            ProductionFeatureState(false, false, false, false, false, false, false)
        var rows = emptyList<NexusPortfolioBalance>()
        backgroundScope.launch { repository.observeCurrentWallet().collect { rows = it } }
        runCurrent()
        assertEquals("1", rows.single().xorQuantity)
        assertTrue(rows.single().sendAvailable)
    }

    @Test
    fun `one capability request serves every wallet's rows that asks while it runs`() = runTest {
        fixture()
        every { sendQualification.capabilitiesFor(any()) } returns QUALIFIED
        var answer = CompletableDeferred<ProductionFeatureState>()
        coEvery { featureManager.getState() } coAnswers { answer.await() }
        val first = mutableListOf<List<NexusPortfolioBalance>>()
        val second = mutableListOf<List<NexusPortfolioBalance>>()
        backgroundScope.launch { repository.observeCurrentWallet().collect { } }
        backgroundScope.launch { repository.observeWallet("wallet", history = false).collect { first += it } }
        backgroundScope.launch { repository.observeWallet("wallet", history = false).collect { second += it } }
        runCurrent()
        coVerify(exactly = 1) { featureManager.getState() }
        assertFalse(first.last().single().sendAvailable)
        answer.complete(features.value)
        runCurrent()
        assertTrue(first.last().single().sendAvailable)
        assertTrue(second.last().single().sendAvailable)
        coVerify(exactly = 1) { featureManager.getState() }
        // Each refresh asks once more, for all of them together.
        answer = CompletableDeferred()
        repository.refresh()
        runCurrent()
        coVerify(exactly = 2) { featureManager.getState() }
        answer.complete(features.value)
        runCurrent()
        // More history is no refresh: the capabilities are not asked for again.
        repository.loadMoreHistory("wallet", WalletNetworkId.TAIRA)
        runCurrent()
        coVerify(exactly = 2) { featureManager.getState() }
    }

    @Test
    fun `a subscription that starts after a capability read asks again and waits for its own answer`() = runTest {
        fixture()
        every { sendQualification.capabilitiesFor(any()) } returns QUALIFIED
        backgroundScope.launch { repository.observeWallet("wallet", history = false).collect { } }
        runCurrent()
        coVerify(exactly = 1) { featureManager.getState() }
        val answer = CompletableDeferred<ProductionFeatureState>()
        coEvery { featureManager.getState() } coAnswers { answer.await() }
        val late = mutableListOf<List<NexusPortfolioBalance>>()
        backgroundScope.launch { repository.observeWallet("wallet", history = false).collect { late += it } }
        runCurrent()
        coVerify(exactly = 2) { featureManager.getState() }
        assertFalse(late.single().single().sendAvailable)
        answer.complete(features.value)
        runCurrent()
        assertEquals(2, late.size)
        assertTrue(late.last().single().sendAvailable)
    }

    @Test
    fun `a refresh keeps Send on screen while the capabilities are asked for again`() = runTest {
        fixture()
        every { sendQualification.capabilitiesFor(any()) } returns QUALIFIED
        var pending: CompletableDeferred<ProductionFeatureState>? = null
        coEvery { featureManager.getState() } coAnswers { pending?.await() ?: features.value }
        val emitted = mutableListOf<List<NexusPortfolioBalance>>()
        backgroundScope.launch { repository.observeWallet("wallet", history = false).collect { emitted += it } }
        runCurrent()
        assertTrue(emitted.last().single().sendAvailable)
        pending = CompletableDeferred()
        repository.refresh()
        runCurrent()
        assertTrue(emitted.all { rows -> rows.single().sendAvailable })
        coVerify(exactly = 2) { torii.getXorBalance(any(), any()) }
        pending?.complete(features.value)
        runCurrent()
        assertTrue(emitted.last().single().sendAvailable)
    }

    @Test
    fun `a refresh offers Send nowhere new before its own capability request answers`() = runTest {
        fixture()
        var pending: CompletableDeferred<ProductionFeatureState>? = null
        coEvery { featureManager.getState() } coAnswers { pending?.await() ?: features.value }
        val emitted = mutableListOf<List<NexusPortfolioBalance>>()
        backgroundScope.launch { repository.observeWallet("wallet", history = false).collect { emitted += it } }
        runCurrent()
        // The first capability request has answered; this wallet does not qualify to send yet.
        assertFalse(emitted.last().single().sendAvailable)
        every { sendQualification.capabilitiesFor(any()) } returns QUALIFIED
        pending = CompletableDeferred()
        repository.refresh()
        runCurrent()
        // Read again at once, and it qualifies now: Send still waits for this refresh's answer.
        coVerify(exactly = 2) { torii.getXorBalance(any(), any()) }
        assertTrue(emitted.none { rows -> rows.single().sendAvailable })
        pending?.complete(features.value)
        runCurrent()
        assertTrue(emitted.last().single().sendAvailable)
        coVerify(exactly = 2) { torii.getXorBalance(any(), any()) }
    }

    @Test
    fun `a refresh takes Send off at once where the capability set no longer allows it`() = runTest {
        fixture()
        every { sendQualification.capabilitiesFor(any()) } returns QUALIFIED
        var pending: CompletableDeferred<ProductionFeatureState>? = null
        coEvery { featureManager.getState() } coAnswers { pending?.await() ?: features.value }
        var rows = emptyList<NexusPortfolioBalance>()
        backgroundScope.launch { repository.observeWallet("wallet", history = false).collect { rows = it } }
        runCurrent()
        assertTrue(rows.single().sendAvailable)
        every { sendQualification.capabilitiesFor(any()) } returns NexusSendCapabilities.unavailable()
        pending = CompletableDeferred()
        repository.refresh()
        runCurrent()
        assertFalse(rows.single().sendAvailable)
    }

    @Test
    fun `a Send decision held while new inputs wait gives the rows before them nothing`() = runTest {
        fixture()
        every { sendQualification.capabilitiesFor(any()) } returns QUALIFIED
        val journal = MutableStateFlow(emptyList<jp.co.soramitsu.core_db.model.PendingNetworkTransactionLocal>())
        every { dao.observePendingTransactions("wallet") } returns journal
        val answer = CompletableDeferred<ProductionFeatureState>()
        coEvery { featureManager.getState() } coAnswers { answer.await() }
        // Reading the capability set as it is now can be held, as on a busy phone.
        var held: CompletableDeferred<Unit>? = null
        val deciding = CompletableDeferred<Unit>()
        every { featureManager.observeState() } answers {
            kotlinx.coroutines.flow.flow {
                held?.let { deciding.complete(Unit); it.await() }
                emitAll(features)
            }
        }
        val emitted = mutableListOf<List<NexusPortfolioBalance>>()
        backgroundScope.launch { repository.observeWallet("wallet", history = false).collect { emitted += it } }
        runCurrent()
        assertFalse(emitted.last().single().sendAvailable)
        val gate = CompletableDeferred<Unit>()
        held = gate
        answer.complete(features.value)
        runCurrent()
        assertTrue(deciding.isCompleted)
        // The journal changes while Send is being decided for the rows read before it.
        held = null
        journal.value = listOf(pendingSend().copy(chainId = null))
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertTrue(emitted.none { rows -> rows.any { it.sendAvailable } })
        assertEquals("NEXUS_PENDING_RECOVERY_REQUIRED", emitted.last().single().errorCode)
    }

    @Test
    fun `rows whose inputs change while their Send is being decided are not shown`() = runTest {
        fixture()
        every { sendQualification.capabilitiesFor(any()) } returns QUALIFIED
        val journal = MutableStateFlow(emptyList<jp.co.soramitsu.core_db.model.PendingNetworkTransactionLocal>())
        every { dao.observePendingTransactions("wallet") } returns journal
        // The capability request answers at once; the balance read is held, so the rows come after it.
        var balanceGate = CompletableDeferred<Unit>()
        coEvery { torii.getXorBalance(NexusNetworks.taira, taira.address) } coAnswers {
            balanceGate.await()
            NexusAssetBalance(asset = TairaTestnetContract.XOR_ASSET_DEFINITION_ID, quantity = "1")
        }
        var held: CompletableDeferred<Unit>? = null
        val deciding = CompletableDeferred<Unit>()
        every { featureManager.observeState() } answers {
            kotlinx.coroutines.flow.flow {
                held?.let { deciding.complete(Unit); it.await() }
                emitAll(features)
            }
        }
        val emitted = mutableListOf<List<NexusPortfolioBalance>>()
        backgroundScope.launch { repository.observeWallet("wallet", history = false).collect { emitted += it } }
        runCurrent()
        assertTrue(emitted.isEmpty())
        // The rows arrive, and Send is being decided for them when the journal changes.
        val gate = CompletableDeferred<Unit>()
        held = gate
        val first = balanceGate
        balanceGate = CompletableDeferred()
        first.complete(Unit)
        runCurrent()
        assertTrue(deciding.isCompleted)
        held = null
        journal.value = listOf(pendingSend().copy(chainId = null))
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        // Read from the journal before: never shown, with or without Send.
        assertTrue(emitted.isEmpty())
        balanceGate.complete(Unit)
        runCurrent()
        assertTrue(emitted.none { rows -> rows.any { it.sendAvailable } })
        assertEquals("NEXUS_PENDING_RECOVERY_REQUIRED", emitted.last().single().errorCode)
    }

    @Test
    fun `a refresh's capability answer gives no Send to the rows read before that refresh`() = runTest {
        fixture()
        every { sendQualification.capabilitiesFor(any()) } returns QUALIFIED
        var calls = 0
        coEvery { featureManager.getState() } coAnswers { if (calls++ == 0) awaitCancellation() else features.value }
        var balanceGate: CompletableDeferred<Unit>? = null
        coEvery { torii.getXorBalance(NexusNetworks.taira, taira.address) } coAnswers {
            balanceGate?.await()
            NexusAssetBalance(asset = TairaTestnetContract.XOR_ASSET_DEFINITION_ID, quantity = "1")
        }
        val emitted = mutableListOf<List<NexusPortfolioBalance>>()
        backgroundScope.launch { repository.observeWallet("wallet", history = false).collect { emitted += it } }
        runCurrent()
        // The first capability request has not answered: the balance shows, Send does not.
        assertEquals(1, emitted.size)
        assertFalse(emitted.single().single().sendAvailable)
        // A refresh: its capability request answers at once, its balance read is held.
        balanceGate = CompletableDeferred()
        repository.refresh()
        runCurrent()
        assertEquals(2, calls)
        assertTrue(emitted.none { rows -> rows.single().sendAvailable })
        balanceGate?.complete(Unit)
        runCurrent()
        assertTrue(emitted.last().single().sendAvailable)
    }

    @Test
    fun `a refresh that begins while Send is being decided gives those rows no new Send`() = runTest {
        fixture()
        every { sendQualification.capabilitiesFor(any()) } returns QUALIFIED
        // The first capability request answers at once; the refresh's never does.
        var calls = 0
        coEvery { featureManager.getState() } coAnswers { if (calls++ == 0) features.value else awaitCancellation() }
        var balanceGate: CompletableDeferred<Unit>? = CompletableDeferred()
        coEvery { torii.getXorBalance(NexusNetworks.taira, taira.address) } coAnswers {
            balanceGate?.await()
            NexusAssetBalance(asset = TairaTestnetContract.XOR_ASSET_DEFINITION_ID, quantity = "1")
        }
        var held: CompletableDeferred<Unit>? = null
        val deciding = CompletableDeferred<Unit>()
        every { featureManager.observeState() } answers {
            kotlinx.coroutines.flow.flow {
                held?.let { deciding.complete(Unit); it.await() }
                emitAll(features)
            }
        }
        val emitted = mutableListOf<List<NexusPortfolioBalance>>()
        backgroundScope.launch { repository.observeWallet("wallet", history = false).collect { emitted += it } }
        runCurrent()
        assertTrue(emitted.isEmpty())
        // The rows arrive after the first capability answer, and Send is being decided for them.
        val gate = CompletableDeferred<Unit>()
        held = gate
        val first = checkNotNull(balanceGate)
        balanceGate = null
        first.complete(Unit)
        runCurrent()
        assertTrue(deciding.isCompleted)
        held = null
        // The decision's feature read returns, and a refresh begins before its inputs are taken in.
        gate.complete(Unit)
        repository.refresh()
        runCurrent()
        assertTrue(emitted.isNotEmpty())
        assertTrue(emitted.none { rows -> rows.any { it.sendAvailable } })
    }

    @Test
    fun `a subscription cancelled during the capability request leaves it to the next one`() = runTest {
        fixture()
        every { sendQualification.capabilitiesFor(any()) } returns QUALIFIED
        var calls = 0
        coEvery { featureManager.getState() } coAnswers { if (calls++ == 0) awaitCancellation() else features.value }
        val first = launch { repository.observeWallet("wallet", history = false).collect { } }
        runCurrent()
        var rows = emptyList<NexusPortfolioBalance>()
        backgroundScope.launch { repository.observeWallet("wallet", history = false).collect { rows = it } }
        runCurrent()
        assertFalse(rows.single().sendAvailable)
        first.cancel()
        runCurrent()
        assertTrue(rows.single().sendAvailable)
        assertEquals(2, calls)
    }

    @Test
    fun `production address remains copyable without any production Torii reads`() = runTest {
        fixture(listOf(production))
        var rows = emptyList<NexusPortfolioBalance>()
        backgroundScope.launch { repository.observeCurrentWallet().collect { rows = it } }
        runCurrent()
        assertEquals(production.address, rows.single().address)
        assertFalse(rows.single().networkLive)
        assertFalse(rows.single().sendAvailable)
        assertEquals(null, rows.single().xorQuantity)
        coVerify(exactly = 0) { torii.getXorBalance(any(), any()) }
        coVerify(exactly = 0) { torii.committedXorTransferPage(any(), any(), any(), any()) }
    }

    @Test
    fun `manual refresh observes an incoming transfer without a local database change`() = runTest {
        fixture()
        var quantity = "1"
        coEvery { torii.getXorBalance(NexusNetworks.taira, taira.address) } answers {
            NexusAssetBalance(asset = TairaTestnetContract.XOR_ASSET_DEFINITION_ID, quantity = quantity)
        }
        var rows = emptyList<NexusPortfolioBalance>()
        backgroundScope.launch { repository.observeCurrentWallet().collect { rows = it } }
        runCurrent()
        assertEquals("1", rows.single().xorQuantity)
        quantity = "2"
        repository.refresh()
        runCurrent()
        assertEquals("2", rows.single().xorQuantity)
    }

    @Test
    fun `global feature disable and reenable update the open wallet without switching accounts`() = runTest {
        fixture(listOf(production, taira))
        var rows = emptyList<NexusPortfolioBalance>()
        backgroundScope.launch { repository.observeCurrentWallet().collect { rows = it } }
        runCurrent()
        assertEquals(2, rows.size)
        features.value = features.value.copy(nexusAvailable = false, tairaVisible = false)
        runCurrent()
        assertTrue(rows.isEmpty())
        features.value = features.value.copy(nexusAvailable = true, tairaVisible = true)
        runCurrent()
        assertEquals(2, rows.size)
    }

    @Test
    fun `wallet starting disabled remains subscribed for later enablement`() = runTest {
        fixture()
        features.value = features.value.copy(nexusAvailable = false)
        var rows = emptyList<NexusPortfolioBalance>()
        backgroundScope.launch { repository.observeCurrentWallet().collect { rows = it } }
        runCurrent()
        assertTrue(rows.isEmpty())
        features.value = features.value.copy(nexusAvailable = true)
        runCurrent()
        assertEquals(taira.address, rows.single().address)
    }

    @Test
    fun `large history loads a bounded page and expands only on user request`() = runTest {
        fixture()
        fun transfer(hash: String) = NexusTransferHistoryItem(hash.repeat(64), 1, "1", taira.address, taira.address)
        coEvery { torii.committedXorTransferPage(any(), any(), any(), null) } returns
            NexusTransferHistorySlice(listOf(transfer("a")), "page2", setOf("a:0"))
        coEvery { torii.committedXorTransferPage(any(), any(), any(), "page2") } returns
            NexusTransferHistorySlice(listOf(transfer("b")), "page3", setOf("b:0"))
        var rows = emptyList<NexusPortfolioBalance>()
        backgroundScope.launch { repository.observeCurrentWallet().collect { rows = it } }
        runCurrent()
        assertEquals(1, rows.single().confirmedTransfers.size)
        assertTrue(rows.single().historyHasMore)
        coVerify(exactly = 1) { torii.committedXorTransferPage(any(), any(), any(), any()) }
        repository.loadMoreHistory("wallet", WalletNetworkId.TAIRA)
        runCurrent()
        assertEquals(2, rows.single().confirmedTransfers.size)
        coVerify(exactly = 2) { torii.committedXorTransferPage(any(), any(), any(), any()) }
        coVerify(exactly = 1) { torii.getXorBalance(any(), any()) }
        coVerify(exactly = 0) { torii.committedXorTransferPage(any(), any(), any(), "page3") }
        repository.refresh()
        runCurrent()
        assertEquals(1, rows.single().confirmedTransfers.size)
        coVerify(exactly = 3) { torii.committedXorTransferPage(any(), any(), any(), any()) }
        coVerify(exactly = 2) { torii.committedXorTransferPage(any(), any(), any(), null) }
        coVerify(exactly = 2) { torii.getXorBalance(any(), any()) }
    }

    @Test
    fun `identity change discards cached pages and balance`() = runTest {
        fixture()
        val accounts = MutableStateFlow(listOf(taira))
        every { dao.observeEnabledNetworkAccounts() } returns accounts
        val changed = taira.copy(publicKey = "02".repeat(32),
            address = IrohaAddressCodec.encode(ByteArray(32) { 2 }, NexusNetworks.taira.chainDiscriminant))
        val old = NexusTransferHistoryItem("a".repeat(64), 1, "1", taira.address, taira.address)
        coEvery { torii.getXorBalance(any(), any()) } returns NexusAssetBalance(
            asset = TairaTestnetContract.XOR_ASSET_DEFINITION_ID, quantity = "1")
        coEvery { torii.committedXorTransferPage(any(), taira.address, any(), null) } returns
            NexusTransferHistorySlice(listOf(old), "page2", setOf("old"))
        coEvery { torii.committedXorTransferPage(any(), changed.address, any(), null) } returns
            NexusTransferHistorySlice(emptyList(), null)
        val emitted = mutableListOf<List<NexusPortfolioBalance>>()
        backgroundScope.launch { repository.observeCurrentWallet().collect { emitted += it } }
        runCurrent()
        repository.loadMoreHistory("wallet", WalletNetworkId.TAIRA)
        runCurrent()
        accounts.value = listOf(changed)
        runCurrent()
        assertEquals(changed.address, emitted.last().single().address)
        // The other address's history is never shown under this one, not even while it loads.
        assertTrue(emitted.flatten().filter { it.address == changed.address }.none { old in it.confirmedTransfers })
        coVerify(exactly = 1) { torii.getXorBalance(NexusNetworks.taira, changed.address) }
        coVerify(exactly = 1) { torii.committedXorTransferPage(any(), changed.address, any(), null) }
        coVerify(exactly = 0) { torii.committedXorTransferPage(any(), changed.address, any(), "page2") }
    }

    @Test
    fun `balance and Send show while the history is still being read`() = runTest {
        fixture()
        every { sendQualification.capabilitiesFor(any()) } returns QUALIFIED
        val history = CompletableDeferred<NexusTransferHistorySlice>()
        coEvery { torii.committedXorTransferPage(any(), any(), any(), null) } coAnswers { history.await() }
        val emitted = mutableListOf<List<NexusPortfolioBalance>>()
        backgroundScope.launch { repository.observeCurrentWallet().collect { emitted += it } }
        runCurrent()

        val early = emitted.last().single()
        assertEquals("1", early.xorQuantity)
        assertTrue(early.sendAvailable)
        assertTrue(early.historyLoading)
        assertTrue(early.confirmedTransfers.isEmpty())
        assertFalse(early.historyHasMore)

        val transfer = NexusTransferHistoryItem("a".repeat(64), 1, "1", taira.address, taira.address)
        history.complete(NexusTransferHistorySlice(listOf(transfer), null, setOf("a")))
        runCurrent()
        assertEquals(early.copy(confirmedTransfers = listOf(transfer), historyLoading = false), emitted.last().single())
    }

    @Test
    fun `rows shown before their history keep every Send check`() = runTest {
        fixture()
        every { sendQualification.capabilitiesFor(any()) } returns QUALIFIED
        val journal = MutableStateFlow(emptyList<jp.co.soramitsu.core_db.model.PendingNetworkTransactionLocal>())
        every { dao.observePendingTransactions("wallet") } returns journal
        // The history never answers: every row below is one shown before it.
        coEvery { torii.committedXorTransferPage(any(), any(), any(), any()) } coAnswers { awaitCancellation() }
        var rows = emptyList<NexusPortfolioBalance>()
        backgroundScope.launch { repository.observeCurrentWallet().collect { rows = it } }
        runCurrent()
        assertTrue(rows.single().historyLoading)
        assertTrue(rows.single().sendAvailable)

        features.value = features.value.copy(tairaAvailable = false)
        runCurrent()
        assertTrue(rows.single().historyLoading)
        assertEquals("1", rows.single().xorQuantity)
        assertFalse(rows.single().sendAvailable)

        features.value = features.value.copy(tairaAvailable = true)
        journal.value = listOf(pendingSend().copy(chainId = null))
        runCurrent()
        assertTrue(rows.single().historyLoading)
        assertFalse(rows.single().sendAvailable)
        assertEquals("NEXUS_PENDING_RECOVERY_REQUIRED", rows.single().errorCode)

        journal.value = listOf(pendingSend())
        every { sendQualification.capabilitiesFor(any()) } returns NexusSendCapabilities.unavailable()
        repository.refresh()
        runCurrent()
        assertTrue(rows.single().historyLoading)
        assertEquals(1, rows.single().pendingTransactions.size)
        assertFalse(rows.single().sendAvailable)

        coEvery { torii.getXorBalance(any(), any()) } throws NexusToriiException("NEXUS_XOR_DEFINITION_INVALID")
        every { sendQualification.capabilitiesFor(any()) } returns QUALIFIED
        repository.refresh()
        runCurrent()
        assertFalse(rows.single().sendAvailable)
        assertEquals("NEXUS_XOR_DEFINITION_INVALID", rows.single().errorCode)
    }

    @Test
    fun `empty history ranges are followed until a page has transfers`() = runTest {
        fixture()
        val transfer = NexusTransferHistoryItem("a".repeat(64), 1, "1", taira.address, taira.address)
        coEvery { torii.committedXorTransferPage(any(), any(), any(), null) } returns
            NexusTransferHistorySlice(emptyList(), "range2")
        coEvery { torii.committedXorTransferPage(any(), any(), any(), "range2") } returns
            NexusTransferHistorySlice(emptyList(), "range3")
        coEvery { torii.committedXorTransferPage(any(), any(), any(), "range3") } returns
            NexusTransferHistorySlice(listOf(transfer), "range4", setOf("a"))
        var rows = emptyList<NexusPortfolioBalance>()
        backgroundScope.launch { repository.observeCurrentWallet().collect { rows = it } }
        runCurrent()

        assertEquals(listOf(transfer), rows.single().confirmedTransfers)
        assertTrue(rows.single().historyHasMore)
        assertFalse(rows.single().historyLoading)
        coVerify(exactly = 3) { torii.committedXorTransferPage(any(), any(), any(), any()) }
        coVerify(exactly = 0) { torii.committedXorTransferPage(any(), any(), any(), "range4") }
    }

    @Test
    fun `a page of recent activity stops after twelve empty ranges and Show more goes on`() = runTest {
        fixture()
        coEvery { torii.committedXorTransferPage(any(), any(), any(), any()) } answers {
            val range = arg<String?>(3)?.removePrefix("range")?.toInt() ?: 0
            NexusTransferHistorySlice(emptyList(), "range${range + 1}")
        }
        var rows = emptyList<NexusPortfolioBalance>()
        backgroundScope.launch { repository.observeCurrentWallet().collect { rows = it } }
        runCurrent()

        assertTrue(rows.single().confirmedTransfers.isEmpty())
        assertTrue(rows.single().historyHasMore)
        assertFalse(rows.single().historyLoading)
        coVerify(exactly = 12) { torii.committedXorTransferPage(any(), any(), any(), any()) }

        repository.loadMoreHistory("wallet", WalletNetworkId.TAIRA)
        runCurrent()
        coVerify(exactly = 24) { torii.committedXorTransferPage(any(), any(), any(), any()) }
        coVerify(exactly = 1) { torii.committedXorTransferPage(any(), any(), any(), "range12") }
        coVerify(exactly = 1) { torii.getXorBalance(any(), any()) }
    }

    @Test
    fun `a wallet read without history shows its balance and Send and never asks for history`() = runTest {
        fixture()
        every { sendQualification.capabilitiesFor(any()) } returns QUALIFIED
        coEvery { torii.committedXorTransferPage(any(), any(), any(), any()) } returns
            NexusTransferHistorySlice(emptyList(), "range2")
        val emitted = mutableListOf<List<NexusPortfolioBalance>>()
        backgroundScope.launch { repository.observeWallet("wallet", history = false).collect { emitted += it } }
        runCurrent()

        val row = emitted.single().single()
        assertEquals("1", row.xorQuantity)
        assertTrue(row.sendAvailable)
        assertFalse(row.historyLoading)
        assertFalse(row.historyHasMore)
        repository.refresh()
        runCurrent()
        coVerify(exactly = 2) { torii.getXorBalance(any(), any()) }
        coVerify(exactly = 0) { torii.committedXorTransferPage(any(), any(), any(), any()) }
    }

    @Test
    fun `a refresh keeps the history on screen while it is read again`() = runTest {
        fixture()
        val old = NexusTransferHistoryItem("a".repeat(64), 1, "1", taira.address, taira.address)
        val fresh = old.copy(transactionHash = "f".repeat(64))
        val second = CompletableDeferred<NexusTransferHistorySlice>()
        var calls = 0
        coEvery { torii.committedXorTransferPage(any(), any(), any(), null) } coAnswers {
            if (calls++ == 0) NexusTransferHistorySlice(listOf(old), "page2", setOf("old")) else second.await()
        }
        var rows = emptyList<NexusPortfolioBalance>()
        backgroundScope.launch { repository.observeCurrentWallet().collect { rows = it } }
        runCurrent()
        repository.refresh()
        runCurrent()
        assertTrue(rows.single().historyLoading)
        assertEquals(listOf(old), rows.single().confirmedTransfers)
        assertTrue(rows.single().historyHasMore)

        second.complete(NexusTransferHistorySlice(listOf(fresh), null, setOf("fresh")))
        runCurrent()
        assertEquals(listOf(fresh), rows.single().confirmedTransfers)
        assertFalse(rows.single().historyHasMore)
        assertFalse(rows.single().historyLoading)
    }

    @Test
    fun `a transfer that leaves the journal stays listed until the history that holds it is read`() =
        runTest {
            fixture()
            val send = pendingSend()
            val journal = MutableStateFlow(listOf(send))
            every { dao.observePendingTransactions("wallet") } returns journal
            val confirmed = NexusTransferHistoryItem(checkNotNull(send.transactionHash), 2, "1", taira.address, send.recipient)
            val second = CompletableDeferred<NexusTransferHistorySlice>()
            var calls = 0
            coEvery { torii.committedXorTransferPage(any(), any(), any(), null) } coAnswers {
                if (calls++ == 0) NexusTransferHistorySlice(emptyList(), null) else second.await()
            }
            var rows = emptyList<NexusPortfolioBalance>()
            backgroundScope.launch { repository.observeCurrentWallet().collect { rows = it } }
            runCurrent()
            assertEquals(listOf(send.localId), rows.single().pendingTransactions.map { it.localId })

            // Reconciliation finalized it: the journal no longer lists it.
            journal.value = emptyList()
            runCurrent()
            assertEquals(listOf(send.localId), rows.single().pendingTransactions.map { it.localId })

            second.complete(NexusTransferHistorySlice(listOf(confirmed), null, setOf("confirmed")))
            runCurrent()
            assertTrue(rows.single().pendingTransactions.isEmpty())
            assertEquals(listOf(confirmed), rows.single().confirmedTransfers)
        }

    @Test
    fun `a row that turns Send off is shown at once even when a transfer left the journal`() = runTest {
        fixture()
        every { sendQualification.capabilitiesFor(any()) } returns QUALIFIED
        val journal = MutableStateFlow(listOf(pendingSend()))
        every { dao.observePendingTransactions("wallet") } returns journal
        val second = CompletableDeferred<NexusTransferHistorySlice>()
        var calls = 0
        coEvery { torii.committedXorTransferPage(any(), any(), any(), null) } coAnswers {
            if (calls++ == 0) NexusTransferHistorySlice(emptyList(), null) else second.await()
        }
        var rows = emptyList<NexusPortfolioBalance>()
        backgroundScope.launch { repository.observeCurrentWallet().collect { rows = it } }
        runCurrent()
        assertTrue(rows.single().sendAvailable)

        journal.value = emptyList()
        features.value = features.value.copy(tairaAvailable = false)
        runCurrent()
        assertFalse(rows.single().sendAvailable)
        assertTrue(rows.single().historyLoading)
        assertTrue(rows.single().pendingTransactions.isEmpty())
    }

    @Test
    fun `refresh cancellation cannot append a late page to the new snapshot`() = runTest {
        fixture()
        val release = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val old = NexusTransferHistoryItem("a".repeat(64), 1, "1", taira.address, taira.address)
        val fresh = old.copy(transactionHash = "f".repeat(64))
        var firstPage = NexusTransferHistorySlice(listOf(old), "page2", setOf("old"))
        coEvery { torii.committedXorTransferPage(any(), any(), any(), null) } answers { firstPage }
        coEvery { torii.committedXorTransferPage(any(), any(), any(), "page2") } coAnswers {
            started.complete(Unit)
            withContext(NonCancellable) { release.await() }
            NexusTransferHistorySlice(listOf(old.copy(amount = "2")), null, setOf("late"))
        }
        var rows = emptyList<NexusPortfolioBalance>()
        backgroundScope.launch { repository.observeCurrentWallet().collect { rows = it } }
        runCurrent()
        repository.loadMoreHistory("wallet", WalletNetworkId.TAIRA)
        runCurrent()
        assertTrue(started.isCompleted)
        firstPage = NexusTransferHistorySlice(listOf(fresh), null, setOf("fresh"))
        repository.refresh()
        runCurrent()
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf(fresh), rows.single().confirmedTransfers)
        assertFalse(rows.single().historyHasMore)
        coVerify(exactly = 3) { torii.committedXorTransferPage(any(), any(), any(), any()) }
    }

    @Test
    fun `repeated cursor does not append or replace the validated first page`() = runTest {
        fixture()
        val first = NexusTransferHistoryItem("a".repeat(64), 1, "1", taira.address, taira.address)
        coEvery { torii.committedXorTransferPage(any(), any(), any(), null) } returns
            NexusTransferHistorySlice(listOf(first), "page2", setOf("first"))
        coEvery { torii.committedXorTransferPage(any(), any(), any(), "page2") } returns
            NexusTransferHistorySlice(listOf(first.copy(amount = "2")), "page2", setOf("second"))
        var rows = emptyList<NexusPortfolioBalance>()
        backgroundScope.launch { repository.observeCurrentWallet().collect { rows = it } }
        runCurrent()
        repository.loadMoreHistory("wallet", WalletNetworkId.TAIRA)
        runCurrent()
        assertEquals(listOf(first), rows.single().confirmedTransfers)
        assertEquals("NEXUS_HISTORY_UNAVAILABLE", rows.single().historyErrorCode)
    }

    @Test
    fun `portfolio preserves a typed fanout deployment health diagnostic`() = runTest {
        fixture()
        coEvery { torii.getXorBalance(any(), any()) } throws NexusToriiException("NEXUS_FANOUT_ROUTE_UNAVAILABLE")
        var rows = emptyList<NexusPortfolioBalance>()
        backgroundScope.launch { repository.observeCurrentWallet().collect { rows = it } }
        runCurrent()
        assertEquals("NEXUS_FANOUT_ROUTE_UNAVAILABLE", rows.single().errorCode)
        assertFalse(rows.single().sendAvailable)
    }

    private fun account(id: WalletNetworkId): NetworkAccountLocal {
        val network = NexusNetworks.require(id)
        return NetworkAccountLocal("wallet", id.wireId, "01".repeat(32),
            IrohaAddressCodec.encode(ByteArray(32) { 1 }, network.chainDiscriminant),
            network.derivationPath, 1, true)
    }

    /** A committed Taira send waiting for its history proof, as the journal stores it. */
    private fun pendingSend() = jp.co.soramitsu.core_db.model.PendingNetworkTransactionLocal(
        localId = "${TairaTestnetContract.PENDING_JOURNAL_PREFIX}send",
        walletId = "wallet",
        networkId = WalletNetworkId.TAIRA.wireId,
        chainId = TairaTestnetContract.CHAIN_ID,
        transactionHash = "ab".repeat(32),
        assetId = TairaTestnetContract.XOR_ASSET_DEFINITION_ID,
        amount = "1",
        recipient = IrohaAddressCodec.encode(ByteArray(32) { 2 }, NexusNetworks.taira.chainDiscriminant),
        state = "COMMITTED_PENDING_RECONCILIATION",
        submissionIsAmbiguous = false,
        createdAt = 1,
        updatedAt = 1,
    )

    private companion object {
        val QUALIFIED = NexusSendCapabilities(NexusCapabilityStatus.QUALIFIED, NexusCapabilityStatus.QUALIFIED)
    }
}
