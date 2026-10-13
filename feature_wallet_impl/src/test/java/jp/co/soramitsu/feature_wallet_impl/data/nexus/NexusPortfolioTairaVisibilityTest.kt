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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
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
        coEvery { torii.getXorBalance(any(), any()) } returns NexusAssetBalance(
            asset = TairaTestnetContract.XOR_ASSET_DEFINITION_ID, quantity = "1")
        coEvery { torii.committedXorTransferPage(any(), any(), any(), null) } returns
            NexusTransferHistorySlice(emptyList(), "page2")
        var rows = emptyList<NexusPortfolioBalance>()
        backgroundScope.launch { repository.observeCurrentWallet().collect { rows = it } }
        runCurrent()
        repository.loadMoreHistory("wallet", WalletNetworkId.TAIRA)
        runCurrent()
        val changed = taira.copy(publicKey = "02".repeat(32),
            address = IrohaAddressCodec.encode(ByteArray(32) { 2 }, NexusNetworks.taira.chainDiscriminant))
        accounts.value = listOf(changed)
        runCurrent()
        assertEquals(changed.address, rows.single().address)
        coVerify(exactly = 1) { torii.getXorBalance(NexusNetworks.taira, changed.address) }
        coVerify(exactly = 1) { torii.committedXorTransferPage(any(), changed.address, any(), null) }
        coVerify(exactly = 0) { torii.committedXorTransferPage(any(), changed.address, any(), "page2") }
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
}
