package jp.co.soramitsu.feature_polkaswap_impl.data.repository

import io.mockk.*
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.data.network.dto.*
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.feature_account_api.domain.interfaces.*
import jp.co.soramitsu.feature_blockexplorer_api.data.*
import jp.co.soramitsu.sora.substrate.runtime.Sora2RuntimeContract
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigInteger

/** Indexer metadata is retained, but account-bound financial values must come from one finalized block. */
class PolkamarktAuthoritativeReadsTest {
    private val calls = mockk<SubstrateCalls>()
    private val user = mockk<UserRepository>()
    private val runtime = mockk<Sora2RuntimeContract>(relaxed = true)
    private val flags = mockk<ProductionFeatureManager>()
    private val credentials = mockk<CredentialsRepository>()
    private val extrinsics = mockk<ExtrinsicManager>()
    private val repo = PolkamarktTraderRepository(mockk(), calls, extrinsics, runtime, credentials, user, flags, mockk<AppDatabase>(), mockk())
    private val account = SoraAccount("owner", "Wallet")
    private val block = "0x" + "ab".repeat(32)
    private fun n(value: Long) = BigInteger.valueOf(value)
    private fun authoritative() = PolkamarktAuthoritativeMarketDto(42, "Resolved", n(500), n(600))
    private fun runtimeState() = PolkamarktMarketStateDto(42, "DynamicParimutuel", n(123), n(6), n(4), n(10), 5500, 4500, 6000, 4000)
    private fun claim(id: Long = 42) = PolkamarktClaimableDto(id, "owner", "Resolved", "Yes", n(6), n(4), n(10), n(12), n(12), n(2), true)
    private fun market() = PolkamarktMarket("42", 42, "Catalog title", "science", "space", "Catalog description", null, "Public source", 100,
        "Open", "CachedMechanism", "owner", "KUSD", "0", "100", "500", 0.1, 0.1, 0.9, "0", "0", "0", "0", 1000, 9000, 1000, 9000,
        "0", "0", "0", null, null, null, 90, 1_800_000_000_000)
    @Before fun setup() {
        coEvery { user.getCurSoraAccount() } returns account
        coEvery { flags.getState() } returns ProductionFeatureState(false, false, true, false, false, false, false)
        coEvery { calls.getFinalizedHead() } returns block
        coEvery { calls.polkamarktAuthoritativeMarket(42, block) } returns FinalizedRuntimeValue(block, authoritative())
        coEvery { calls.polkamarktMarketState(42, block) } returns FinalizedRuntimeValue(block, runtimeState())
        coEvery { calls.polkamarktClaimable("owner", 42, block) } returns FinalizedRuntimeValue(block, claim())
    }
    private suspend fun fails(code: String, call: suspend () -> Any?) {
        try { call(); fail("Expected $code") } catch (error: IllegalStateException) { assertEquals(code, error.message) }
    }
    @Test fun `authoritative detail replaces stale catalog prices status and share amounts`() = runTest {
        val result = repo.getAuthoritativeMarketDetail("owner", market())
        assertEquals("Catalog title", result.market.title)
        assertEquals("Catalog description", result.market.description)
        assertEquals("Resolved", result.market.status)
        assertEquals(500L, result.market.closeBlock)
        assertEquals("DynamicParimutuel", result.market.mechanism)
        assertEquals("123", result.market.virtualDepth)
        assertEquals("6", result.market.realYesShares)
        assertEquals("4", result.market.realNoShares)
        assertEquals("10", result.market.dpmCollateral)
        assertEquals(5500, result.market.marginalYesPriceBps)
        assertEquals(4500, result.market.marginalNoPriceBps)
        assertEquals(6000, result.market.displayYesProbabilityBps)
        assertEquals(4000, result.market.displayNoProbabilityBps)
        assertEquals(claim(), result.claimable)
        assertEquals(block, result.finalizedBlockHash)
        coVerify(exactly = 1) { calls.getFinalizedHead() }
        coVerify(exactly = 0) { credentials.retrieveKeyPair(any()) }
    }
    @Test fun `empty account claim remains absent instead of inheriting catalog payouts`() = runTest {
        coEvery { calls.polkamarktClaimable("owner", 42, block) } returns FinalizedRuntimeValue(block, null)
        assertNull(repo.getAuthoritativeMarketDetail("owner", market()).claimable)
    }
    @Test fun `detail rejects an account mismatch before any runtime request`() = runTest {
        fails("POLKAMARKT_ACCOUNT_CHANGED") { repo.getAuthoritativeMarketDetail("different", market()) }
        coVerify(exactly = 0) { calls.getFinalizedHead() }
    }
    @Test fun `detail rejects missing or contradictory catalog market identity`() = runTest {
        fails("POLKAMARKT_MARKET_ID_MISSING") { repo.getAuthoritativeMarketDetail("owner", market().copy(marketId = null)) }
        fails("POLKAMARKT_MARKET_ID_MISMATCH") { repo.getAuthoritativeMarketDetail("owner", market().copy(id = "43")) }
        coVerify(exactly = 0) { calls.getFinalizedHead() }
    }
    @Test fun `disabled market surface cannot request runtime state`() = runTest {
        coEvery { flags.getState() } returns ProductionFeatureState(false, false, false, false, false, false, false)
        fails("POLKAMARKT_DISABLED") { repo.getAuthoritativeMarketDetail("owner", market()) }
        coVerify(exactly = 0) { calls.getFinalizedHead() }
    }
    @Test fun `all detail responses must belong to the same finalized block`() = runTest {
        coEvery { calls.polkamarktMarketState(42, block) } returns FinalizedRuntimeValue("0x" + "cd".repeat(32), runtimeState())
        fails("POLKAMARKT_MARKET_BLOCK_MISMATCH") { repo.getAuthoritativeMarketDetail("owner", market()) }
    }
    @Test fun `missing authoritative state cannot be replaced by indexer metadata`() = runTest {
        coEvery { calls.polkamarktAuthoritativeMarket(42, block) } returns FinalizedRuntimeValue(block, null)
        fails("POLKAMARKT_MARKET_STATE_MISSING") { repo.getAuthoritativeMarketDetail("owner", market()) }
    }
    @Test fun `invalid runtime quantities prices and identities are rejected`() = runTest {
        for (state in listOf(runtimeState().copy(marketId = 43), runtimeState().copy(virtualDepth = n(-1)),
            runtimeState().copy(realYesShares = n(-1)), runtimeState().copy(realNoShares = n(-1)), runtimeState().copy(dpmCollateral = n(-1)),
            runtimeState().copy(marginalYesPriceBps = 10001), runtimeState().copy(impliedNoProbabilityBps = -1), runtimeState().copy(mechanism = "Active\n"))) {
            coEvery { calls.polkamarktMarketState(42, block) } returns FinalizedRuntimeValue(block, state)
            fails("POLKAMARKT_RUNTIME_DETAIL_INVALID") { repo.getAuthoritativeMarketDetail("owner", market()) }
        }
    }
    @Test fun `invalid authoritative block and state values are rejected`() = runTest {
        for (state in listOf(authoritative().copy(marketId = 43), authoritative().copy(closeBlock = n(-1)),
            authoritative().copy(observedBlockNumber = n(-1)), authoritative().copy(status = " Open"))) {
            coEvery { calls.polkamarktAuthoritativeMarket(42, block) } returns FinalizedRuntimeValue(block, state)
            fails("POLKAMARKT_RUNTIME_DETAIL_INVALID") { repo.getAuthoritativeMarketDetail("owner", market()) }
        }
    }
    @Test fun `negative claim amounts and malformed labels cannot reach display`() = runTest {
        for (value in listOf(claim().copy(yesShares = n(-1)), claim().copy(noShares = n(-1)), claim().copy(netCollateralPaid = n(-1)),
            claim().copy(traderPayout = n(-1)), claim().copy(claimablePayout = n(-1)), claim().copy(creatorFees = n(-1)),
            claim().copy(status = " Resolved"), claim().copy(resolutionOutcome = "Yes\n"))) {
            coEvery { calls.polkamarktClaimable("owner", 42, block) } returns FinalizedRuntimeValue(block, value)
            fails("POLKAMARKT_CLAIMABLE_INVALID") { repo.getAuthoritativeMarketDetail("owner", market()) }
        }
    }
    @Test fun `detail detects a wallet switch while the account specific request is in flight`() = runTest {
        coEvery { user.getCurSoraAccount() } returnsMany listOf(account, SoraAccount("different", "Other"))
        fails("POLKAMARKT_ACCOUNT_CHANGED") { repo.getAuthoritativeMarketDetail("owner", market()) }
    }
    @Test fun `claim review binds every amount to the requested wallet market and checkpoint`() = runTest {
        coEvery { calls.polkamarktClaimable("owner", 43, block) } returns FinalizedRuntimeValue(block, claim(43))
        val result = repo.getAuthoritativeClaimables("owner", listOf(42, 43))
        assertEquals(setOf(42L, 43L), result.requestedMarketIds)
        assertEquals(mapOf(42L to claim(), 43L to claim(43)), result.claims)
        assertEquals(block, result.finalizedBlockHash)
        coVerify(exactly = 1) { calls.getFinalizedHead() }
        coVerify(exactly = 0) { credentials.retrieveKeyPair(any()) }
    }
    @Test fun `missing claim is explicitly absent in a completed review`() = runTest {
        coEvery { calls.polkamarktClaimable("owner", 42, block) } returns FinalizedRuntimeValue(block, null)
        val result = repo.getAuthoritativeClaimables("owner", listOf(42))
        assertEquals(setOf(42L), result.requestedMarketIds)
        assertTrue(result.claims.isEmpty())
    }
    @Test fun `claim review rejects checkpoint drift`() = runTest {
        coEvery { calls.polkamarktClaimable("owner", 42, block) } returns FinalizedRuntimeValue("0x" + "cd".repeat(32), claim())
        fails("POLKAMARKT_CLAIM_BLOCK_MISMATCH") { repo.getAuthoritativeClaimables("owner", listOf(42)) }
    }
    @Test fun `claim review detects wallet changes before returning`() = runTest {
        coEvery { user.getCurSoraAccount() } returnsMany listOf(account, SoraAccount("different", "Other"))
        fails("POLKAMARKT_ACCOUNT_CHANGED") { repo.getAuthoritativeClaimables("owner", listOf(42)) }
    }
    @Test fun `claim review rejects malformed payout instead of presenting it`() = runTest {
        coEvery { calls.polkamarktClaimable("owner", 42, block) } returns FinalizedRuntimeValue(block, claim().copy(claimablePayout = n(-1)))
        fails("POLKAMARKT_CLAIMABLE_INVALID") { repo.getAuthoritativeClaimables("owner", listOf(42)) }
    }
}
