package jp.co.soramitsu.feature_blockexplorer_api.data

import io.mockk.*
import jp.co.soramitsu.common.domain.AppStateProvider
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.model.*
import jp.co.soramitsu.feature_blockexplorer_api.data.models.SoraCurrency
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*

/** Real valuation and fiat-refresh decisions with mocked indexer and database boundaries. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BlockExplorerPresentationDataTest {
    private val client = mockk<PolkaswapIndexerClient>()
    private val dao = mockk<AssetDao>()
    private val db = mockk<AppDatabase> { every { assetDao() } returns dao }
    private val app = mockk<AppStateProvider> { every { isForeground } returns true }
    private val config = mockk<SoraConfigManager>()
    private var currency = SoraCurrency("USD", "Dollar", "$")
    private var localPrices = listOf(price("xor", 2.0), price("val", 3.0))
    private var response = listOf(info("xor"), info("val"))
    private val writes = mutableListOf<List<FiatTokenPriceLocal>>()
    private val manager = BlockExplorerManager(client, db, app, config)
    private fun price(id: String, value: Double) = FiatTokenPriceLocal(id, currency.code, value, 100, 1.0, 20, 0.5, 10, 0.1)
    private fun info(id: String, supply: String? = "1000000", change: Double? = 12.5, usd: String? = "4.5") = IndexerAssetInfo(id, usd, supply, change)
    private fun token(id: String) = TokenLocal(id, id, id.uppercase(), 6, true, "", true)
    @Before fun setup() {
        mockkObject(FirebaseWrapper); every { FirebaseWrapper.recordException(any()) } just Runs
        coEvery { config.getSelectedCurrency() } answers { currency }
        coEvery { dao.getFiatTokenPriceLocal(any()) } answers { localPrices }
        coEvery { dao.getPrecisionOfToken(any()) } returns 6
        coEvery { dao.insertFiatPrice(any()) } coAnswers { writes += firstArg<List<FiatTokenPriceLocal>>() }
        coEvery { dao.getTokensWithFiatOfCurrency(any()) } answers { listOf(TokenWithFiatLocal(token("xor"), localPrices[0]), TokenWithFiatLocal(token("val"), null)) }
        coEvery { client.getAssetsInfo(any()) } answers { val ids = firstArg<List<String>>(); response.filter { it.id in ids } }
        coEvery { client.getFiat() } answers { response }
        coEvery { client.getPoolApys() } returns listOf(IndexerPoolApy("xor-val", "0.125"))
    }
    @After fun cleanup() = unmockkObject(FirebaseWrapper)
    @Test fun liquidityUsesTokenPrecisionAndLocalFiatPrice() = runTest {
        assertEquals(listOf("xor" to 2.0), manager.getTokensLiquidity(listOf("xor")))
        assertEquals(0.125, writes.single().single().fiatChange!!, 0.0)
        assertEquals(2.0, writes.single().single().fiatPrice, 0.0)
    }
    @Test fun aSecondTokenRequestDoesNotReuseFirstTokensResult() = runTest {
        manager.getTokensLiquidity(listOf("xor")); assertEquals(listOf("val" to 3.0), manager.getTokensLiquidity(listOf("val")))
    }
    @Test fun currencyChangeUsesItsOwnLocalValuation() = runTest {
        manager.getTokensLiquidity(listOf("xor")); currency = SoraCurrency("JPY", "Yen", "¥"); localPrices = listOf(price("xor", 300.0))
        assertEquals(listOf("xor" to 300.0), manager.getTokensLiquidity(listOf("xor")))
    }
    @Test fun failedLiquidityReadCanBeRetried() = runTest {
        coEvery { client.getAssetsInfo(any()) } throws IllegalStateException("offline")
        assertTrue(manager.getTokensLiquidity(listOf("xor")).isEmpty())
        coEvery { client.getAssetsInfo(any()) } returns listOf(info("xor"))
        assertEquals(listOf("xor" to 2.0), manager.getTokensLiquidity(listOf("xor")))
    }
    @Test fun refreshedLocalPriceIsUsedForNextLiquidityRead() = runTest {
        manager.getTokensLiquidity(listOf("xor")); localPrices = listOf(price("xor", 7.0))
        assertEquals(listOf("xor" to 7.0), manager.getTokensLiquidity(listOf("xor")))
    }
    @Test fun missingPriceOmitsValuationWithoutInventingZero() = runTest {
        localPrices = emptyList(); assertTrue(manager.getTokensLiquidity(listOf("xor")).isEmpty())
    }
    @Test fun missingPrecisionOmitsValuation() = runTest {
        coEvery { dao.getPrecisionOfToken(any()) } returns null; assertTrue(manager.getTokensLiquidity(listOf("xor")).isEmpty())
    }
    @Test fun malformedSupplyIsNotValued() = runTest {
        response = listOf(info("xor", "1.5")); assertTrue(manager.getTokensLiquidity(listOf("xor")).isEmpty())
    }
    @Test fun absentSupplyIsNotValued() = runTest {
        response = listOf(info("xor", null)); assertTrue(manager.getTokensLiquidity(listOf("xor")).isEmpty())
    }
    @Test fun negativeSupplyCannotProduceNegativeLiquidity() = runTest {
        response = listOf(info("xor", "-1000000")); assertTrue(manager.getTokensLiquidity(listOf("xor")).isEmpty())
    }
    @Test fun nonFinitePriceDoesNotDiscardOtherValidAssets() = runTest {
        localPrices = listOf(price("xor", Double.NaN), price("val", 3.0))
        assertEquals(listOf("val" to 3.0), manager.getTokensLiquidity(listOf("xor", "val")))
    }
    @Test fun unavailableChangeDoesNotBecomeKnownZero() = runTest {
        response = listOf(info("xor", change = null)); manager.getTokensLiquidity(listOf("xor")); assertNull(writes.single().single().fiatChange)
    }
    @Test fun liquidityCancellationIsNeverCachedAsEmpty() = runTest {
        coEvery { client.getAssetsInfo(any()) } throws CancellationException("fixture")
        try { manager.getTokensLiquidity(listOf("xor")); fail("cancellation expected") } catch (_: CancellationException) { }
        assertTrue(writes.isEmpty())
    }
    @Test fun apyConvertsFractionToPercent() = runTest {
        manager.updatePoolsSbApy(); assertEquals(12.5, manager.getTempApy("xor-val")!!, 0.0); assertNull(manager.getTempApy("missing"))
    }
    @Test fun replacementApySnapshotRemovesOldPool() = runTest {
        manager.updatePoolsSbApy(); coEvery { client.getPoolApys() } returns listOf(IndexerPoolApy("xor-pswap", "0.2"))
        manager.updatePoolsSbApy(); assertNull(manager.getTempApy("xor-val")); assertEquals(20.0, manager.getTempApy("xor-pswap")!!, 0.0)
    }
    @Test fun apyReadFailurePreservesLastSnapshot() = runTest {
        manager.updatePoolsSbApy(); coEvery { client.getPoolApys() } throws IllegalStateException("offline")
        manager.updatePoolsSbApy(); assertEquals(12.5, manager.getTempApy("xor-val")!!, 0.0)
    }
    @Test fun unavailableApyIsNotANumber() = runTest {
        coEvery { client.getPoolApys() } returns listOf(IndexerPoolApy("xor-val", "NaN")); manager.updatePoolsSbApy(); assertNull(manager.getTempApy("xor-val"))
    }
    @Test fun infiniteApyIsNotDisplayed() = runTest {
        coEvery { client.getPoolApys() } returns listOf(IndexerPoolApy("xor-val", "Infinity")); manager.updatePoolsSbApy(); assertNull(manager.getTempApy("xor-val"))
    }
    @Test fun finiteApyThatOverflowsWhenConvertedToPercentIsUnavailable() = runTest {
        coEvery { client.getPoolApys() } returns listOf(IndexerPoolApy("xor-val", "1" + "0".repeat(308)))
        manager.updatePoolsSbApy()
        assertNull(manager.getTempApy("xor-val"))
    }
    @Test fun largeFiniteApyStillReturnsItsExactFinitePercent() = runTest {
        coEvery { client.getPoolApys() } returns listOf(IndexerPoolApy("xor-val", "1" + "0".repeat(306)))
        manager.updatePoolsSbApy()
        assertEquals(1e308, manager.getTempApy("xor-val")!!, 0.0)
    }
    @Test fun backgroundAppDoesNotRefreshPrices() = runTest {
        every { app.isForeground } returns false; manager.updateFiat(); coVerify(exactly = 0) { client.getFiat() }; assertTrue(writes.isEmpty())
    }
    @Test fun priceRefreshPreservesHistoryAndInitializesMissingRows() = runTest {
        manager.updateFiat(); val rows = writes.single(); assertEquals(2, rows.size)
        assertEquals(1.0, rows[0].fiatPricePrevH, 0.0); assertEquals(0.5, rows[0].fiatPricePrevD, 0.0)
        assertEquals(4.5, rows[0].fiatPrice, 0.0); assertEquals(0.0, rows[1].fiatPricePrevH, 0.0)
        assertEquals("USD", rows[1].currencyId); assertTrue(rows[0].fiatPriceTime > 100)
    }
    @Test fun malformedOrUnknownFiatRowsDoNotOverwriteValidPrices() = runTest {
        response = listOf(info("xor", usd = "not a number"), info("missing")); manager.updateFiat(); assertTrue(writes.flatten().isEmpty())
    }
    @Test fun infiniteFiatPriceIsRejected() = runTest {
        response = listOf(info("xor", usd = "Infinity")); manager.updateFiat(); assertTrue(writes.flatten().isEmpty())
    }
    @Test fun negativeFiatPriceIsRejected() = runTest {
        response = listOf(info("xor", usd = "-4.5")); manager.updateFiat(); assertTrue(writes.flatten().isEmpty())
    }
    @Test fun usdPricesAreNeverStoredAsSelectedForeignCurrency() = runTest {
        currency = SoraCurrency("JPY", "Yen", "¥"); manager.updateFiat()
        assertTrue(writes.flatten().all { it.currencyId == "USD" })
        coVerify(exactly = 0) { dao.getTokensWithFiatOfCurrency("JPY") }
    }
    @Test fun fiatCancellationPropagatesWithoutWrites() = runTest {
        coEvery { client.getFiat() } throws CancellationException("fixture")
        try { manager.updateFiat(); fail("cancellation expected") } catch (_: CancellationException) { }
        assertTrue(writes.isEmpty())
    }
    @Test fun fiatTransportFailureDoesNotClearStoredRows() = runTest {
        coEvery { client.getFiat() } throws IllegalStateException("offline"); manager.updateFiat(); assertTrue(writes.isEmpty())
    }
}
