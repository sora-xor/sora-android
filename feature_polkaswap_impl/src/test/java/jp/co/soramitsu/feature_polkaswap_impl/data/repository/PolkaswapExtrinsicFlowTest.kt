package jp.co.soramitsu.feature_polkaswap_impl.data.repository

import io.mockk.*
import jp.co.soramitsu.common.domain.*
import jp.co.soramitsu.common_wallet.domain.model.WithDesired
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.PoolDao
import jp.co.soramitsu.core_db.model.PoolBaseTokenLocal
import jp.co.soramitsu.feature_blockexplorer_api.data.BlockExplorerManager
import jp.co.soramitsu.feature_polkaswap_impl.data.mappers.SwapMarketMapper
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.substrate.*
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.xsubstrate.runtime.extrinsic.ExtrinsicBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal
import java.math.BigInteger

/** Real repository and runtime argument builders; signer and transport never execute. */
class PolkaswapExtrinsicFlowTest {
    private val manager = mockk<ExtrinsicManager>()
    private val builder = mockk<ExtrinsicBuilder>()
    private val pools = mockk<PoolDao>()
    private val db = mockk<AppDatabase> { every { poolDao() } returns pools }
    private val repo = PolkaswapExtrinsicRepositoryImpl(manager, SwapMarketMapper(), db, mockk<BlockExplorerManager>())
    private val a = AssetHolder.emptyToken.copy(id = "0x" + "02" + "00".repeat(31), precision = 6, symbol = "A")
    private val b = AssetHolder.emptyToken.copy(id = "0x" + "04" + "00".repeat(31), precision = 12, symbol = "B")
    private val keypair = mockk<Sr25519Keypair>()
    private val activity = Sora2SubmissionActivity(Sora2SubmissionActivity.Kind.SWAP, listOf(a.id, b.id), listOf("1.234567", "2.123456789012"))
    private val status = ExtrinsicSubmitStatus(true, "public-transaction", "public-block")
    private var result = status
    private var fee: BigInteger? = BigInteger("25000000000000000")
    private val calls = mutableListOf<Call>()
    private var feeBatch: Boolean? = null
    private var submittedBatch: Boolean? = null
    private data class Call(val pallet: String, val method: String, val arguments: Map<String, Any?>)
    @Before fun setup() {
        every { builder.call(any<String>(), any<String>(), any()) } answers {
            calls += Call(firstArg(), secondArg(), thirdArg()); builder
        }
        coEvery { pools.getPoolBaseToken(any()) } returns PoolBaseTokenLocal(a.id, 3)
        coEvery { manager.calcFee(any(), any(), any()) } coAnswers {
            feeBatch = secondArg(); thirdArg<suspend ExtrinsicBuilder.() -> Unit>().invoke(builder); fee
        }
        coEvery { manager.submitAndWatchExtrinsic(any(), any(), any(), any(), any()) } coAnswers {
            submittedBatch = thirdArg(); arg<suspend ExtrinsicBuilder.() -> Unit>(4).invoke(builder); result
        }
    }
    private fun decimal(value: String) = BigDecimal(value)
    private fun raw(value: String) = BigInteger(value)
    private fun args() = calls.last().arguments
    private fun payload() = (args()["swap_amount"] as DictEnum.Entry<*>).value as Struct.Instance
    private fun equalDecimal(expected: String, actual: BigDecimal?) { assertNotNull(actual); assertEquals(0, decimal(expected).compareTo(actual)) }
    private suspend fun swap(desired: WithDesired = WithDesired.INPUT, markets: List<Market> = listOf(Market.XYK)) =
        repo.observeSwap(a, b, keypair, "owner", markets, desired, decimal("1.234567"), decimal("2.123456789012"), 3, activity)
    private suspend fun add(presented: Boolean = false, enabled: Boolean = false) =
        repo.observeAddLiquidity("owner", keypair, a, b, decimal("1.25"), decimal("2.5"), enabled, presented, 0.5, activity)
    private suspend fun addFee(presented: Boolean = false, enabled: Boolean = false) =
        repo.calcAddLiquidityNetworkFee("owner", a.id to a.precision, b.id to b.precision, decimal("1.25"), decimal("2.5"), enabled, presented, 0.5)
    private suspend fun remove() = repo.observeRemoveLiquidity("owner", keypair, a, b, decimal("0.25"), decimal("1.234567"), decimal("2.123456789012"), activity)
    private suspend fun removeFee() = repo.calcRemoveLiquidityNetworkFee(a.id to a.precision, b.id to b.precision, "owner")

