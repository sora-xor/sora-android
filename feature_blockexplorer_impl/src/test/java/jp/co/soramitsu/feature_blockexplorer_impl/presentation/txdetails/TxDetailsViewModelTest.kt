/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_impl.presentation.txdetails

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockkStatic
import io.mockk.verify
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_blockexplorer_api.domain.TransactionHistoryHandler
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.BasicTxDetailsItem
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.BasicTxDetailsState
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.TxDetailsScreenState
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.TxType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_impl.testdata.TestTransactions
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_data.TestAccounts
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.getOrAwaitValue
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import java.util.Date

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class TxDetailsViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var assetsInteractor: AssetsInteractor

    @MockK
    private lateinit var walletInteractor: WalletInteractor

    @MockK
    private lateinit var transactionHistoryHandler: TransactionHistoryHandler

    @MockK
    private lateinit var clipboardManager: ClipboardManager

    @MockK
    private lateinit var resourceManager: ResourceManager

    @MockK
    private lateinit var dateTimeFormatter: DateTimeFormatter

    @MockK
    private lateinit var router: WalletRouter

    private val nf = NumbersFormatter()

    private val mockedUri = Mockito.mock(Uri::class.java)

    private val txHash = "txHash"
    private val date = "10 Jan. 2021 12:12"

    private lateinit var viewModel: TxDetailsViewModel

    private suspend fun initTestData(tx: Transaction = TestTransactions.sendFailedTx) {
        coEvery { transactionHistoryHandler.getTransaction(txHash) } returns tx
        every { transactionHistoryHandler.flowLocalTransactions() } returns flowOf(true)
        every { resourceManager.getString(R.string.common_recipient) } returns "recipient"
        every { resourceManager.getString(R.string.common_sent) } returns "sent"
        every { resourceManager.getString(R.string.polkaswap_swapped) } returns "swapped"
        every { resourceManager.getString(R.string.details_sent_to_pool) } returns "sent to pool"
        every { resourceManager.getString(R.string.details_receive_from_pool) } returns "received from pool"
        every { resourceManager.getString(R.string.wallet_bonded) } returns "wallet bonded"
        every { resourceManager.getString(R.string.wallet_unbonded) } returns "wallet unbonded"
        every { resourceManager.getString(R.string.referrer_set) } returns "referrer set"
        every { resourceManager.getString(R.string.history_referrer) } returns "referrer"
        every { resourceManager.getString(R.string.activity_referral_title) } returns "referrer join"
        every { resourceManager.getString(R.string.history_referral) } returns "referral"
        every { resourceManager.getString(R.string.common_received) } returns "received"
        every { resourceManager.getQuantityString(R.plurals.referral_invitations, 1) } returns "invitation"
        every {
            dateTimeFormatter.formatDate(
                Date(tx.base.timestamp),
                DateTimeFormatter.DD_MMM_YYYY_HH_MM
            )
        } returns date
        every { clipboardManager.addToClipboard("copy item", any()) } returns Unit

        viewModel = TxDetailsViewModel(
            assetsInteractor,
            walletInteractor,
            transactionHistoryHandler,
            clipboardManager,
            resourceManager,
            dateTimeFormatter,
            nf,
            txHash
        )
    }

    @Before
    fun setUp() = runTest {
        mockkStatic(Uri::parse)
        every { Uri.parse(any()) } returns mockedUri
        coEvery { assetsInteractor.getCurSoraAccount() } returns TestAccounts.soraAccount
        coEvery { walletInteractor.getFeeToken() } returns TestTokens.xorToken
        coEvery { router.popBackStackFragment() } returns Unit
    }

    @Test
    fun `init with receive transfer tx`() = runTest {
        initTestData(TestTransactions.receiveSuccessfulTx)
        val s = viewModel.toolbarState.getOrAwaitValue()
        assertEquals(false, s.basic.visibility)

        val expectedScreenState = with(TestTransactions.receiveSuccessfulTx) {
            TxDetailsScreenState(
                BasicTxDetailsState(
                    base.txHash,
                    base.blockHash,
                    TestAccounts.soraAccount.substrateAddress,
                    listOf(
                        BasicTxDetailsItem("recipient", peer)
                    ),
                    base.status,
                    date,
                    TestTokens.xorToken.printBalance(base.fee, nf),
                    TestTokens.xorToken.printFiat(base.fee, nf),
                    R.drawable.ic_new_arrow_down_24,
                    "received"
                ),
                "+${token.printBalance(amount, nf)}",
                null,
                "+${token.printFiat(amount, nf)}",
                mockedUri,
                null,
                true,
                TxType.REFERRAL_TRANSFER
            )
        }

        delay(10)

        assertEquals(expectedScreenState.toString(), viewModel.txDetailsScreenState.toString())
    }

    @Test
    fun `init with successful transfer tx`() = runTest {
        initTestData(TestTransactions.sendSuccessfulTx)
        val s = viewModel.toolbarState.getOrAwaitValue()
        assertEquals(false, s.basic.visibility)

        val expectedScreenState = with(TestTransactions.sendSuccessfulTx) {
            TxDetailsScreenState(
                BasicTxDetailsState(
                    base.txHash,
                    base.blockHash,
                    TestAccounts.soraAccount.substrateAddress,
                    listOf(
                        BasicTxDetailsItem("recipient", peer)
                    ),
                    base.status,
                    date,
                    TestTokens.xorToken.printBalance(base.fee, nf),
                    TestTokens.xorToken.printFiat(base.fee, nf),
                    R.drawable.ic_new_arrow_up_24,
                    "sent"
                ),
                token.printBalance(amount, nf),
                null,
                token.printFiat(amount, nf),
                mockedUri,
                null,
                false,
                TxType.REFERRAL_TRANSFER
            )
        }

        delay(10)

        assertEquals(expectedScreenState.toString(), viewModel.txDetailsScreenState.toString())
    }

    @Test
    fun `init with pending transfer tx`() = runTest {
        initTestData(TestTransactions.sendPendingTx)
        val s = viewModel.toolbarState.getOrAwaitValue()
        assertEquals(false, s.basic.visibility)

        val expectedScreenState = with(TestTransactions.sendPendingTx) {
            TxDetailsScreenState(
                BasicTxDetailsState(
                    base.txHash,
                    base.blockHash,
                    TestAccounts.soraAccount.substrateAddress,
                    listOf(
                        BasicTxDetailsItem("recipient", peer)
                    ),
                    base.status,
                    date,
                    TestTokens.xorToken.printBalance(base.fee, nf),
                    TestTokens.xorToken.printFiat(base.fee, nf),
                    R.drawable.ic_new_arrow_up_24,
                    "sent"
                ),
                token.printBalance(amount, nf),
                null,
                token.printFiat(amount, nf),
                mockedUri,
                null,
                false,
                TxType.REFERRAL_TRANSFER
            )
        }

        delay(10)

        assertEquals(expectedScreenState.toString(), viewModel.txDetailsScreenState.toString())
    }

    @Test
    fun `init with failed transfer tx`() = runTest {
        initTestData(TestTransactions.sendFailedTx)
        val s = viewModel.toolbarState.getOrAwaitValue()
        assertEquals(false, s.basic.visibility)

        val expectedScreenState = with(TestTransactions.sendFailedTx) {
            TxDetailsScreenState(
                BasicTxDetailsState(
                    base.txHash,
                    base.blockHash,
                    TestAccounts.soraAccount.substrateAddress,
                    listOf(
                        BasicTxDetailsItem("recipient", peer)
                    ),
                    base.status,
                    date,
                    TestTokens.xorToken.printBalance(base.fee, nf),
                    TestTokens.xorToken.printFiat(base.fee, nf),
                    R.drawable.ic_new_arrow_up_24,
                    "sent"
                ),
                token.printBalance(amount, nf),
                null,
                token.printFiat(amount, nf),
                mockedUri,
                null,
                false,
                TxType.REFERRAL_TRANSFER
            )
        }

        delay(10)

        assertEquals(expectedScreenState.toString(), viewModel.txDetailsScreenState.toString())
    }

    @Test
    fun `init with swap tx`() = runTest {
        initTestData(TestTransactions.swapTx)
        val s = viewModel.toolbarState.getOrAwaitValue()
        assertEquals(false, s.basic.visibility)

        val expectedScreenState = with(TestTransactions.swapTx) {
            TxDetailsScreenState(
                BasicTxDetailsState(
                    base.txHash,
                    base.blockHash,
                    TestAccounts.soraAccount.substrateAddress,
                    emptyList(),
                    base.status,
                    date,
                    TestTokens.xorToken.printBalance(base.fee, nf),
                    TestTokens.xorToken.printFiat(base.fee, nf),
                    R.drawable.ic_refresh_24,
                    "swapped"
                ),
                tokenFrom.printBalance(amountFrom, nf),
                tokenTo.printBalance(amountTo, nf),
                "=${tokenFrom.printFiat(amountFrom, nf)}",
                mockedUri,
                mockedUri,
                false,
                TxType.SWAP
            )
        }

        delay(10)

        assertEquals(expectedScreenState.toString(), viewModel.txDetailsScreenState.toString())
    }

    @Test
    fun `init with referralUnbond tx`() = runTest {
        initTestData(TestTransactions.unbondTx)
        val s = viewModel.toolbarState.getOrAwaitValue()
        assertEquals(false, s.basic.visibility)

        val expectedScreenState = with(TestTransactions.unbondTx) {
            TxDetailsScreenState(
                BasicTxDetailsState(
                    base.txHash,
                    base.blockHash,
                    TestAccounts.soraAccount.substrateAddress,
                    emptyList(),
                    base.status,
                    date,
                    TestTokens.xorToken.printBalance(base.fee, nf),
                    TestTokens.xorToken.printFiat(base.fee, nf),
                    R.drawable.ic_new_arrow_down_24,
                    "wallet unbonded"
                ),
                token.printBalance(amount, nf),
                null,
                "+${token.printFiat(amount, nf)}",
                mockedUri,
                null,
                true,
                TxType.REFERRAL_TRANSFER
            )
        }

        delay(10)

        assertEquals(expectedScreenState.toString(), viewModel.txDetailsScreenState.toString())
    }

    @Test
    fun `init with setReferrer tx`() = runTest {
        initTestData(TestTransactions.setReferrerTx)
        val s = viewModel.toolbarState.getOrAwaitValue()
        assertEquals(false, s.basic.visibility)

        val expectedScreenState = with(TestTransactions.setReferrerTx) {
            TxDetailsScreenState(
                BasicTxDetailsState(
                    base.txHash,
                    base.blockHash,
                    TestAccounts.soraAccount.substrateAddress,
                    listOf(
                        BasicTxDetailsItem(
                            "referrer",
                            who
                        )
                    ),
                    base.status,
                    date,
                    null,
                    null,
                    R.drawable.ic_new_arrow_up_24,
                    "referrer set"
                ),
                "--",
                null,
                "0",
                mockedUri,
                null,
                false,
                TxType.REFERRAL_TRANSFER
            )
        }

        delay(10)

        assertEquals(expectedScreenState.toString(), viewModel.txDetailsScreenState.toString())
    }

    @Test
    fun `init with referral join tx`() = runTest {
        initTestData(TestTransactions.joinReferralTx)
        val s = viewModel.toolbarState.getOrAwaitValue()
        assertEquals(false, s.basic.visibility)

        val expectedScreenState = with(TestTransactions.joinReferralTx) {
            TxDetailsScreenState(
                BasicTxDetailsState(
                    base.txHash,
                    base.blockHash,
                    TestAccounts.soraAccount.substrateAddress,
                    listOf(
                        BasicTxDetailsItem(
                            "referral",
                            who
                        )
                    ),
                    base.status,
                    date,
                    TestTokens.xorToken.printBalance(base.fee, nf),
                    TestTokens.xorToken.printFiat(base.fee, nf),
                    R.drawable.ic_new_arrow_up_24,
                    "referrer join"
                ),
                "-1 invitation",
                null,
                "0",
                mockedUri,
                null,
                false,
                TxType.REFERRAL_TRANSFER
            )
        }

        delay(10)

        assertEquals(expectedScreenState.toString(), viewModel.txDetailsScreenState.toString())
    }

    @Test
    fun `init with referralBond tx`() = runTest {
        initTestData(TestTransactions.bondTx)
        val s = viewModel.toolbarState.getOrAwaitValue()
        assertEquals(false, s.basic.visibility)

        val expectedScreenState = with(TestTransactions.bondTx) {
            TxDetailsScreenState(
                BasicTxDetailsState(
                    base.txHash,
                    base.blockHash,
                    TestAccounts.soraAccount.substrateAddress,
                    emptyList(),
                    base.status,
                    date,
                    TestTokens.xorToken.printBalance(base.fee, nf),
                    TestTokens.xorToken.printFiat(base.fee, nf),
                    R.drawable.ic_new_arrow_up_24,
                    "wallet bonded"
                ),
                token.printBalance(amount, nf),
                null,
                token.printFiat(amount, nf),
                mockedUri,
                null,
                false,
                TxType.REFERRAL_TRANSFER
            )
        }

        delay(10)

        assertEquals(expectedScreenState.toString(), viewModel.txDetailsScreenState.toString())
    }

    @Test
    fun `init with addLiquidity tx`() = runTest {
        initTestData(TestTransactions.addLiquidityTx)
        val s = viewModel.toolbarState.getOrAwaitValue()
        assertEquals(false, s.basic.visibility)

        val expectedScreenState = with(TestTransactions.addLiquidityTx) {
            TxDetailsScreenState(
                BasicTxDetailsState(
                    base.txHash,
                    base.blockHash,
                    TestAccounts.soraAccount.substrateAddress,
                    emptyList(),
                    base.status,
                    date,
                    TestTokens.xorToken.printBalance(base.fee, nf),
                    TestTokens.xorToken.printFiat(base.fee, nf),
                    R.drawable.ic_new_arrow_up_24,
                    "sent to pool"
                ),
                token1.printBalance(amount1, nf),
                token2.printBalance(amount2, nf),
                token1.printFiat(amount1, nf),
                mockedUri,
                mockedUri,
                false,
                TxType.LIQUIDITY
            )
        }

        delay(10)

        assertEquals(expectedScreenState.toString(), viewModel.txDetailsScreenState.toString())
    }

    @Test
    fun `init with receiveLiquidity tx`() = runTest {
        initTestData(TestTransactions.withdrawLiquidityTx)
        val s = viewModel.toolbarState.getOrAwaitValue()
        assertEquals(false, s.basic.visibility)

        val expectedScreenState = with(TestTransactions.withdrawLiquidityTx) {
            TxDetailsScreenState(
                BasicTxDetailsState(
                    base.txHash,
                    base.blockHash,
                    TestAccounts.soraAccount.substrateAddress,
                    emptyList(),
                    base.status,
                    date,
                    TestTokens.xorToken.printBalance(base.fee, nf),
                    TestTokens.xorToken.printFiat(base.fee, nf),
                    R.drawable.ic_new_arrow_down_24,
                    "received from pool"
                ),
                "+${token1.printBalance(amount1, nf)}",
                "+${token2.printBalance(amount2, nf)}",
                "+${token1.printFiat(amount1, nf)}",
                mockedUri,
                mockedUri,
                true,
                TxType.LIQUIDITY
            )
        }

        delay(10)

        assertEquals(expectedScreenState.toString(), viewModel.txDetailsScreenState.toString())
    }

    @Test
    fun `onCopyClicked(String) called`() = runTest {
        initTestData()
        val copyText = "text"
        viewModel.onCopyClicked(copyText)

        verify { clipboardManager.addToClipboard("copy item", copyText) }
    }
}