package jp.co.soramitsu.feature_referral_impl.domain

import io.mockk.*
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.*
import jp.co.soramitsu.feature_account_api.domain.interfaces.*
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.*
import jp.co.soramitsu.feature_referral_api.data.ReferralRepository
import jp.co.soramitsu.feature_referral_api.data.ReferralReward
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.runtime.*
import jp.co.soramitsu.sora.substrate.substrate.*
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal

class ReferralFlowContractTest {
    private val assets = mockk<AssetsRepository>()
    private val users = mockk<UserRepository>()
    private val repo = mockk<ReferralRepository>()
    private val credentials = mockk<CredentialsRepository>()
    private val runtime = mockk<RuntimeManager>()
    private val history = mockk<TransactionHistoryRepository>(relaxed = true)
    private val keys = mockk<Sr25519Keypair>()
    private var selected = SoraAccount("owner", "Main")
    private val token = Token(SubstrateOptionsProvider.feeAssetId, "XOR", "SORA", 18, false, null, 1.0, 0.0, "$")
    private val fee = BigDecimal("0.0007")
    private val amount = BigDecimal("2.123456789012345678")
    private val subject = ReferralInteractor(assets, users, repo, mockk<WalletRepository>(), credentials, runtime, history)

    @Before fun setup() {
        coEvery { users.getCurSoraAccount() } answers { selected }
        coEvery { assets.getToken(SubstrateOptionsProvider.feeAssetId) } returns token
        coEvery { credentials.retrieveKeyPair(any()) } returns keys
        coEvery { repo.calcBondFee(any(), token) } returns fee
        coEvery { runtime.isAddressOk(any()) } answers { firstArg<String>() == "other" || firstArg<String>() == "owner" }
    }
    @Test fun `invitation follows the selected wallet`() = runTest {
        assertEquals("polkaswap.io/#/referral/owner", subject.getInvitationLink())
        selected = SoraAccount("other", "Other")
        assertEquals("polkaswap.io/#/referral/other", subject.getInvitationLink())
    }
    @Test fun `referral input accepts a valid other account or its link`() = runTest {
        assertEquals(true to "other", subject.isLinkOrAddressOk("other"))
        assertEquals(true to "other", subject.isLinkOrAddressOk("https://polkaswap.io/#/referral/other"))
    }
    @Test fun `referral input rejects self invalid and empty addresses`() = runTest {
        assertEquals(false to "owner", subject.isLinkOrAddressOk("polkaswap.io/#/referral/owner"))
        assertEquals(false to "invalid", subject.isLinkOrAddressOk("invalid"))
        assertEquals(false to "", subject.isLinkOrAddressOk(""))
    }
    @Test fun `referral reward refresh targets only the selected wallet`() = runTest {
        coEvery { repo.updateReferralRewards(any()) } just Runs
        subject.updateReferrals(); selected = SoraAccount("other", "Other"); subject.updateReferrals()
        coVerifySequence { repo.updateReferralRewards("owner"); repo.updateReferralRewards("other") }
    }
    @Test fun `bond fee refreshes when the selected account changes`() = runTest {
        coEvery { repo.calcBondFee("other", token) } returns BigDecimal("0.002")
        assertEquals(fee, subject.calcBondFee()); selected = SoraAccount("other", "Other")
        assertEquals(BigDecimal("0.002"), subject.calcBondFee())
    }
    @Test fun `missing bond fee is retried instead of cached as zero`() = runTest {
        coEvery { repo.calcBondFee(any(), token) } returnsMany listOf(null, fee)
        assertEquals(BigDecimal.ZERO, subject.calcBondFee()); assertEquals(fee, subject.calcBondFee())
    }
    @Test fun `set referrer fee uses current account and preserves unavailable fallback`() = runTest {
        coEvery { repo.getSetReferrerFee("owner", token) } returns null
        coEvery { repo.getSetReferrerFee("other", token) } returns fee
        assertEquals(BigDecimal.ZERO, subject.getSetReferrerFee())
        selected = SoraAccount("other", "Other"); assertEquals(fee, subject.getSetReferrerFee())
    }
    @Test fun `missing fee token cannot authorize a referral submission`() = runTest {
        coEvery { assets.getToken(any()) } returns null
        try { subject.observeSetReferrer("other"); fail("No token metadata") } catch (_: IllegalArgumentException) { }
        coVerify(exactly = 0) { repo.observeSetReferrer(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { history.saveTransaction(any(), any()) }
    }
    @Test fun `bond preserves exact amount wallet fee and explicit duplicate consent`() = runTest {
        val activity = slot<Sora2SubmissionActivity>(); val saved = slot<Transaction>()
        coEvery { repo.observeBond(keys, "owner", amount, token, fee, capture(activity)) } returns ExtrinsicSubmitStatus(true, "hash", "block")
        every { history.saveTransaction("owner", capture(saved)) } just Runs
        assertEquals(TransactionOutcome.Confirmed("hash", "block"), subject.observeBond(amount, true))
        assertEquals(listOf(amount.toPlainString()), activity.captured.amounts)
        assertEquals(listOf(token.id), activity.captured.tokenIds)
        assertEquals(fee.toPlainString(), activity.captured.fee); assertTrue(activity.captured.duplicateConfirmed)
        val tx = saved.captured as Transaction.ReferralBond
        assertEquals(amount, tx.amount); assertSame(token, tx.token); assertEquals(fee, tx.base.fee)
        assertEquals("hash", tx.base.txHash); assertEquals("block", tx.base.blockHash); assertEquals(TransactionStatus.PENDING, tx.base.status)
    }
    @Test fun `unbond uses distinct activity and leaves duplicate consent unset`() = runTest {
        val activity = slot<Sora2SubmissionActivity>(); val saved = slot<Transaction>()
        coEvery { repo.observeUnbond(keys, "owner", amount, token, fee, capture(activity)) } returns ExtrinsicSubmitStatus(true, "unbond", "block")
        every { history.saveTransaction("owner", capture(saved)) } just Runs
        assertEquals(TransactionOutcome.Confirmed("unbond", "block"), subject.observeUnbond(amount))
        assertFalse(activity.captured.duplicateConfirmed); assertEquals(listOf(amount.toPlainString()), activity.captured.amounts)
        val tx = saved.captured as Transaction.ReferralUnbond
        assertEquals(amount, tx.amount); assertEquals("unbond", tx.base.txHash); assertEquals(TransactionStatus.PENDING, tx.base.status)
    }
    @Test fun `set referrer binds the recipient and creates a non amount activity`() = runTest {
        val activity = slot<Sora2SubmissionActivity>(); val saved = slot<Transaction>()
        coEvery { repo.observeSetReferrer(keys, "owner", "other", token, capture(activity)) } returns ExtrinsicSubmitStatus(true, "set", "block")
        every { history.saveTransaction("owner", capture(saved)) } just Runs
        assertEquals(TransactionOutcome.Confirmed("set", "block"), subject.observeSetReferrer("other", true))
        assertTrue(activity.captured.duplicateConfirmed)
        val tx = saved.captured as Transaction.ReferralSetReferrer
        assertEquals("other", tx.who); assertTrue(tx.myReferrer); assertEquals(BigDecimal.ZERO, tx.base.fee)
    }
    @Test fun `lost bond response remains waiting with a recoverable activity`() = runTest {
        coEvery { repo.observeBond(any(), any(), any(), any(), any(), any()) } throws ExtrinsicSubmissionUnknownException("waiting", IllegalStateException("disconnected"), true)
        val saved = slot<Transaction>(); every { history.saveTransaction("owner", capture(saved)) } just Runs
        assertEquals(TransactionOutcome.Waiting("waiting", true), subject.observeBond(amount))
        assertEquals("waiting", saved.captured.base.txHash); assertNull(saved.captured.base.blockHash)
        assertEquals(TransactionStatus.PENDING, saved.captured.base.status)
    }
    @Test fun `on chain failure stays failed even though activity reconciliation is pending`() = runTest {
        coEvery { repo.observeUnbond(any(), any(), any(), any(), any(), any()) } returns ExtrinsicSubmitStatus(false, "failed", "block")
        assertEquals(TransactionOutcome.FailedOnChain("failed", "block", TransactionChainFailure.EXECUTION_FAILED), subject.observeUnbond(amount))
        verify { history.saveTransaction("owner", ofType<Transaction.ReferralUnbond>()) }
    }
    @Test fun `activity storage failure never rewrites a confirmed referral`() = runTest {
        coEvery { repo.observeSetReferrer(any(), any(), any(), any(), any()) } returns ExtrinsicSubmitStatus(true, "set", "block")
        every { history.saveTransaction(any(), any()) } throws IllegalStateException("storage unavailable")
        assertEquals(TransactionOutcome.Confirmed("set", "block"), subject.observeSetReferrer("other"))
    }
    @Test fun `cancelled credentials do not submit or create an activity`() = runTest {
        coEvery { credentials.retrieveKeyPair(any()) } throws CancellationException("closed")
        try { subject.observeBond(amount); fail("Cancellation must propagate") } catch (_: CancellationException) { }
        coVerify(exactly = 0) { repo.observeBond(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { history.saveTransaction(any(), any()) }
    }
    @Test fun `balance flow keeps missing values distinct from zero`() = runTest {
        every { repo.observeReferrerBalance("owner", token) } returns flowOf(null, BigDecimal.ZERO, amount)
        assertEquals(listOf(null, BigDecimal.ZERO, amount), subject.observeReferrerBalance().toList())
    }
    @Test fun `referrer and referral subscriptions capture wallet at collection time`() = runTest {
        val referrals = subject.observeReferrals(); val referrer = subject.observeMyReferrer()
        selected = SoraAccount("other", "Other")
        every { repo.observerReferrals("other") } returns flowOf("first", "second")
        every { repo.observeMyReferrer("other") } returns flowOf("sponsor", "")
        assertEquals(listOf("first", "second"), referrals.toList()); assertEquals(listOf("sponsor", ""), referrer.toList())
    }
    @Test fun `rewards retain integer precision and updated empty snapshots`() = runTest {
        every { repo.getReferralRewards() } returns flowOf(listOf(ReferralReward("address", "2123456789012345678")), emptyList())
        val snapshots = subject.getReferrals().toList()
        assertEquals(amount, snapshots[0].single().xorAmount)
        assertTrue(snapshots[1].isEmpty())
    }
}