    @Test fun inputSwapUsesInputPrecisionAndOutputFloor() = runTest {
        swap(); assertEquals(raw("1234567"), payload().get<BigInteger>("desired_amount_in"))
        assertEquals(raw("2123456789012"), payload().get<BigInteger>("min_amount_out")); assertEquals(raw("3"), args()["dex_id"])
    }
    @Test fun outputSwapUsesOutputPrecisionAndInputCeiling() = runTest {
        repo.observeSwap(a, b, keypair, "owner", emptyList(), WithDesired.OUTPUT, decimal("2.123456789012"), decimal("1.234567"), 3, activity)
        assertEquals(raw("2123456789012"), payload().get<BigInteger>("desired_amount_out")); assertEquals(raw("1234567"), payload().get<BigInteger>("max_amount_in"))
    }
    @Test fun explicitMarketRestrictionReachesRuntime() = runTest {
        swap(markets = listOf(Market.XYK, Market.TBC))
        assertEquals("AllowSelected", (args()["filter_mode"] as DictEnum.Entry<*>).name)
        assertEquals(listOf("XYKPool", "MulticollateralBondingCurvePool"), (args()["selected_source_types"] as List<*>).map { (it as DictEnum.Entry<*>).name })
    }
    @Test fun smartRoutingDoesNotEncodeSyntheticMarket() = runTest {
        swap(markets = listOf(Market.SMART)); assertEquals("Disabled", (args()["filter_mode"] as DictEnum.Entry<*>).name)
        assertEquals(emptyList<Any>(), args()["selected_source_types"])
    }
    @Test fun swapForwardsExactOwnerKeyAndRecoveryActivity() = runTest {
        assertSame(status, swap()); coVerify(exactly = 1) { manager.submitAndWatchExtrinsic("owner", keypair, false, activity, any()) }
    }
    @Test fun failedOnChainSwapRemainsFailedWithNetworkDetail() = runTest {
        result = ExtrinsicSubmitStatus(false, "tx", "block", "LiquidityProxy.SlippageNotTolerated")
        assertSame(result, swap()); assertFalse(result.success)
    }
    @Test fun swapTransportCancellationPropagatesWithoutRetry() = runTest {
        val cancelled = CancellationException("cancelled")
        coEvery { manager.submitAndWatchExtrinsic(any(), any(), any(), any(), any()) } throws cancelled
        try { swap(); fail("Expected cancellation") } catch (actual: CancellationException) { assertSame(cancelled, actual) }
        coVerify(exactly = 1) { manager.submitAndWatchExtrinsic(any(), any(), any(), any(), any()) }
    }
    @Test fun swapFeeIsXorRegardlessOfTradedAssetPrecision() = runTest { equalDecimal("0.025", repo.calcSwapNetworkFee(a, "owner")) }
    @Test fun missingSwapFeeRemainsUnknown() = runTest { fee = null; assertNull(repo.calcSwapNetworkFee(a, "owner")) }
    @Test fun swapQuoteNeverInvokesSubmission() = runTest {
        repo.calcSwapNetworkFee(a, "owner"); coVerify(exactly = 0) { manager.submitAndWatchExtrinsic(any(), any(), any(), any(), any()) }
    }
    @Test fun newPairRegistersInitializesThenDepositsAtomically() = runTest {
        add(); assertEquals(listOf("register", "initialize_pool", "deposit_liquidity"), calls.map { it.method }); assertEquals(true, submittedBatch)
        assertTrue(calls.all { it.arguments["dex_id"] == raw("3") })
    }
    @Test fun enabledPairSkipsRegistrationButStillInitializesAtomically() = runTest {
        add(enabled = true); assertEquals(listOf("initialize_pool", "deposit_liquidity"), calls.map { it.method }); assertEquals(true, submittedBatch)
    }
    @Test fun existingPoolDepositsWithoutReinitializing() = runTest {
        add(presented = true); assertEquals(listOf("deposit_liquidity"), calls.map { it.method }); assertEquals(false, submittedBatch)
    }
    @Test fun supplyScalesEachAssetAndItsSlippageMinimumIndependently() = runTest {
        add(); assertEquals(raw("1250000"), args()["input_a_desired"]); assertEquals(raw("2500000000000"), args()["input_b_desired"])
        assertEquals(raw("1243750"), args()["input_a_min"]); assertEquals(raw("2487500000000"), args()["input_b_min"])
    }
    @Test fun liquiditySubmissionRetainsFailureIdentity() = runTest {
        result = ExtrinsicSubmitStatus(false, "failed-hash", "final-block", "PoolXYK.InsufficientBalance")
        assertSame(result, add()); coVerify { manager.submitAndWatchExtrinsic("owner", keypair, true, activity, any()) }
    }
    @Test fun newPoolFeeUsesSameAtomicBatchModeAsSubmission() = runTest { addFee(); assertEquals(true, feeBatch) }
    @Test fun existingPoolFeeUsesSingleDepositMode() = runTest { addFee(presented = true); assertEquals(false, feeBatch); assertEquals(listOf("deposit_liquidity"), calls.map { it.method }) }
    @Test fun enabledPairFeeDoesNotIncludeRegistration() = runTest { addFee(enabled = true); assertEquals(listOf("initialize_pool", "deposit_liquidity"), calls.map { it.method }) }
    @Test fun addFeeUsesXorUnitsRatherThanPoolBaseUnits() = runTest { equalDecimal("0.025", addFee()) }
    @Test fun unknownAddFeeRemainsUnknown() = runTest { fee = null; assertNull(addFee()) }
    @Test fun unavailableDexMappingUsesDefaultDex() = runTest {
        coEvery { pools.getPoolBaseToken(a.id) } returns null
        add(); assertTrue(calls.all { it.arguments["dex_id"] == BigInteger.ZERO })
    }
    @Test fun removalKeepsEachOutputMinimumInItsOwnPrecision() = runTest {
        assertSame(status, remove()); assertEquals(raw("1234567"), args()["output_a_min"]); assertEquals(raw("2123456789012"), args()["output_b_min"])
        coVerify { manager.submitAndWatchExtrinsic("owner", keypair, false, activity, any()) }
    }
    @Test fun removeFeeBuildsSampleOutputUsingSecondAssetPrecision() = runTest { removeFee(); assertEquals(raw("1000000000000"), args()["output_b_min"]) }
    @Test fun removeFeeUsesXorUnitsRatherThanPoolBaseUnits() = runTest { equalDecimal("0.025", removeFee()) }
    @Test fun unknownRemoveFeeRemainsUnknown() = runTest { fee = null; assertNull(removeFee()) }
    @Test fun poolLookupFailureNeverStartsSubmission() = runTest {
        val error = IllegalStateException("local database unavailable"); coEvery { pools.getPoolBaseToken(any()) } throws error
        try { add(); fail("Expected lookup failure") } catch (actual: IllegalStateException) { assertSame(error, actual) }
        coVerify(exactly = 0) { manager.submitAndWatchExtrinsic(any(), any(), any(), any(), any()) }
    }
    @Test fun quoteFailureIsNotMisreportedAsZeroFee() = runTest {
        val error = IllegalStateException("runtime unavailable"); coEvery { manager.calcFee(any(), any(), any()) } throws error
        try { removeFee(); fail("Expected fee failure") } catch (actual: IllegalStateException) { assertSame(error, actual) }
    }
}
