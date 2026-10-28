package jp.co.soramitsu.feature_assets_impl.data

import io.mockk.*
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.data.network.dto.TokenInfoDto
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.WhitelistTokensManager
import jp.co.soramitsu.common_wallet.data.AssetLocalToAssetMapper
import jp.co.soramitsu.common_wallet.data.XorBalanceDto
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.model.*
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.feature_blockexplorer_api.data.models.SoraCurrency
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Collections

@OptIn(ExperimentalCoroutinesApi::class)
class AssetCatalogAndBalancesContractTest {
    private val db = mockk<AppDatabase>()
    private val dao = mockk<AssetDao>()
    private val calls = mockk<SubstrateCalls>()
    private val extrinsics = mockk<ExtrinsicManager>()
    private val config = mockk<SoraConfigManager>()
    private val whitelist = mockk<WhitelistTokensManager>()
    private val manager = mockk<CoroutineManager>()
    private val mapper = AssetLocalToAssetMapper(whitelist, config)
    private val xor = SubstrateOptionsProvider.feeAssetId
    private val owner = "wallet-one"
    private val inserted = mutableListOf<List<AssetLocal>>()
    private val tokenWrites = mutableListOf<List<TokenLocal>>()
    private val tokenUpdates = mutableListOf<List<TokenLocal>>()
    private var active = emptyList<AssetTokenWithFiatLocal>()
    private var catalog = emptyList<TokenWithFiatLocal>()
    private var currency = SoraCurrency("USD", "US Dollar", "$")
    private fun token(id: String, precision: Int = 6, symbol: String = id.uppercase()) = TokenLocal(id, symbol, symbol, precision, true, "SORA", true)
    private fun asset(id: String, precision: Int = 6, exists: Boolean = true, position: Int = 7, amount: String = "9") = AssetTokenWithFiatLocal(token(id, precision), null,
        if (exists) AssetLocal(id, owner, true, position, BigDecimal(amount), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true) else null)
    private fun atomic(value: String, precision: Int = 18) = BigDecimal(value).movePointRight(precision).toBigIntegerExact()
    private val xorBalance = XorBalanceDto(atomic("10"), atomic("2"), atomic("3"), atomic("4"), atomic("5"), atomic("6"), atomic("7"))
    private fun equalDecimal(expected: String, actual: BigDecimal) = assertEquals(expected, 0, BigDecimal(expected).compareTo(actual))
    @Before fun setup() {
        every { db.assetDao() } returns dao
        coEvery { config.getSelectedCurrency() } answers { currency }
        coEvery { whitelist.updateWhitelistStorage() } just Runs
        every { whitelist.whitelistIds } returns listOf(xor, "alpha")
        every { whitelist.getTokenIconUri(any()) } answers { "icon:${firstArg<String>()}" }
        coEvery { calls.fetchAssetsList() } returns emptyList()
        coEvery { dao.getTokensWithFiatOfCurrency(any()) } answers { catalog }
        coEvery { dao.insertTokenList(any()) } answers { tokenWrites += firstArg<List<TokenLocal>>() }
        coEvery { dao.updateTokenList(any()) } answers { tokenUpdates += firstArg<List<TokenLocal>>() }
        coEvery { dao.getAssetsWhitelist(any(), any()) } answers { active }
        coEvery { dao.getAssetsActive(any(), any()) } answers { active }
        coEvery { dao.insertAssets(any()) } answers { inserted += firstArg<List<AssetLocal>>() }
        coEvery { dao.insertTokenListIgnore(any()) } just Runs
        coEvery { calls.fetchXORBalances(any()) } returns xorBalance
        coEvery { calls.fetchBalances(any(), any()) } answers { secondArg<List<String>>().map { atomic("1.25", 6) } }
    }
    private fun TestScope.subject(): AssetsRepositoryImpl {
        every { manager.applicationScope } returns this
        every { manager.io } returns StandardTestDispatcher(testScheduler)
        return AssetsRepositoryImpl(db, mapper, extrinsics, calls, config, manager, whitelist)
    }
    @Test fun `catalog separates new and existing tokens while refreshing exact metadata`() = runTest {
        catalog = listOf(TokenWithFiatLocal(token("alpha", 18), null))
        coEvery { calls.fetchAssetsList() } returns listOf(TokenInfoDto("alpha", "New alpha", "ALPHA", 6, false), TokenInfoDto("beta", "Beta", "BETA", 12, true))
        subject(); runCurrent()
        val added = tokenWrites.single().single(); val updated = tokenUpdates.single().single()
        assertEquals("beta", added.id); assertEquals(12, added.precision); assertEquals("", added.whitelistName)
        assertEquals("alpha", updated.id); assertEquals("New alpha", updated.name); assertEquals(6, updated.precision); assertFalse(updated.isMintable)
        assertEquals(AssetHolder.DEFAULT_WHITE_LIST_NAME, updated.whitelistName)
    }
    @Test fun `token list reads the selected currency after catalog readiness`() = runTest {
        val repo = subject(); runCurrent(); currency = SoraCurrency("JPY", "Yen", "¥")
        coEvery { dao.getTokensOfWhitelist("JPY") } returns listOf(TokenWithFiatLocal(token("alpha"), null))
        val result = repo.tokensList().single()
        assertEquals("alpha", result.id); assertEquals("¥", result.fiatSymbol); assertEquals("icon:alpha", result.iconFile)
    }
    @Test fun `unknown token is absent rather than substituted with another asset`() = runTest {
        coEvery { dao.getTokensByList(listOf("missing"), "USD") } returns emptyList()
        assertNull(subject().getToken("missing"))
    }
    @Test fun `known token keeps its precision and identity`() = runTest {
        coEvery { dao.getTokensByList(listOf("alpha"), "USD") } returns listOf(TokenWithFiatLocal(token("alpha", 12), null))
        val found = subject().getToken("alpha")!!
        assertEquals("alpha", found.id); assertEquals(12, found.precision)
    }
    @Test fun `whitelist membership rejects missing and empty names`() = runTest {
        coEvery { dao.getWhitelistOfToken("missing") } returns null
        coEvery { dao.getWhitelistOfToken("empty") } returns ""
        coEvery { dao.getWhitelistOfToken("alpha") } returns "SORA"
        val repo = subject(); assertFalse(repo.isWhitelistedToken("missing")); assertFalse(repo.isWhitelistedToken("empty")); assertTrue(repo.isWhitelistedToken("alpha"))
    }
    @Test fun `reading assets skips token rows without an account balance`() = runTest {
        active = listOf(asset("alpha"), asset("beta", exists = false))
        val result = subject().getAssetsActive(owner)
        assertEquals(listOf("alpha"), result.map { it.token.id }); equalDecimal("9", result.single().balance.transferable)
    }
    @Test fun `single asset lookup is bound to its wallet and currency`() = runTest {
        coEvery { dao.getAssetWithToken(owner, "alpha", "USD") } returns asset("alpha")
        val result = subject().getAsset("alpha", owner)!!
        assertEquals("alpha", result.token.id); assertTrue(result.favorite); assertTrue(result.visibility); assertEquals(7, result.position)
    }
    @Test fun `absent account asset remains unavailable`() = runTest {
        coEvery { dao.getAssetWithToken(owner, "alpha", "USD") } returns null
        assertNull(subject().getAsset("alpha", owner))
    }
    @Test fun `visible balance refresh accepts an immutable RPC result`() = runTest {
        active = listOf(asset(xor, 18), asset("alpha"))
        coEvery { calls.fetchBalances(owner, listOf("alpha")) } returns Collections.unmodifiableList(listOf(atomic("1.25", 6)))
        subject().updateBalancesVisibleAssets(owner)
        val rows = inserted.last(); equalDecimal("1.25", rows.single { it.tokenId == "alpha" }.free)
        equalDecimal("10", rows.single { it.tokenId == xor }.free)
    }
    @Test fun `refresh without XOR preserves each returned token balance`() = runTest {
        active = listOf(asset("alpha"), asset("beta"))
        coEvery { calls.fetchBalances(owner, listOf("alpha", "beta")) } returns listOf(atomic("1.25", 6), atomic("2.5", 6))
        subject().updateBalancesVisibleAssets(owner)
        assertEquals(listOf("alpha", "beta"), inserted.last().map { it.tokenId })
        equalDecimal("1.25", inserted.last()[0].free); equalDecimal("2.5", inserted.last()[1].free)
    }
    @Test fun `XOR at the middle cannot shift non XOR balances`() = runTest {
        active = listOf(asset("alpha"), asset(xor, 18), asset("beta"))
        coEvery { calls.fetchBalances(owner, listOf("alpha", "beta")) } returns mutableListOf(atomic("1.25", 6), atomic("2.5", 6))
        subject().updateBalancesVisibleAssets(owner)
        val rows = inserted.last(); equalDecimal("1.25", rows[0].free); equalDecimal("10", rows[1].free); equalDecimal("2.5", rows[2].free)
    }
    @Test fun `XOR refresh persists every lock and staking field without changing preferences`() = runTest {
        active = listOf(asset(xor, 18, position = 42))
        coEvery { calls.fetchBalances(owner, emptyList()) } returns mutableListOf()
        subject().updateBalancesVisibleAssets(owner)
        val row = inserted.last().single()
        equalDecimal("10", row.free); equalDecimal("2", row.reserved); equalDecimal("3", row.miscFrozen); equalDecimal("4", row.feeFrozen)
        equalDecimal("5", row.bonded); equalDecimal("6", row.redeemable); equalDecimal("7", row.unbonding)
        assertTrue(row.displayAsset); assertTrue(row.visibility); assertEquals(42, row.position); assertEquals(owner, row.accountAddress)
    }
    @Test fun `new whitelisted token stores its fetched balance on the first refresh`() = runTest {
        active = listOf(asset(xor, 18), asset("alpha", exists = false))
        coEvery { calls.fetchBalances(owner, any()) } returns mutableListOf(atomic("1.25", 6))
        subject().updateWhitelistBalances(owner)
        val row = inserted.last().single { it.tokenId == "alpha" }
        equalDecimal("1.25", row.free); assertFalse(row.displayAsset); assertFalse(row.visibility); assertEquals(8, row.position)
    }
    @Test fun `new XOR account asset does not crash while adding its first balance`() = runTest {
        active = listOf(asset(xor, 18, exists = false), asset("alpha"))
        coEvery { calls.fetchBalances(owner, any()) } returns mutableListOf(atomic("1.25", 6))
        subject().updateWhitelistBalances(owner)
        val row = inserted.last().single { it.tokenId == xor }; equalDecimal("10", row.free); equalDecimal("4", row.feeFrozen)
    }
    @Test fun `catalog only whitelist read preserves balances and creates hidden missing rows`() = runTest {
        active = listOf(asset("alpha", amount = "6"), asset("beta", exists = false))
        subject().getAssetsWhitelist(owner)
        val rows = inserted.last(); equalDecimal("6", rows[0].free); equalDecimal("0", rows[1].free)
        assertFalse(rows[1].visibility); assertFalse(rows[1].displayAsset)
        coVerify(exactly = 0) { calls.fetchXORBalances(any()); calls.fetchBalances(any(), any()) }
    }
    @Test fun `failed balance fetch cannot replace a previous balance snapshot`() = runTest {
        active = listOf(asset(xor, 18), asset("alpha")); coEvery { calls.fetchBalances(any(), any()) } throws IllegalStateException("offline")
        try { subject().updateBalancesVisibleAssets(owner); fail("Expected failure") } catch (_: IllegalStateException) { }
        assertTrue(inserted.isEmpty())
    }
    @Test fun `cancelled balance fetch releases the refresh lock for retry`() = runTest {
        active = listOf(asset(xor, 18), asset("alpha")); val repo = subject()
        coEvery { calls.fetchBalances(any(), any()) } throws CancellationException("screen closed")
        try { repo.updateBalancesVisibleAssets(owner); fail("Expected cancellation") } catch (_: CancellationException) { }
        coEvery { calls.fetchBalances(any(), any()) } returns mutableListOf(atomic("1.25", 6))
        repo.updateBalancesVisibleAssets(owner); assertEquals(1, inserted.size)
    }
    @Test fun `concurrent visible refreshes do not overwrite each other`() = runTest {
        active = listOf(asset(xor, 18), asset("alpha")); val reply = CompletableDeferred<List<BigInteger>>()
        coEvery { calls.fetchBalances(any(), any()) } coAnswers { reply.await() }
        val repo = subject(); val first = launch { repo.updateBalancesVisibleAssets(owner) }; runCurrent()
        repo.updateBalancesVisibleAssets(owner)
        coVerify(exactly = 1) { calls.fetchBalances(any(), any()) }; assertTrue(inserted.isEmpty())
        reply.complete(mutableListOf(atomic("1.25", 6))); first.join(); assertEquals(1, inserted.size)
    }
    @Test fun `subscribing to a missing asset emits null then the inserted balance`() = runTest {
        every { dao.subscribeAsset(owner, "alpha", "USD") } returns flowOf(null, asset("alpha"), asset("alpha"))
        val result = subject().subscribeAsset(owner, "alpha").toList()
        assertEquals(2, result.size); assertNull(result[0]); assertEquals("alpha", result[1]!!.token.id)
    }
    @Test fun `active asset subscription filters absent wallet rows and retains order`() = runTest {
        every { dao.subscribeAssetsActive(owner, "USD") } returns flowOf(listOf(asset("beta"), asset("alpha", exists = false), asset(xor, 18)))
        val result = subject().subscribeAssetsActive(owner).first()
        assertEquals(listOf("beta", xor), result.map { it.token.id })
    }
    @Test fun `token subscription keeps exact precision and current fiat symbol`() = runTest {
        currency = SoraCurrency("JPY", "Yen", "¥")
        every { dao.subscribeTokens("JPY") } returns flowOf(listOf(TokenWithFiatLocal(token("alpha", 12), null)))
        val result = subject().subscribeTokensList().first().single()
        assertEquals("alpha", result.id); assertEquals(12, result.precision); assertEquals("¥", result.fiatSymbol)
    }
    @Test fun `visibility and favorites stay scoped to the selected account`() = runTest {
        coEvery { dao.hideAssets(any(), any()) } just Runs; coEvery { dao.displayAssets(any(), any()) } just Runs
        coEvery { dao.toggleVisibilityOfToken(any(), any(), any()) } just Runs
        val wallet = SoraAccount(owner, "wallet"); val repo = subject()
        repo.hideAssets(listOf("alpha"), wallet); repo.displayAssets(listOf("beta"), wallet); repo.toggleVisibilityOfToken("alpha", false, wallet)
        coVerify { dao.hideAssets(listOf("alpha"), owner); dao.displayAssets(listOf("beta"), owner); dao.toggleVisibilityOfToken("alpha", false, owner) }
    }
    @Test fun `empty wallet seeds default asset identities before first refresh`() = runTest {
        subject().updateBalancesVisibleAssets(owner)
        val defaults = inserted.first()
        assertEquals(AssetHolder.getIds().toSet(), defaults.map { it.tokenId }.toSet())
        assertTrue(defaults.all { it.accountAddress == owner && it.free == BigDecimal.ZERO })
        coVerify { dao.insertTokenListIgnore(match { it.map { token -> token.id }.toSet() == AssetHolder.getIds().toSet() }) }
    }
    @Test fun `XOR balance result keeps transfer locks distinct from staking totals`() = runTest {
        val result = subject().getXORBalance(owner, 18)
        equalDecimal("7", result.transferable); equalDecimal("17", result.totalBalance)
        equalDecimal("2", result.reserved); equalDecimal("5", result.bonded)
    }
    @Test fun `refresh leaves the RPC owned list unchanged`() = runTest {
        active = listOf(asset(xor, 18), asset("alpha")); val reply = mutableListOf(atomic("1.25", 6))
        coEvery { calls.fetchBalances(owner, listOf("alpha")) } returns reply
        subject().updateBalancesVisibleAssets(owner)
        assertEquals(listOf(atomic("1.25", 6)), reply)
    }
    @Test fun `truncated token balances cannot erase saved balances`() = runTest {
        active = listOf(asset(xor, 18), asset("alpha"), asset("beta"))
        coEvery { calls.fetchBalances(owner, any()) } returns mutableListOf(atomic("1.25", 6))
        val failure = runCatching { subject().updateBalancesVisibleAssets(owner) }.exceptionOrNull()
        assertNotNull(failure); assertTrue(inserted.isEmpty())
    }

    @Test fun `first whitelist read immediately returns newly initialized token rows`() = runTest {
        active = listOf(asset("alpha"), asset("beta", exists = false))
        val result = subject().getAssetsWhitelist(owner)
        assertEquals(listOf("alpha", "beta"), result.map { it.token.id })
        equalDecimal("0", result[1].balance.transferable); assertFalse(result[1].favorite)
    }

}
