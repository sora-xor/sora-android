package jp.co.soramitsu.feature_blockexplorer_impl.data

import io.mockk.*
import java.math.BigDecimal
import java.net.UnknownHostException
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal
import jp.co.soramitsu.feature_blockexplorer_api.data.*
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.*
import jp.co.soramitsu.feature_blockexplorer_impl.testdata.TestTransactions
import jp.co.soramitsu.sora.substrate.substrate.*
import jp.co.soramitsu.test_data.TestAccounts
import jp.co.soramitsu.test_data.TestTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*

/** Public history reads with real checkpoint validation/mapping and inert transport/storage boundaries. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HistoryReadAndActivityFlowTest {
    @get:Rule val main = MainCoroutineRule()
    private val account = TestAccounts.soraAccount
    private val address = account.substrateAddress
    private val item = TestTransactions.txHistoryItem
    private val tokens = listOf(TestTokens.xorToken, TestTokens.valToken, TestTokens.pswapToken)
    private val client = mockk<PolkaswapIndexerClient>()
    private val extrinsics = mockk<ExtrinsicManager>(relaxed = true)
    private val rpc = mockk<SubstrateCalls>()
    private val cache = mockk<PiValidatedHistoryCache>(relaxed = true)
    private val dao = mockk<WalletIdentityDao>()
    private val database = mockk<AppDatabase> { every { walletIdentityDao() } returns dao }
    private val details = mockk<Sora2SubmissionDetailsStore>(relaxed = true)
    private val coordinator = mockk<Sora2PendingSubmissionCoordinator>(relaxed = true)
    private val health = mockk<PiIndexerHealth>()
    private lateinit var repository: TransactionHistoryRepositoryImpl
    private var rows = emptyList<Sora2PendingSubmissionLocal>()
    private var descriptions = emptyList<Sora2SubmissionDetails>()
    private val localHash = "0x" + "a1".repeat(32)
    @Before fun setup() {
        mockkObject(FirebaseWrapper)
        every { FirebaseWrapper.recordErrorClass(any()) } just Runs
        every { health.latestIndexedBlock } returns null
        every { health.latestIndexedBlockHash } returns null
        every { health.workerLatestIndexedBlock } returns "100"
        every { health.workerLatestFinalizedBlock } returns "104"
        coEvery { rpc.getBlockHash(100) } returns item.blockHash!!
        every { dao.observeSora2PendingSubmissionChanges() } returns emptyFlow()
        coEvery { dao.getSora2SubmissionOverlayRows(address) } answers { rows }
        coEvery { details.list(address) } answers { descriptions }
        coEvery { cache.readLast(any(), any()) } returns null
        coEvery { cache.readTransaction(any(), any()) } returns null
        coEvery { cache.readPage(any(), any(), any()) } returns null
        qualified()
        repository = TransactionHistoryRepositoryImpl(client, extrinsics, rpc, cache, database, details, coordinator)
    }
    @After fun cleanup() { unmockkObject(FirebaseWrapper) }
    private fun qualified(items: List<IndexerHistoryElement> = listOf(item), cached: Boolean = false, end: Boolean = true) {
        coEvery { client.getLastTransactionsQualified(address, 10) } returns PiQualifiedRead(items, health, cached)
        coEvery { client.getTransactionQualified(item.id) } returns PiQualifiedRead(items, health, cached)
        coEvery { client.getTransactionHistoryQualified(address, any(), 100) } returns
            PiQualifiedRead(IndexerHistoryPage(items, end, if (end) items.size else 101), health, cached)
    }
    private fun saved(items: List<IndexerHistoryElement> = listOf(item), end: Boolean = true) {
        coEvery { cache.readLast(address, 10) } returns PiValidatedHistoryRecord(items, health)
        coEvery { cache.readTransaction(address, item.id) } returns PiValidatedHistoryRecord(items, health)
        coEvery { cache.readPage(address, any(), 100) } returns PiValidatedHistoryRecord(items, health, end, if (end) items.size else 101)
    }
    private fun failure(error: Exception) {
        coEvery { client.getLastTransactionsQualified(address, 10) } throws error
        coEvery { client.getTransactionQualified(item.id) } throws error
        coEvery { client.getTransactionHistoryQualified(address, any(), 100) } throws error
    }
    private suspend fun last(filter: String? = null) = repository.getLastTransactions(account, tokens, 10, filter)
    private suspend fun page(number: Long = 1, filter: String? = null) = repository.getTransactionHistory(number, tokens, account, filter)
    private suspend fun detail() = repository.getTransaction(item.id, tokens, account)
    private fun assertIndexed(actual: Transaction?) {
        assertNotNull(actual); assertEquals(item.id, actual!!.base.txHash)
        assertEquals(TransactionStatus.COMMITTED, actual.base.status)
        assertEquals(item.blockHash, actual.base.blockHash)
    }
    private suspend fun cancelled(block: suspend () -> Unit) {
        try { block(); fail("Cancellation must propagate") } catch (expected: CancellationException) { assertEquals("fixture cancelled", expected.message) }
    }

    @Test fun liveDetailValidatesCanonicalBlockBeforeSaving() = runTest {
        assertIndexed(detail()); coVerifyOrder { rpc.getBlockHash(100); cache.storeTransaction(address, item.id, any()) }
    }
    @Test fun livePagePreservesPaginationAndSavesValidatedReceipt() = runTest {
        qualified(end = false); val result = page(); assertIndexed(result.transactions.single())
        assertFalse(result.endReached); assertNull(result.errorMessage)
        coVerifyOrder { rpc.getBlockHash(100); cache.storePage(address, 1, 100, any(), any()) }
    }
    @Test fun rawCachedDetailUsesSeparateValidatedRecord() = runTest {
        qualified(emptyList(), cached = true); saved(); assertIndexed(detail())
        coVerify(exactly = 0) { rpc.getBlockHash(any()) }
    }
    @Test fun rawCachedPagePreservesValidatedPagination() = runTest {
        qualified(emptyList(), cached = true); saved(end = false); val result = page()
        assertIndexed(result.transactions.single()); assertFalse(result.endReached)
        coVerify(exactly = 0) { rpc.getBlockHash(any()) }
    }
    @Test fun rawCachedDetailWithoutValidatedCopyMustUseRpc() = runTest {
        qualified(cached = true); assertIndexed(detail()); coVerify(exactly = 1) { rpc.getBlockHash(100) }
    }
    @Test fun rawCachedPageWithoutValidatedCopyMustUseRpc() = runTest {
        qualified(cached = true); assertIndexed(page().transactions.single()); coVerify(exactly = 1) { rpc.getBlockHash(100) }
    }
    @Test fun offlineDetailUsesValidatedCopyWithoutRpc() = runTest {
        failure(UnknownHostException("offline")); saved(); assertIndexed(detail()); coVerify(exactly = 0) { rpc.getBlockHash(any()) }
    }
    @Test fun offlinePageUsesValidatedCopyAndRetainsEndMarker() = runTest {
        failure(UnknownHostException("offline")); saved(end = false); val result = page()
        assertIndexed(result.transactions.single()); assertFalse(result.endReached); assertNull(result.errorMessage)
    }
    @Test fun malformedDetailNeverFallsBackToCachedHistory() = runTest {
        failure(IllegalStateException("invalid checkpoint")); saved(); assertNull(detail())
        coVerify(exactly = 0) { cache.readTransaction(any(), any()) }
    }
    @Test fun malformedPageNeverFallsBackToCachedHistory() = runTest {
        failure(IllegalStateException("invalid checkpoint")); saved(); val result = page()
        assertTrue(result.transactions.isEmpty()); assertEquals("invalid checkpoint", result.errorMessage)
        coVerify(exactly = 0) { cache.readPage(any(), any(), any()) }
    }
    @Test fun wrongForkDetailDoesNotPublishOrPersist() = runTest {
        coEvery { rpc.getBlockHash(100) } returns "0x" + "ab".repeat(32); assertNull(detail())
        coVerify(exactly = 0) { cache.storeTransaction(any(), any(), any()) }
    }
    @Test fun wrongForkPageReportsFailureWithoutCaching() = runTest {
        coEvery { rpc.getBlockHash(100) } returns "0x" + "ab".repeat(32)
        assertTrue(page().transactions.isEmpty()); coVerify(exactly = 0) { cache.storePage(any(), any(), any(), any(), any()) }
    }
    @Test fun optionalDetailPersistenceFailureKeepsLiveResult() = runTest {
        coEvery { cache.storeTransaction(any(), any(), any()) } throws IllegalStateException("disk full"); assertIndexed(detail())
    }
    @Test fun optionalPagePersistenceFailureKeepsLiveResult() = runTest {
        coEvery { cache.storePage(any(), any(), any(), any(), any()) } throws IllegalStateException("disk full"); assertIndexed(page().transactions.single())
    }
    @Test fun optionalRecentPersistenceFailureKeepsLiveResult() = runTest {
        coEvery { cache.storeLast(any(), any(), any()) } throws IllegalStateException("disk full"); assertIndexed(last().single())
    }
    @Test fun detailCancellationNeverUsesCache() = runTest {
        failure(CancellationException("fixture cancelled")); saved(); cancelled { detail() }; coVerify(exactly = 0) { cache.readTransaction(any(), any()) }
    }
    @Test fun cacheWriteCancellationPropagatesFromPage() = runTest {
        coEvery { cache.storePage(any(), any(), any(), any(), any()) } throws CancellationException("fixture cancelled"); cancelled { page() }
    }
    @Test fun cacheWriteCancellationPropagatesFromDetail() = runTest {
        coEvery { cache.storeTransaction(any(), any(), any()) } throws CancellationException("fixture cancelled"); cancelled { detail() }
    }
    @Test fun cacheWriteCancellationPropagatesFromRecentHistory() = runTest {
        coEvery { cache.storeLast(any(), any(), any()) } throws CancellationException("fixture cancelled"); cancelled { last() }
    }
    @Test fun secondPageNeverRepeatsFirstPagePendingOverlay() = runTest {
        rows = listOf(row()); assertIndexed(page(2).transactions.single())
    }
    @Test fun offlineSecondPageNeverRepeatsFirstPagePendingOverlay() = runTest {
        rows = listOf(row()); failure(UnknownHostException("offline")); val result = page(2)
        assertTrue(result.transactions.isEmpty()); assertNotNull(result.errorMessage)
    }
    @Test fun firstPageMergesWaitingActivityWithLiveHistory() = runTest {
        rows = listOf(row()); val result = page(); assertEquals(setOf(localHash, item.id), result.transactions.map { it.base.txHash }.toSet())
    }
    @Test fun pageTokenFilterKeepsMatchingLiquidityOnly() = runTest {
        assertIndexed(page(filter = TestTokens.valToken.id).transactions.single())
        assertTrue(page(filter = TestTokens.pswapToken.id).transactions.isEmpty())
    }
    @Test fun malformedDetailHashNeverReadsNetworkOrCache() = runTest {
        assertNull(repository.getTransaction("not a hash", tokens, account))
        coVerify(exactly = 0) { client.getTransactionQualified(any()); cache.readTransaction(any(), any()) }
    }
    @Test fun accountChangeClearsContactCache() = runTest {
        coEvery { client.getRecentTransactionPeers(address) } returnsMany listOf(listOf("first"), listOf("second"))
        assertEquals(setOf("first"), repository.getContacts(address, "")); repository.onSoraAccountChange()
        assertEquals(setOf("second"), repository.getContacts(address, ""))
    }
    @Test fun contactCancellationPropagatesAndDoesNotPoisonRetry() = runTest {
        coEvery { client.getRecentTransactionPeers(address) } throws CancellationException("fixture cancelled")
        cancelled { repository.getContacts(address, "") }
        coEvery { client.getRecentTransactionPeers(address) } returns listOf("recovered")
        assertEquals(setOf("recovered"), repository.getContacts(address, ""))
    }
    @Test fun statusRefreshCancellationPropagates() = runTest {
        coEvery { coordinator.recoverStatusOnlyFor(address, localHash) } throws CancellationException("fixture cancelled")
        cancelled { repository.checkWaitingTransaction(address, localHash) }
    }

    private suspend fun restored(activity: Sora2SubmissionActivity): Transaction {
        failure(UnknownHostException("offline")); rows = listOf(row())
        descriptions = listOf(Sora2SubmissionDetails(address, localHash, 1, activity))
        return last().single().also {
            assertEquals(localHash, it.base.txHash); assertEquals(TransactionStatus.PENDING, it.base.status)
        }
    }
    private fun activity(kind: Sora2SubmissionActivity.Kind) = Sora2SubmissionActivity(kind,
        tokens.map { it.id }, listOf("1.25", "2.5"), "0.003", "public recipient", "SMART")
    @Test fun restoredTransferKeepsRecipientAmountAndToken() = runTest {
        val result = restored(activity(Sora2SubmissionActivity.Kind.TRANSFER)) as Transaction.Transfer
        assertEquals("public recipient", result.peer); assertEquals(BigDecimal("1.25"), result.amount)
        assertEquals(TestTokens.xorToken, result.token); assertEquals(TransactionTransferType.OUTGOING, result.transferType)
    }
    @Test fun restoredLiquidityAddKeepsExactPairAndAmounts() = runTest {
        val result = restored(activity(Sora2SubmissionActivity.Kind.LIQUIDITY_ADD)) as Transaction.Liquidity
        assertEquals(TransactionLiquidityType.ADD, result.type); assertEquals(TestTokens.xorToken, result.token1)
        assertEquals(TestTokens.valToken, result.token2); assertEquals(BigDecimal("2.5"), result.amount2)
    }
    @Test fun restoredLiquidityRemovalKeepsWithdrawalKind() = runTest {
        val result = restored(activity(Sora2SubmissionActivity.Kind.LIQUIDITY_REMOVE)) as Transaction.Liquidity
        assertEquals(TransactionLiquidityType.WITHDRAW, result.type); assertEquals(BigDecimal("1.25"), result.amount1)
    }
    @Test fun restoredReferralBondKeepsAmount() = runTest {
        val result = restored(activity(Sora2SubmissionActivity.Kind.REFERRAL_BOND)) as Transaction.ReferralBond
        assertEquals(BigDecimal("1.25"), result.amount); assertEquals(TestTokens.xorToken, result.token)
    }
    @Test fun restoredReferralUnbondKeepsAmount() = runTest {
        val result = restored(activity(Sora2SubmissionActivity.Kind.REFERRAL_UNBOND)) as Transaction.ReferralUnbond
        assertEquals(BigDecimal("1.25"), result.amount); assertEquals(TestTokens.xorToken, result.token)
    }
    @Test fun restoredReferrerKeepsExactCounterparty() = runTest {
        val result = restored(activity(Sora2SubmissionActivity.Kind.REFERRAL_SET_REFERRER)) as Transaction.ReferralSetReferrer
        assertEquals("public recipient", result.who); assertTrue(result.myReferrer)
    }
    @Test fun restoredFarmStakeKeepsBaseTargetAndReward() = runTest {
        val result = restored(activity(Sora2SubmissionActivity.Kind.DEMETER_STAKE)) as Transaction.DemeterFarming
        assertEquals(DemeterType.STAKE, result.type); assertEquals(TestTokens.xorToken, result.baseToken)
        assertEquals(TestTokens.valToken, result.targetToken); assertEquals(TestTokens.pswapToken, result.rewardToken)
    }
    @Test fun restoredFarmUnstakeKeepsUnstakeKind() = runTest {
        val result = restored(activity(Sora2SubmissionActivity.Kind.DEMETER_UNSTAKE)) as Transaction.DemeterFarming
        assertEquals(DemeterType.UNSTAKE, result.type); assertEquals(BigDecimal("1.25"), result.amount)
    }
    @Test fun restoredFarmRewardKeepsRewardKind() = runTest {
        val result = restored(activity(Sora2SubmissionActivity.Kind.DEMETER_CLAIM)) as Transaction.DemeterFarming
        assertEquals(DemeterType.REWARD, result.type); assertEquals(BigDecimal("0.003"), result.base.fee)
    }
    @Test fun legacyClaimRetainsGenericWaitingEntry() = runTest {
        assertTrue(restored(Sora2SubmissionActivity.legacyClaim()) is Transaction.Sora2Submission)
    }
    @Test fun missingTokenDoesNotInventRichActivity() = runTest {
        assertTrue(restored(activity(Sora2SubmissionActivity.Kind.SWAP).copy(tokenIds = listOf("missing"))) is Transaction.Sora2Submission)
    }
    @Test fun malformedAmountDoesNotInventRichActivity() = runTest {
        assertTrue(restored(activity(Sora2SubmissionActivity.Kind.TRANSFER).copy(amounts = listOf("invalid"))) is Transaction.Sora2Submission)
    }
    @Test fun missingRecipientDoesNotInventTransfer() = runTest {
        assertTrue(restored(activity(Sora2SubmissionActivity.Kind.TRANSFER).copy(peer = null)) is Transaction.Sora2Submission)
    }
    @Test fun unknownSwapMarketUsesSmartRoutingLabel() = runTest {
        val result = restored(activity(Sora2SubmissionActivity.Kind.SWAP).copy(market = "future-market")) as Transaction.Swap
        assertEquals(Market.SMART, result.market)
    }
    @Test fun rewardTokenHistoryIncludesPendingFarmClaim() = runTest {
        restored(activity(Sora2SubmissionActivity.Kind.DEMETER_CLAIM))
        assertEquals(localHash, last(TestTokens.pswapToken.id).single().base.txHash)
        assertTrue(last("unrelated").isEmpty())
    }
    @Test fun orphanDescriptionsArePrunedWithGracePeriod() = runTest {
        descriptions = listOf(Sora2SubmissionDetails(address, localHash, 1)); val before = System.currentTimeMillis()
        last(); val after = System.currentTimeMillis()
        coVerify(exactly = 1) { details.retainOnly(address, emptySet(), match { it in (before - 3_600_000)..(after - 3_600_000) }) }
    }
    private fun row(hash: String = localHash) = Sora2PendingSubmissionLocal(
        localId = "sora2:${hash.removePrefix("0x")}",
        recoverySchemaVersion = Sora2PendingSubmissionLocal.RECOVERY_SCHEMA_VERSION,
        walletId = TestAccounts.soraAccount.substrateAddress,
        networkId = Sora2PendingSubmissionLocal.NETWORK_SORA2,
        transactionHash = hash,
        accountId = "11".repeat(32),
        publicKey = "11".repeat(32),
        genesisHash = "0x" + "22".repeat(32),
        specVersion = 130,
        transactionVersion = 130,
        metadataSha256 = "33".repeat(32),
        typesSha256 = "44".repeat(32),
        eraBirthBlock = 64,
        eraDeathBlockExclusive = 128,
        eraPeriod = 64,
        eraPhase = 0,
        eraBirthBlockHash = "0x" + "55".repeat(32),
        operationKind = Sora2PendingSubmissionLocal.OPERATION_GENERIC,
        state = Sora2PendingSubmissionLocal.STATE_SUBMISSION_UNKNOWN,
        submissionIsAmbiguous = true,
        terminalBlockNumber = null,
        terminalBlockHash = null,
        terminalFinalizedHeight = null,
        createdAt = 1,
        updatedAt = 2,
    )

}
