package jp.co.soramitsu.demeter.domain

import io.mockk.*
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.demeter.data.DemeterFarmingRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.*
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.Sora2SubmissionActivity
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicSubmissionUnknown
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class DemeterFarmingInteractorTest {
    private val repo = mockk<DemeterFarmingRepository>()
    private val assets = mockk<AssetsRepository>()
    private val users = mockk<UserRepository>()
    private val history = mockk<TransactionHistoryRepository>(relaxed = true)
    private val builder = mockk<TransactionBuilder>()
    private val credentials = mockk<CredentialsRepository>()
    private val keys = mockk<Sr25519Keypair>()
    private val transaction = mockk<Transaction.DemeterFarming>()
    private val wallet = SoraAccount("wallet-address", "My wallet")
    private val ids = Triple("xor", "val", "pswap")
    private val amount = BigDecimal("1.234567890123456789")
    private val fee = BigDecimal("0.0002")
    private fun token(id: String) = Token(id, id.uppercase(), id.uppercase(), 18, true, null, 1.0, 0.0, "$")
    private val subject = DemeterFarmingInteractorImpl(repo, assets, users, history, builder, credentials)

    @Before fun setup() {
        coEvery { users.getCurSoraAccount() } returns wallet
        coEvery { credentials.retrieveKeyPair(wallet) } returns keys
        coEvery { assets.getToken(any()) } answers { token(firstArg()) }
        every { builder.buildDemeterStaking(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns transaction
        every { builder.buildDemeterRewards(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns transaction
    }
    private fun basic(target: String = "val", reward: String = "pswap", tvl: String = "100") =
        DemeterFarmingBasicPool(token("xor"), token(target), token(reward), 12.0, BigDecimal(tvl), 0.1)
    private fun farm(target: String = "val", reward: String = "pswap") =
        DemeterFarmingPool(token("xor"), token(target), token(reward), 12.0, amount, BigDecimal("2"))

    @Test fun `basic pools are ordered by exact TVL without changing the repository list`() = runTest {
        val low = basic(tvl = "100.000000000000000001")
        val high = basic(target = "eth", tvl = "100.000000000000000002")
        val source = listOf(low, high)
        coEvery { repo.getFarmedBasicPools() } returns source
        assertEquals(listOf(high, low), subject.getFarmedBasicPools())
        assertEquals(listOf(low, high), source)
    }
    @Test fun `pool lookup matches all three token identifiers`() = runTest {
        val expected = farm()
        coEvery { repo.getFarmedPools(wallet.substrateAddress) } returns listOf(farm(reward = "deo"), farm(target = "eth"), expected)
        assertSame(expected, subject.getFarmedPool(ids))
        assertNull(subject.getFarmedPool(Triple("other", "val", "pswap")))
    }
    @Test fun `basic pool lookup does not confuse farms sharing a base token`() = runTest {
        val expected = basic()
        coEvery { repo.getFarmedBasicPools() } returns listOf(basic(reward = "deo"), basic(target = "eth"), expected)
        assertSame(expected, subject.getFarmedBasicPool(ids))
        assertNull(subject.getFarmedBasicPool(Triple("xor", "missing", "pswap")))
    }
    @Test fun `missing farm data remains unavailable instead of inventing an empty position`() = runTest {
        coEvery { repo.getFarmedPools(wallet.substrateAddress) } returns null
        assertNull(subject.getFarmedPools())
        assertNull(subject.getFarmedPool(ids))
    }
    @Test fun `staked balance is requested for the currently selected wallet`() = runTest {
        coEvery { repo.getStakedFarmedAmountOfAsset(wallet.substrateAddress, "val") } returns amount
        assertEquals(amount, subject.getStakedFarmedBalanceOfAsset("val"))
        coVerify(exactly = 1) { repo.getStakedFarmedAmountOfAsset(wallet.substrateAddress, "val") }
    }
    @Test fun `subscription preserves emitted wallet updates`() = runTest {
        every { repo.subscribeFarms(wallet.substrateAddress) } returns flowOf("first", "second")
        assertEquals(listOf("first", "second"), subject.subscribeFarms(wallet.substrateAddress).toList())
    }
    @Test fun `deposit binds wallet exact amount fee tokens and duplicate consent`() = runTest {
        val activity = slot<Sora2SubmissionActivity>()
        coEvery { repo.depositDemeterFarm(wallet.substrateAddress, keys, "xor", "val", "pswap", true, amount, capture(activity)) } returns ExtrinsicSubmitStatus(true, "tx-hash", "block-hash")
        assertEquals(TransactionOutcome.Confirmed("tx-hash", "block-hash"), subject.depositDemeterFarming(ids, amount, fee, true))
        assertEquals(Sora2SubmissionActivity.Kind.DEMETER_STAKE, activity.captured.kind)
        assertEquals(listOf("xor", "val", "pswap"), activity.captured.tokenIds)
        assertEquals(listOf(amount.toPlainString()), activity.captured.amounts)
        assertEquals(fee.toPlainString(), activity.captured.fee)
        assertTrue(activity.captured.duplicateConfirmed)
        verify { builder.buildDemeterStaking("tx-hash", "block-hash", fee, TransactionStatus.PENDING, any(), amount, DemeterType.STAKE, token("xor"), token("val"), token("pswap")) }
        coVerify(exactly = 1) { history.saveTransaction(wallet.substrateAddress, transaction) }
    }
    @Test fun `withdrawal does not imply duplicate consent and records unstaking`() = runTest {
        val activity = slot<Sora2SubmissionActivity>()
        coEvery { repo.withdrawDemeterFarm(wallet.substrateAddress, keys, "xor", "val", "pswap", true, amount, capture(activity)) } returns ExtrinsicSubmitStatus(true, "withdraw-hash", "block-hash")
        val outcome = subject.withdrawDemeterFarming(ids, amount, fee)
        assertEquals(TransactionOutcome.Confirmed("withdraw-hash", "block-hash"), outcome)
        assertEquals(Sora2SubmissionActivity.Kind.DEMETER_UNSTAKE, activity.captured.kind)
        assertFalse(activity.captured.duplicateConfirmed)
        verify { builder.buildDemeterStaking("withdraw-hash", "block-hash", fee, TransactionStatus.PENDING, any(), amount, DemeterType.UNSTAKE, token("xor"), token("val"), token("pswap")) }
        coVerify(exactly = 1) { history.saveTransaction(wallet.substrateAddress, transaction) }
    }
    @Test fun `claim preserves the reviewed reward amount and transaction identity`() = runTest {
        val activity = slot<Sora2SubmissionActivity>()
        coEvery { repo.claimDemeterRewards(wallet.substrateAddress, keys, "xor", "val", "pswap", true, capture(activity)) } returns ExtrinsicSubmitStatus(true, "claim-hash", "block-hash")
        assertEquals(TransactionOutcome.Confirmed("claim-hash", "block-hash"), subject.claimDemeterRewards(ids, amount, fee, true))
        assertEquals(Sora2SubmissionActivity.Kind.DEMETER_CLAIM, activity.captured.kind)
        assertEquals(listOf(amount.toPlainString()), activity.captured.amounts)
        assertTrue(activity.captured.duplicateConfirmed)
        verify { builder.buildDemeterRewards("claim-hash", "block-hash", fee, TransactionStatus.PENDING, any(), amount, DemeterType.REWARD, token("xor"), token("val"), token("pswap")) }
    }
    @Test fun `activity persistence failure cannot rewrite a confirmed transaction outcome`() = runTest {
        coEvery { repo.depositDemeterFarm(any(), any(), any(), any(), any(), any(), any(), any()) } returns ExtrinsicSubmitStatus(true, "tx-hash", "block-hash")
        coEvery { history.saveTransaction(any(), any()) } throws IllegalStateException("database unavailable")
        assertEquals(TransactionOutcome.Confirmed("tx-hash", "block-hash"), subject.depositDemeterFarming(ids, amount, fee))
    }
    @Test fun `failure before submission cannot create a pending activity row`() = runTest {
        coEvery { credentials.retrieveKeyPair(wallet) } throws IllegalStateException("wallet locked")
        try { subject.depositDemeterFarming(ids, amount, fee); fail("Locked wallet must not submit") } catch (_: IllegalStateException) { }
        coVerify(exactly = 0) { repo.depositDemeterFarm(any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { history.saveTransaction(any(), any()) }
    }
    @Test fun `cancellation while recording activity is propagated`() = runTest {
        coEvery { repo.claimDemeterRewards(any(), any(), any(), any(), any(), any(), any()) } returns ExtrinsicSubmitStatus(true, "tx-hash", "block-hash")
        coEvery { history.saveTransaction(any(), any()) } throws CancellationException("screen closed")
        try { subject.claimDemeterRewards(ids, amount, fee); fail("Cancellation was swallowed") } catch (_: CancellationException) { }
    }
    @Test fun `fee estimations use the selected account and fee asset precision`() = runTest {
        coEvery { assets.getToken(SubstrateOptionsProvider.feeAssetId) } returns token("fee").copy(precision = 6)
        coEvery { repo.calcDepositFarmFee(wallet.substrateAddress, "xor", "val", "pswap", true, 6) } returns BigDecimal("0.11")
        coEvery { repo.calcWithdrawFarmFee(wallet.substrateAddress, "xor", "val", "pswap", true, 6) } returns BigDecimal("0.12")
        coEvery { repo.calcClaimDemeterRewards(wallet.substrateAddress, "xor", "val", "pswap", true, 6) } returns BigDecimal("0.13")
        assertEquals(BigDecimal("0.11"), subject.calcDepositDemeterNetworkFee(ids))
        assertEquals(BigDecimal("0.12"), subject.calcWithdrawDemeterNetworkFee(ids))
        assertEquals(BigDecimal("0.13"), subject.calcClaimDemeterRewards(ids))
    }
    @Test fun `pool search matches token names symbols and identifiers case insensitively`() {
        val pool = basic()
        for (filter in listOf("XOR", "VaL", "pSwAp", "")) assertTrue(pool.isFilterMatch(filter))
        assertFalse(pool.isFilterMatch("ETH"))
        assertEquals(ids, pool.ids())
    }
}
