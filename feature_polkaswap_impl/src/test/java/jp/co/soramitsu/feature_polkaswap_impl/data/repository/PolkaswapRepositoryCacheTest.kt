package jp.co.soramitsu.feature_polkaswap_impl.data.repository

import io.mockk.*
import java.math.BigDecimal
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common_wallet.data.AssetLocalToAssetMapper
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.*
import jp.co.soramitsu.core_db.model.*
import jp.co.soramitsu.feature_blockexplorer_api.data.*
import jp.co.soramitsu.feature_blockexplorer_api.data.models.SoraCurrency
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PolkaswapRepositoryCacheTest {
    private val datasource = mockk<WalletDatasource>(relaxed = true)
    private val db = mockk<AppDatabase>()
    private val pools = mockk<PoolDao>(relaxed = true)
    private val assets = mockk<AssetDao>()
    private val mapper = mockk<AssetLocalToAssetMapper>()
    private val explorer = mockk<BlockExplorerManager>()
    private val config = mockk<SoraConfigManager>()
    private val subject = PolkaswapRepositoryImpl(datasource, db, mapper, explorer, config, mockk<SubstrateCalls>())
    private val currency = SoraCurrency("USD", "United States Dollar", "$")
    private val wallet = SoraAccount("owner", "Main")
    private val base = Token("xor", "XOR", "SORA", 18, true, null, 2.0, 0.0, "$")
    private val target = base.copy(id = "usd", symbol = "USD", precision = 6)
    private val basic = BasicPoolLocal("xor", "usd", BigDecimal("100"), BigDecimal("200"), BigDecimal("1000"), "reserve")
    private val user = UserPoolLocal("xor", "usd", "owner", BigDecimal("25"), true, 7)
    private val baseLocal = TokenLocal("xor", "SORA", "XOR", 18, true, "SORA", false)
    private val targetLocal = baseLocal.copy(id = "usd", symbol = "USD", precision = 6)

    @Before fun setup() {
        every { db.poolDao() } returns pools
        every { db.assetDao() } returns assets
        every { explorer.getTempApy("reserve") } returns 0.12
        coEvery { config.getSelectedCurrency() } returns currency
        coEvery { assets.getToken("xor", any()) } returns TokenWithFiatLocal(baseLocal, null)
        coEvery { assets.getToken("usd", any()) } returns TokenWithFiatLocal(targetLocal, null)
        coEvery { mapper.map(any<TokenWithFiatLocal>()) } answers { if (firstArg<TokenWithFiatLocal>().token.id == "xor") base else target }
        coEvery { mapper.map(any<TokenFiatLocal>()) } answers { if (firstArg<TokenFiatLocal>().token.id == "xor") base else target }
    }

    @Test fun `basic pool lookup preserves absent pools and maps reserves with selected currency`() = runTest {
        coEvery { pools.getBasicPool("xor", "usd") } returnsMany listOf(null, basic)
        assertNull(subject.getBasicPool("xor", "usd"))
        val result = subject.getBasicPool("xor", "usd")!!
        assertSame(base, result.baseToken); assertSame(target, result.targetToken)
        assertEquals(BigDecimal("100"), result.baseReserves)
        assertEquals(BigDecimal("200"), result.targetReserves)
        assertEquals(0.12, result.sbapy)
        coVerify { assets.getToken("xor", currency.code); assets.getToken("usd", currency.code) }
    }

    @Test fun `all basic pools and base dex metadata retain source order`() = runTest {
        coEvery { pools.getBasicPools() } returns listOf(basic)
        coEvery { pools.getPoolBaseTokens() } returns listOf(BasePoolWithTokenLocal(PoolBaseTokenLocal("xor", 7), baseLocal))
        assertEquals("reserve", subject.getBasicPools().single().reserveAccount)
        val dex = subject.getPoolBaseTokens().single()
        assertEquals(7, dex.dexId); assertEquals("xor", dex.tokenId); assertEquals("XOR", dex.tokenSymbol)
    }

    @Test fun `favorites use explicit account identity and disclaimer follows datasource`() = runTest {
        every { datasource.getDisclaimerVisibility() } returns flowOf(true, false)
        subject.poolFavoriteOn("xor" to "usd", wallet)
        subject.poolFavoriteOff("xor" to "usd", wallet.copy(substrateAddress = "other"))
        coVerify { pools.poolFavoriteOn("xor", "usd", "owner") }
        coVerify { pools.poolFavoriteOff("xor", "usd", "other") }
        assertEquals(listOf(true, false), subject.getPolkaswapDisclaimerVisibility().toList())
        subject.setPolkaswapDisclaimerVisibility(false)
        coVerify { datasource.saveDisclaimerVisibility(false) }
    }

    @Test fun `pool subscription never borrows another wallets position`() = runTest {
        every { pools.subscribePool("usd", "xor") } returns flowOf(
            emptyList(),
            listOf(UserPoolJoinedLocalNullable(user.copy(accountAddress = "other"), basic)),
            listOf(UserPoolJoinedLocalNullable(user.copy(accountAddress = "other"), basic), UserPoolJoinedLocalNullable(user, basic)),
        )
        val states = subject.subscribePoolOfAccount("owner", "xor", "usd").toList()
        assertNull(states[0])
        assertNull(states[1]!!.user)
        assertEquals(BigDecimal("25"), states[2]!!.user!!.poolProvidersBalance)
        assertEquals(2.5, states[2]!!.user!!.poolShare, 0.00001)
    }

    @Test fun `local reserves are absent without a position and use the matching wallet share`() = runTest {
        every { pools.subscribePool("usd", "xor") } returns flowOf(emptyList(), listOf(UserPoolJoinedLocalNullable(user, basic)))
        val states = subject.subscribeLocalPoolReserves("owner", "xor", "usd").toList()
        assertNull(states[0])
        val position = states[1]!!
        assertEquals(0, BigDecimal("2.5").compareTo(position.firstPooled))
        assertEquals(0, BigDecimal("5").compareTo(position.secondPooled))
        assertEquals(BigDecimal("100"), position.firstReserves)
        assertEquals(BigDecimal("200"), position.secondReserves)
        assertEquals(0.12, position.sbApy)
    }

    @Test fun `cache lookup and debounced changes retain account position metadata`() = runTest {
        val records = listOf(UserPoolJoinedLocal(user, basic))
        coEvery { pools.getPoolsList("owner") } returns records
        val cached = subject.getPoolsCacheOfAccount("owner").single()
        assertTrue(cached.user.favorite); assertEquals(7, cached.user.sort)
        val updates = MutableSharedFlow<List<UserPoolJoinedLocal>>()
        every { pools.subscribePoolsList("owner") } returns updates
        val seen = mutableListOf<List<jp.co.soramitsu.common_wallet.domain.model.CommonUserPoolData>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { subject.subscribePools("owner").toList(seen) }
        runCurrent(); updates.emit(emptyList()); advanceTimeBy(200); updates.emit(records)
        advanceTimeBy(499); runCurrent(); assertTrue(seen.isEmpty())
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(listOf(cached)), seen)
    }

    @Test fun `basic pool subscriptions suppress unchanged pool presentations`() = runTest {
        val record = BasicPoolWithTokenFiatLocal(basic, TokenFiatLocal(baseLocal, null), TokenFiatLocal(targetLocal, null))
        every { pools.subscribeBasicPoolsWithToken() } returns flowOf(listOf(record), listOf(record), emptyList())
        val states = subject.subscribeBasicPools().toList()
        assertEquals(2, states.size)
        assertEquals("reserve", states[0].single().reserveAccount)
        assertTrue(states[1].isEmpty())
    }
}
