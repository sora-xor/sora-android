package jp.co.soramitsu.feature_assets_impl.data

import io.mockk.*
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.data.network.dto.TokenInfoDto
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.WhitelistTokensManager
import jp.co.soramitsu.common.io.FileManager
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
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.RestClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    private fun token(id: String, precision: Int = 6, symbol: String = id.uppercase()) = TokenLocal(id, symbol, symbol, precision, true, AssetHolder.DEFAULT_WHITE_LIST_NAME, true)
    private fun asset(id: String, precision: Int = 6, exists: Boolean = true, position: Int = 7, amount: String = "9", reserved: String = "0") = AssetTokenWithFiatLocal(token(id, precision), null,
        if (exists) AssetLocal(id, owner, true, position, BigDecimal(amount), BigDecimal(reserved), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true) else null)
    private fun atomic(value: String, precision: Int = 18) = BigDecimal(value).movePointRight(precision).toBigIntegerExact()
    private val xorBalance = XorBalanceDto(atomic("10"), atomic("2"), atomic("3"), atomic("4"), atomic("5"), atomic("6"), atomic("7"))
    private fun equalDecimal(expected: String, actual: BigDecimal) = assertEquals(expected, 0, BigDecimal(expected).compareTo(actual))
    @Before fun setup() {
        every { db.assetDao() } returns dao
        coEvery { config.getSelectedCurrency() } answers { currency }
        coEvery { whitelist.updateWhitelistStorage() } just Runs
        every { whitelist.whitelistIds } returns listOf(xor, "alpha")
        every { whitelist.blacklistIds } returns emptySet()
        every { whitelist.getTokenIconUri(any()) } answers { "icon:${firstArg<String>()}" }
        coEvery { calls.fetchAssetsList() } returns emptyList()
        coEvery { dao.getTokensWithFiatOfCurrency(any()) } answers { catalog }
        coEvery { dao.insertTokenList(any()) } answers { tokenWrites += firstArg<List<TokenLocal>>() }
        coEvery { dao.updateTokenList(any()) } answers { tokenUpdates += firstArg<List<TokenLocal>>() }
        coEvery { dao.getAssetsWhitelist(any(), any()) } answers { active }
        coEvery { dao.getAssetsActive(any(), any()) } answers { active }
        coEvery { dao.insertAssets(any()) } answers { inserted += firstArg<List<AssetLocal>>() }
        coEvery { dao.insertTokenListIgnore(any()) } just Runs
        coEvery { calls.fetchXORBalances(any(), any()) } returns xorBalance
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
        coVerify(exactly = 0) { calls.fetchXORBalances(any(), any()); calls.fetchBalances(any(), any()) }
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
    @Test fun `every whitelist row of a wallet is one flow, read without creating any row`() = runTest {
        every { dao.subscribeAssetsOfWhitelist(owner, "USD") } returns flowOf(listOf(asset("beta"), asset("alpha", exists = false), asset(xor, 18)))
        val result = subject().subscribeAssetsOfWhitelist(owner).first()
        assertEquals(listOf("beta", xor), result.map { it.token.id })
        coVerify(exactly = 0) { dao.insertAssets(any()) }
    }
    @Test fun `token subscription keeps exact precision and current fiat symbol`() = runTest {
        currency = SoraCurrency("JPY", "Yen", "¥")
        every { dao.subscribeTokens("JPY") } returns flowOf(listOf(TokenWithFiatLocal(token("alpha", 12), null)))
        val result = subject().subscribeTokensList().first().single()
        assertEquals("alpha", result.id); assertEquals(12, result.precision); assertEquals("¥", result.fiatSymbol)
    }
    @Test fun `the newest price time is in milliseconds, for the selected currency`() = runTest {
        currency = SoraCurrency("JPY", "Yen", "¥")
        every { dao.subscribeNewestFiatPriceTime("JPY") } returns flowOf(1_700_000_000L)
        assertEquals(1_700_000_000_000L, subject().subscribeNewestPriceMillis().first())
    }
    @Test fun `a price with no time, or no price at all, has no price time`() = runTest {
        every { dao.subscribeNewestFiatPriceTime("USD") } returns flowOf(null, 0L)
        val times = mutableListOf<Long?>()
        subject().subscribeNewestPriceMillis().collect { times += it }
        assertEquals(listOf<Long?>(null, null), times)
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
    @Test fun `a wallet with stored rows reads its balances while the token list walk is still running`() = runTest {
        active = listOf(asset(xor, 18), asset("alpha")); val walk = CompletableDeferred<List<TokenInfoDto>>()
        coEvery { calls.fetchAssetsList() } coAnswers { walk.await() }
        val repo = subject(); runCurrent()
        repo.updateWhitelistBalances(owner); repo.updateBalancesVisibleAssets(owner)
        assertEquals(2, inserted.size); equalDecimal("1.25", inserted.last().single { it.tokenId == "alpha" }.free)
        coVerify(exactly = 0) { dao.insertTokenListIgnore(any()) }
        assertTrue(tokenWrites.isEmpty() && tokenUpdates.isEmpty())
        walk.complete(emptyList()); runCurrent()
    }
    @Test fun `a token list walk that fails does not stop a wallet with stored rows from reading its balances`() = runTest {
        active = listOf(asset(xor, 18), asset("alpha"))
        coEvery { calls.fetchAssetsList() } throws IllegalStateException("no node")
        val repo = subject(); runCurrent()
        repo.updateWhitelistBalances(owner)
        equalDecimal("10", inserted.single().single { it.tokenId == xor }.free)
    }
    @Test fun `a wallet without rows waits for the token list walk before its rows are made`() = runTest {
        val walk = CompletableDeferred<List<TokenInfoDto>>()
        coEvery { calls.fetchAssetsList() } coAnswers { walk.await() }
        val repo = subject(); val refresh = launch { repo.updateBalancesVisibleAssets(owner) }; runCurrent()
        assertTrue(inserted.isEmpty()); coVerify(exactly = 0) { dao.insertTokenListIgnore(any()); calls.fetchBalances(any(), any()) }
        walk.complete(emptyList()); refresh.join()
        assertEquals(AssetHolder.getIds().toSet(), inserted.first().map { it.tokenId }.toSet())
    }
    @Test fun `a wallet without rows fails its refresh when the token list walk failed, as before`() = runTest {
        coEvery { calls.fetchAssetsList() } throws IllegalStateException("no node")
        val repo = subject(); runCurrent()
        assertTrue(runCatching { repo.updateBalancesVisibleAssets(owner) }.exceptionOrNull() is IllegalStateException)
        assertTrue(inserted.isEmpty())
    }
    /** The phone lists alpha; the walk finds beta as well, and it is whitelisted. */
    private fun walkAddsBeta(): CompletableDeferred<List<TokenInfoDto>> {
        val walk = CompletableDeferred<List<TokenInfoDto>>()
        coEvery { calls.fetchAssetsList() } coAnswers { walk.await() }
        every { whitelist.whitelistIds } returns listOf(xor, "alpha", "beta")
        coEvery { dao.getTokensWithFiatOfCurrency(any()) } returnsMany listOf(
            listOf(TokenWithFiatLocal(token("alpha"), null)),
            listOf(TokenWithFiatLocal(token("alpha"), null), TokenWithFiatLocal(token("beta"), null)),
        )
        return walk
    }
    private val alphaAndBeta = listOf(TokenInfoDto("alpha", "ALPHA", "ALPHA", 6, true), TokenInfoDto("beta", "BETA", "BETA", 6, true))
    @Test fun `a wallet read during the token list walk is read once more when the walk lists a new token`() = runTest {
        active = listOf(asset(xor, 18), asset("alpha")); val walk = walkAddsBeta()
        val repo = subject(); runCurrent()
        repo.updateWhitelistBalances(owner)
        coVerify(exactly = 1) { calls.fetchBalances(owner, listOf("alpha")) }
        // The caller waits for the walk, which has not finished: nothing is read meanwhile.
        val after = launch { repo.updateWhitelistBalancesAfterTokenListWalk(owner) }; runCurrent()
        assertEquals(1, inserted.size)
        // The walk has written beta: the wallet's list now has a row to make for it.
        active = listOf(asset(xor, 18), asset("alpha"), asset("beta", exists = false))
        coEvery { calls.fetchBalances(owner, listOf("alpha", "beta")) } returns listOf(atomic("1.25", 6), atomic("4", 6))
        walk.complete(alphaAndBeta); after.join()
        coVerify(exactly = 1) { calls.fetchBalances(owner, listOf("alpha", "beta")) }
        equalDecimal("4", inserted.last().single { it.tokenId == "beta" }.free)
        // Once: asking again reads nothing more.
        repo.updateWhitelistBalancesAfterTokenListWalk(owner)
        assertEquals(2, inserted.size)
    }
    @Test fun `a wallet read during the walk is read once more when the walk blacklists a token it holds`() = runTest {
        // "scam" is on the chain, was never whitelisted and stays that way; the walk's blacklist names it.
        active = listOf(asset(xor, 18), asset("alpha")); val walk = CompletableDeferred<List<TokenInfoDto>>()
        coEvery { calls.fetchAssetsList() } coAnswers { walk.await() }
        var notListed = emptySet<String>()
        every { whitelist.blacklistIds } answers { notListed }
        coEvery { whitelist.updateWhitelistStorage() } answers { notListed = setOf("scam") }
        catalog = listOf(TokenWithFiatLocal(token("alpha"), null), TokenWithFiatLocal(token("scam").copy(whitelistName = ""), null))
        coEvery { dao.getAssetWithToken(owner, "scam", "USD") } returns asset("scam", exists = false)
        val repo = subject(); runCurrent()
        repo.updateWhitelistBalances(owner)
        coVerify(exactly = 0) { calls.fetchBalances(owner, listOf("alpha", "scam")) }
        walk.complete(listOf(TokenInfoDto("alpha", "ALPHA", "ALPHA", 6, true), TokenInfoDto("scam", "SCAM", "SCAM", 6, true)))
        repo.updateWhitelistBalancesAfterTokenListWalk(owner)
        coVerify(exactly = 1) { calls.fetchBalances(owner, listOf("alpha", "scam")) }
    }
    @Test fun `a wallet read during the walk is read once more when a token only moved from one list to the other`() = runTest {
        // "moved" was blacklisted and is whitelisted now, with the same precision: the tokens a
        // read asks for are the same before and after the walk, but not in between, after the
        // blacklist dropped it and before Room lists it.
        active = listOf(asset(xor, 18), asset("alpha")); val walk = CompletableDeferred<List<TokenInfoDto>>()
        coEvery { calls.fetchAssetsList() } coAnswers { walk.await() }
        var notListed = setOf("moved")
        var listed = listOf(xor, "alpha")
        every { whitelist.blacklistIds } answers { notListed }
        every { whitelist.whitelistIds } answers { listed }
        coEvery { whitelist.updateWhitelistStorage() } answers { notListed = emptySet(); listed = listOf(xor, "alpha", "moved") }
        catalog = listOf(TokenWithFiatLocal(token("alpha"), null), TokenWithFiatLocal(token("moved").copy(whitelistName = ""), null))
        val room = CompletableDeferred<Unit>()
        coEvery { dao.updateTokenList(any()) } coAnswers {
            room.await()
            catalog = firstArg<List<TokenLocal>>().map { TokenWithFiatLocal(it, null) }
        }
        val repo = subject(); runCurrent()
        walk.complete(listOf(TokenInfoDto("alpha", "ALPHA", "ALPHA", 6, true), TokenInfoDto("moved", "MOVED", "MOVED", 6, true)))
        runCurrent()
        // In between: the blacklist no longer names it and Room does not list it yet.
        repo.updateWhitelistBalances(owner)
        coVerify(exactly = 1) { calls.fetchBalances(owner, listOf("alpha")) }
        assertEquals(1, inserted.size)
        // Room lists it: the wallet's whitelist now has a row to make for it, and the chain holds 7.
        active = listOf(asset(xor, 18), asset("alpha"), asset("moved", exists = false))
        coEvery { calls.fetchBalances(owner, listOf("alpha", "moved")) } returns listOf(atomic("1.25", 6), atomic("7", 6))
        room.complete(Unit)
        repo.updateWhitelistBalancesAfterTokenListWalk(owner)
        assertEquals(AssetHolder.DEFAULT_WHITE_LIST_NAME, catalog.single { it.token.id == "moved" }.token.whitelistName)
        assertEquals(2, inserted.size)
        equalDecimal("7", inserted.last().single { it.tokenId == "moved" }.free)
    }
    @Test fun `a wallet read during the walk is read once more when a token kept under another list's name joins the whitelist`() = runTest {
        // The whitelist is the rows that carry its own name, the test the Room queries make.
        active = listOf(asset(xor, 18), asset("alpha")); val walk = CompletableDeferred<List<TokenInfoDto>>()
        coEvery { calls.fetchAssetsList() } coAnswers { walk.await() }
        coEvery { dao.getTokensWithFiatOfCurrency(any()) } returnsMany listOf(
            listOf(TokenWithFiatLocal(token("alpha"), null), TokenWithFiatLocal(token("old").copy(whitelistName = "another_list"), null)),
            listOf(TokenWithFiatLocal(token("alpha"), null), TokenWithFiatLocal(token("old"), null)),
        )
        val repo = subject(); runCurrent()
        repo.updateWhitelistBalances(owner)
        walk.complete(listOf(TokenInfoDto("alpha", "ALPHA", "ALPHA", 6, true), TokenInfoDto("old", "OLD", "OLD", 6, true)))
        repo.updateWhitelistBalancesAfterTokenListWalk(owner)
        assertEquals(2, inserted.size)
    }
    @Test fun `two callers asking for the read owed after the walk read once`() = runTest {
        active = listOf(asset(xor, 18), asset("alpha")); val walk = walkAddsBeta()
        val repo = subject(); runCurrent()
        repo.updateWhitelistBalances(owner)
        active = listOf(asset(xor, 18), asset("alpha"), asset("beta", exists = false))
        coEvery { calls.fetchBalances(owner, listOf("alpha", "beta")) } returns listOf(atomic("1.25", 6), atomic("4", 6))
        val first = launch { repo.updateWhitelistBalancesAfterTokenListWalk(owner) }
        val second = launch { repo.updateWhitelistBalancesAfterTokenListWalk(owner) }
        runCurrent()
        walk.complete(alphaAndBeta); first.join(); second.join()
        assertEquals(2, inserted.size)
        coVerify(exactly = 1) { calls.fetchBalances(owner, listOf("alpha", "beta")) }
    }
    @Test fun `a read owed after the walk stays owed when it fails, until a full read is written`() = runTest {
        active = listOf(asset(xor, 18), asset("alpha")); val walk = walkAddsBeta()
        val repo = subject(); runCurrent()
        repo.updateWhitelistBalances(owner)
        active = listOf(asset(xor, 18), asset("alpha"), asset("beta", exists = false))
        walk.complete(alphaAndBeta); runCurrent()
        coEvery { calls.fetchBalances(owner, listOf("alpha", "beta")) } throws IllegalStateException("SORA2_STORAGE_ANSWER_INCOMPLETE")
        assertTrue(runCatching { repo.updateWhitelistBalancesAfterTokenListWalk(owner) }.exceptionOrNull() is IllegalStateException)
        assertEquals(1, inserted.size)
        coEvery { calls.fetchBalances(owner, listOf("alpha", "beta")) } returns listOf(atomic("1.25", 6), atomic("4", 6))
        repo.updateWhitelistBalancesAfterTokenListWalk(owner)
        assertEquals(2, inserted.size)
        equalDecimal("4", inserted.last().single { it.tokenId == "beta" }.free)
        repo.updateWhitelistBalancesAfterTokenListWalk(owner)
        assertEquals(2, inserted.size)
    }
    @Test fun `a full read written after the walk settles what a read during the walk owed`() = runTest {
        active = listOf(asset(xor, 18), asset("alpha")); val walk = walkAddsBeta()
        val repo = subject(); runCurrent()
        repo.updateWhitelistBalances(owner)
        active = listOf(asset(xor, 18), asset("alpha"), asset("beta", exists = false))
        walk.complete(alphaAndBeta); runCurrent()
        // A balance event's full read comes first: it reads the list as the walk left it.
        repo.updateWhitelistBalances(owner)
        assertEquals(2, inserted.size)
        repo.updateWhitelistBalancesAfterTokenListWalk(owner)
        assertEquals(2, inserted.size)
    }
    @Test fun `a wallet read during the token list walk is not read again when the walk changes nothing`() = runTest {
        active = listOf(asset(xor, 18), asset("alpha")); val walk = CompletableDeferred<List<TokenInfoDto>>()
        coEvery { calls.fetchAssetsList() } coAnswers { walk.await() }
        catalog = listOf(TokenWithFiatLocal(token("alpha"), null))
        val repo = subject(); runCurrent()
        repo.updateWhitelistBalances(owner)
        walk.complete(listOf(TokenInfoDto("alpha", "ALPHA", "ALPHA", 6, true)))
        repo.updateWhitelistBalancesAfterTokenListWalk(owner)
        assertEquals(1, inserted.size)
    }
    @Test fun `a wallet read after the token list walk is not read again`() = runTest {
        active = listOf(asset(xor, 18), asset("alpha")); val walk = walkAddsBeta()
        val repo = subject(); runCurrent()
        walk.complete(alphaAndBeta); runCurrent()
        repo.updateWhitelistBalances(owner)
        repo.updateWhitelistBalancesAfterTokenListWalk(owner)
        assertEquals(1, inserted.size)
    }
    @Test fun `waiting for a token list walk that failed fails, and reads nothing`() = runTest {
        active = listOf(asset(xor, 18), asset("alpha"))
        coEvery { calls.fetchAssetsList() } throws IllegalStateException("no node")
        val repo = subject(); runCurrent()
        repo.updateWhitelistBalances(owner)
        assertTrue(runCatching { repo.updateWhitelistBalancesAfterTokenListWalk(owner) }.exceptionOrNull() is IllegalStateException)
        assertEquals(1, inserted.size)
    }
    @Test fun `balances that are shown are read with every part required, the transfer check as before`() = runTest {
        active = listOf(asset(xor, 18), asset("alpha")); val repo = subject(); runCurrent()
        repo.updateWhitelistBalances(owner); repo.getXORBalance(owner, 18)
        coVerify(exactly = 2) { calls.fetchXORBalances(owner, true) }
        coVerify(exactly = 0) { calls.fetchXORBalances(owner, false) }
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

    @Test fun `a blacklisted token is stored outside the whitelist and returns when the server clears it`() = runTest {
        val listed = "0x" + "aa".repeat(32); val scam = "0x" + "bb".repeat(32); val other = "0x" + "cc".repeat(32)
        val rest = mockk<RestClient>(); val files = mockk<FileManager>(relaxed = true)
        every { files.readInternalFile(any()) } returns null
        every { files.readInternalCacheFileAsUri(any()) } returns null
        val icon = JsonPrimitive("data:image/svg+xml,%3Csvg%2F%3E")
        coEvery { rest.get<JsonArray>(match { it.url.endsWith("/whitelist.json") }) } returns
            JsonArray(listOf(listed, scam).map { JsonObject(mapOf("address" to JsonPrimitive(it), "icon" to icon)) })
        coEvery { rest.get<JsonArray>(match { it.url.endsWith("/blacklist.json") }) } returns JsonArray(listOf(JsonPrimitive(scam)))
        // The blacklisted token was a listed favourite before; the other one was never listed.
        catalog = listOf(TokenWithFiatLocal(token(scam), null))
        coEvery { calls.fetchAssetsList() } returns listOf(listed, scam, other).map { TokenInfoDto(it, it, "T", 18, false) }
        every { manager.applicationScope } returns this
        val tokens = WhitelistTokensManager(rest, files)
        val repo = AssetsRepositoryImpl(db, AssetLocalToAssetMapper(tokens, config), extrinsics, calls, config, manager, tokens)
        runCurrent()
        val names = (tokenWrites.single() + tokenUpdates.single()).associate { it.id to it.whitelistName }
        assertEquals(mapOf(listed to AssetHolder.DEFAULT_WHITE_LIST_NAME, scam to "", other to ""), names)
        coEvery { dao.getWhitelistOfToken(scam) } returns names[scam]
        assertFalse(repo.isWhitelistedToken(scam))

        // The next start reads a blacklist without it: it is a whitelisted token again.
        coEvery { rest.get<JsonArray>(match { it.url.endsWith("/blacklist.json") }) } returns JsonArray(emptyList())
        tokenUpdates.clear()
        AssetsRepositoryImpl(db, AssetLocalToAssetMapper(tokens, config), extrinsics, calls, config, manager, tokens)
        runCurrent()
        assertEquals(AssetHolder.DEFAULT_WHITE_LIST_NAME, tokenUpdates.single().single().whitelistName)
    }
    @Test fun `a blacklisted token on the chain has its balance read with the listed ones`() = runTest {
        every { whitelist.blacklistIds } returns setOf("scam", "gone")
        coEvery { dao.getAssetWithToken(owner, "scam", "USD") } returns asset("scam", exists = false)
        coEvery { dao.getAssetWithToken(owner, "gone", "USD") } returns null
        active = listOf(asset(xor, 18), asset("alpha"))
        val repo = subject()
        repo.updateBalancesVisibleAssets(owner)
        coVerify { calls.fetchBalances(owner, listOf("alpha", "scam")) }
        val row = inserted.last().single { it.tokenId == "scam" }
        equalDecimal("1.25", row.free); assertFalse(row.displayAsset); assertFalse(row.visibility)
        inserted.clear()
        repo.updateWhitelistBalances(owner)
        equalDecimal("1.25", inserted.last().single { it.tokenId == "scam" }.free)
    }
    @Test fun `only blacklisted tokens the account holds are offered as not listed`() = runTest {
        every { whitelist.blacklistIds } returns setOf("scam", "spent", "ghost")
        every { dao.subscribeAssetsOfWhitelist(owner, "USD", "") } returns flowOf(
            listOf(asset("scam", amount = "5"), asset("spent", amount = "0"), asset("delisted", amount = "3"), asset("ghost", exists = false))
        )
        val result = subject().subscribeAssetsNotListed(owner).first()
        assertEquals(listOf("scam"), result.map { it.token.id }); equalDecimal("5", result.single().balance.transferable)
    }

    @Test fun `a held blacklisted token stays offered when all of it is reserved`() = runTest {
        every { whitelist.blacklistIds } returns setOf("locked")
        every { dao.subscribeAssetsOfWhitelist(owner, "USD", "") } returns flowOf(
            listOf(asset("locked", amount = "0", reserved = "4"))
        )
        val held = subject().subscribeAssetsNotListed(owner).first().single()
        // Ownership counts, not what can be spent now.
        equalDecimal("0", held.balance.transferable); equalDecimal("4", held.balance.total)
    }

    @Test fun `a blacklist that arrives after the list was read applies to the next read of the rows`() = runTest {
        val held = listOf(asset("old", amount = "1"), asset("new", amount = "2"))
        val rows = MutableSharedFlow<List<AssetTokenWithFiatLocal>>(replay = 1)
        rows.tryEmit(held)
        every { dao.subscribeAssetsOfWhitelist(owner, "USD", "") } returns rows
        var blacklist = setOf("old")
        every { whitelist.blacklistIds } answers { blacklist }
        val emitted = mutableListOf<List<String>>()
        val job = launch { subject().subscribeAssetsNotListed(owner).collect { list -> emitted += list.map { it.token.id } } }
        advanceUntilIdle()
        // A new blacklist is saved; storing the token list again makes Room emit the rows again.
        blacklist = setOf("new")
        rows.emit(held)
        advanceUntilIdle()
        job.cancel()
        assertEquals(listOf(listOf("old"), listOf("new")), emitted)
    }
}
