/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_blockexplorer_impl.presentation.txdetails

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.verify
import java.math.BigDecimal
import java.util.Date
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.androidfoundation.testing.getOrAwaitValue
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.feature_blockexplorer_api.domain.TransactionHistoryHandler
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.BasicTxDetailsItem
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.BasicTxDetailsState
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.TxDetailsScreenState
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.TxType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.DemeterType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBase
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatusDetail
import jp.co.soramitsu.feature_blockexplorer_impl.testdata.TestTransactions
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_data.TestAccounts
import jp.co.soramitsu.test_data.TestTokens
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

@ExperimentalCoroutinesApi
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
    private lateinit var soraConfigManager: SoraConfigManager

    @MockK
    private lateinit var clipboardManager: BasicClipboardManager

    @MockK
    private lateinit var resourceManager: ResourceManager

    @MockK
    private lateinit var dateTimeFormatter: DateTimeFormatter

    @MockK
    private lateinit var router: WalletRouter

    private val nf = NumbersFormatter()

    private val mockedUri = DEFAULT_ICON_URI

    private val txHash = "txHash"
    private val date = "10 Jan. 2021 12:12"

    private lateinit var viewModel: TxDetailsViewModel

    private fun explorerUrl(txHash: String) = "https://sorametrics.org/sorav2?tab=extrinsics&q=$txHash"

    private fun base(hash: String) = TransactionBase(
        txHash = hash,
        blockHash = "blockHash",
        fee = BigDecimal.ONE,
        status = TransactionStatus.COMMITTED,
        timestamp = 1673918013,
    )

    private suspend fun assertExplorerUrlFor(tx: Transaction) {
        initTestData(tx)
        delay(10)

        assertEquals(explorerUrl(tx.base.txHash), viewModel.txDetailsScreenState.value.basicTxDetailsState.explorerUrl)
    }

    private suspend fun initTestData(
        tx: Transaction = TestTransactions.sendFailedTx,
        localTransactions: Flow<Boolean> = flowOf(true),
    ) {
        coEvery { transactionHistoryHandler.getTransaction(txHash) } returns tx
        coEvery {
            soraConfigManager.getTransactionExplorerUrl(tx.base.txHash)
        } returns explorerUrl(tx.base.txHash)
        every { transactionHistoryHandler.flowLocalTransactions() } returns localTransactions
        every { resourceManager.getString(R.string.common_recipient) } returns "recipient"
        every { resourceManager.getString(R.string.common_sent) } returns "sent"
        every { resourceManager.getString(R.string.common_bridged) } returns "bridged"
        every { resourceManager.getString(R.string.asset_sora_fullname) } returns "SORA"
        every { resourceManager.getString(R.string.asset_ether_fullname) } returns "Ethereum"
        every { resourceManager.getString(R.string.eth_tx_address) } returns "Ethereum address"
        every { resourceManager.getString(R.string.eth_tx_hash) } returns "Ethereum transaction hash"
        every { resourceManager.getString(R.string.polkaswap_swapped) } returns "swapped"
        every { resourceManager.getString(R.string.details_sent_to_pool) } returns "sent to pool"
        every { resourceManager.getString(R.string.details_receive_from_pool) } returns "received from pool"
        every { resourceManager.getString(R.string.demeter_claimed_reward) } returns "claimed reward"
        every {
            resourceManager.getString(R.string.demeter_staked_liquidity)
        } returns "staked liquidity"
        every {
            resourceManager.getString(R.string.demeter_unstaked_liquidity)
        } returns "unstaked liquidity"
        every { resourceManager.getString(R.string.wallet_bonded) } returns "wallet bonded"
        every { resourceManager.getString(R.string.wallet_unbonded) } returns "wallet unbonded"
        every { resourceManager.getString(R.string.referrer_set) } returns "referrer set"
        every { resourceManager.getString(R.string.history_referrer) } returns "referrer"
        every { resourceManager.getString(R.string.activity_referral_title) } returns "referrer join"
        every { resourceManager.getString(R.string.history_referral) } returns "referral"
        every { resourceManager.getString(R.string.common_received) } returns "received"
        every { resourceManager.getString(R.string.received_from_adar) } returns "received from adar"
        every { resourceManager.getQuantityString(R.plurals.referral_invitations, 1) } returns "invitation"
        every {
            dateTimeFormatter.formatDate(
                Date(tx.base.timestamp),
                DateTimeFormatter.DD_MMM_YYYY_HH_MM
            )
        } returns date
        every { clipboardManager.addToClipboard(any()) } returns Unit

        viewModel = TxDetailsViewModel(
            assetsInteractor,
            walletInteractor,
            transactionHistoryHandler,
            clipboardManager,
            resourceManager,
            dateTimeFormatter,
            nf,
            soraConfigManager,
            txHash
        )
    }

    @Before
    fun setUp() = runTest {
        coEvery { assetsInteractor.getCurSoraAccount() } returns TestAccounts.soraAccount
        coEvery { walletInteractor.getFeeToken() } returns TestTokens.xorToken
        coEvery { router.popBackStackFragment() } returns Unit
    }

    @Test
    fun `init with receive transfer tx`() = runTest {
        initTestData(TestTransactions.receiveSuccessfulTx)
        advanceUntilIdle()
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
                    null,
                    null,
                    R.drawable.ic_new_arrow_down_24,
                    "received",
                    explorerUrl = explorerUrl(base.txHash)
                ),
                "+${token.printBalance(amount, nf)}",
                null,
                "+${token.printFiat(amount, nf)}",
                mockedUri,
                null,
                null,
                true,
                TxType.REFERRAL_TRANSFER
            )
        }

        assertEquals(expectedScreenState.toString(), viewModel.txDetailsScreenState.value.toString())
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
                    "sent",
                    explorerUrl = explorerUrl(base.txHash)
                ),
                token.printBalance(amount, nf),
                null,
                token.printFiat(amount, nf),
                mockedUri,
                null,
                null,
                false,
                TxType.REFERRAL_TRANSFER
            )
        }

        delay(10)

        assertEquals(expectedScreenState.toString(), viewModel.txDetailsScreenState.value.toString())
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
                    "sent",
                    explorerUrl = explorerUrl(base.txHash)
                ),
                token.printBalance(amount, nf),
                null,
                token.printFiat(amount, nf),
                mockedUri,
                null,
                null,
                false,
                TxType.REFERRAL_TRANSFER
            )
        }

        delay(10)

        assertEquals(expectedScreenState.toString(), viewModel.txDetailsScreenState.value.toString())
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
                    "sent",
                    explorerUrl = explorerUrl(base.txHash)
                ),
                token.printBalance(amount, nf),
                null,
                token.printFiat(amount, nf),
                mockedUri,
                null,
                null,
                false,
                TxType.REFERRAL_TRANSFER
            )
        }

        delay(10)

        assertEquals(expectedScreenState.toString(), viewModel.txDetailsScreenState.value.toString())
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
                    "swapped",
                    explorerUrl = explorerUrl(base.txHash)
                ),
                tokenFrom.printBalance(amountFrom, nf),
                tokenTo.printBalance(amountTo, nf),
                "=${tokenFrom.printFiat(amountFrom, nf)}",
                mockedUri,
                mockedUri,
                null,
                false,
                TxType.SWAP
            )
        }

        delay(10)

        assertEquals(expectedScreenState.toString(), viewModel.txDetailsScreenState.value.toString())
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
                    "wallet unbonded",
                    explorerUrl = explorerUrl(base.txHash)
                ),
                token.printBalance(amount, nf),
                null,
                "+${token.printFiat(amount, nf)}",
                mockedUri,
                null,
                null,
                true,
                TxType.REFERRAL_TRANSFER
            )
        }

        delay(10)

        assertEquals(expectedScreenState.toString(), viewModel.txDetailsScreenState.value.toString())
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
                    "referrer set",
                    explorerUrl = explorerUrl(base.txHash)
                ),
                "--",
                null,
                "0",
                mockedUri,
                null,
                null,
                false,
                TxType.REFERRAL_TRANSFER
            )
        }

        delay(10)

        assertEquals(expectedScreenState.toString(), viewModel.txDetailsScreenState.value.toString())
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
                    "referrer join",
                    explorerUrl = explorerUrl(base.txHash)
                ),
                "-1 invitation",
                null,
                "0",
                mockedUri,
                null,
                null,
                false,
                TxType.REFERRAL_TRANSFER
            )
        }

        delay(10)

        assertEquals(expectedScreenState.toString(), viewModel.txDetailsScreenState.value.toString())
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
                    "wallet bonded",
                    explorerUrl = explorerUrl(base.txHash)
                ),
                token.printBalance(amount, nf),
                null,
                token.printFiat(amount, nf),
                mockedUri,
                null,
                null,
                false,
                TxType.REFERRAL_TRANSFER
            )
        }

        delay(10)

        assertEquals(expectedScreenState.toString(), viewModel.txDetailsScreenState.value.toString())
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
                    "sent to pool",
                    explorerUrl = explorerUrl(base.txHash)
                ),
                token1.printBalance(amount1, nf),
                token2.printBalance(amount2, nf),
                token1.printFiat(amount1, nf),
                mockedUri,
                mockedUri,
                null,
                false,
                TxType.LIQUIDITY
            )
        }

        delay(10)

        assertEquals(expectedScreenState.toString(), viewModel.txDetailsScreenState.value.toString())
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
                    "received from pool",
                    explorerUrl = explorerUrl(base.txHash)
                ),
                "+${token1.printBalance(amount1, nf)}",
                "+${token2.printBalance(amount2, nf)}",
                "+${token1.printFiat(amount1, nf)}",
                mockedUri,
                mockedUri,
                null,
                true,
                TxType.LIQUIDITY
            )
        }

        delay(10)

        assertEquals(expectedScreenState.toString(), viewModel.txDetailsScreenState.value.toString())
    }

    @Test
    fun `bridge tx exposes explorer url`() = runTest {
        assertExplorerUrlFor(
            Transaction.EthTransfer(
                base = base("bridgeHash"),
                amount = BigDecimal.ONE,
                token = TestTokens.xorToken,
                ethToken = TestTokens.ethToken,
                requestHash = "requestHash",
                sidechainAddress = "0xabc",
            )
        )
    }

    @Test
    fun `demeter tx exposes explorer url`() = runTest {
        assertExplorerUrlFor(
            Transaction.DemeterFarming(
                base = base("demeterHash"),
                type = DemeterType.STAKE,
                amount = BigDecimal.ONE,
                baseToken = TestTokens.xorToken,
                targetToken = TestTokens.valToken,
                rewardToken = TestTokens.pswapToken,
            )
        )
    }

    @Test
    fun `adar income tx exposes explorer url`() = runTest {
        assertExplorerUrlFor(
            Transaction.AdarIncome(
                base = base("adarHash"),
                amount = BigDecimal.ONE,
                peer = "cnPeer",
                token = TestTokens.xorToken,
            )
        )
    }

    // --- audit A5: the transaction page explains the status -----------------------------------

    private fun swapWith(status: TransactionStatus, detail: TransactionStatusDetail?) =
        Transaction.Swap(
            base = TransactionBase(
                txHash = "0x" + "ab".repeat(32),
                blockHash = null,
                fee = BigDecimal.ONE,
                status = status,
                timestamp = 1673918013,
                statusDetail = detail,
            ),
            tokenFrom = TestTokens.xorToken,
            tokenTo = TestTokens.valToken,
            amountFrom = BigDecimal.ONE,
            amountTo = BigDecimal.TEN,
            market = Market.SMART,
        )

    private suspend fun stateFor(tx: Transaction): BasicTxDetailsState {
        every { resourceManager.getString(any()) } answers { "text-${firstArg<Int>()}" }
        initTestData(tx)
        delay(10)
        return viewModel.txDetailsScreenState.value.basicTxDetailsState
    }

    @Test
    fun `a waiting transaction says it is waiting and must not be sent again`() = runTest {
        val state = stateFor(
            swapWith(
                TransactionStatus.PENDING,
                TransactionStatusDetail(TransactionStatusDetail.Kind.WAITING_FOR_CONFIRMATION),
            )
        )

        assertEquals(TransactionStatus.PENDING, state.txStatus)
        assertEquals("text-${R.string.tx_outcome_note_waiting}", state.statusNote)
    }

    @Test
    fun `an uncertain submission says it is being checked`() = runTest {
        val state = stateFor(
            swapWith(
                TransactionStatus.PENDING,
                TransactionStatusDetail(TransactionStatusDetail.Kind.SUBMISSION_UNCERTAIN),
            )
        )

        assertEquals("text-${R.string.tx_outcome_note_uncertain}", state.statusNote)
    }

    @Test
    fun `a transaction that failed on chain shows the reported reason`() = runTest {
        val state = stateFor(
            swapWith(
                TransactionStatus.REJECTED,
                TransactionStatusDetail(
                    TransactionStatusDetail.Kind.FAILED_ON_CHAIN,
                    "LiquidityProxy.SlippageNotTolerated",
                ),
            )
        )

        assertEquals(TransactionStatus.REJECTED, state.txStatus)
        assertEquals(
            "text-${R.string.tx_outcome_note_failed_on_chain}\n\n" +
                "text-${R.string.tx_outcome_note_failed_on_chain_reason}",
            state.statusNote,
        )
        // It is in a block, so the explorer can show it.
        assertEquals(explorerUrl("0x" + "ab".repeat(32)), state.explorerUrl)
    }

    @Test
    fun `a refused transaction is failed with its reason and has no explorer link`() = runTest {
        val state = stateFor(
            swapWith(
                TransactionStatus.REJECTED,
                TransactionStatusDetail(
                    TransactionStatusDetail.Kind.REFUSED_CANNOT_PAY_FEE,
                    "Inability to pay some fees",
                ),
            )
        )

        assertEquals(TransactionStatus.REJECTED, state.txStatus)
        assertEquals("text-${R.string.tx_outcome_note_refused_fee}", state.statusNote)
        assertEquals(null, state.explorerUrl)
        // "No fee was charged": the fee that was only expected is not listed as paid.
        assertEquals(null, state.networkFee)
        assertEquals(null, state.networkFeeFiat)
    }

    @Test
    fun `a transaction that reached a block keeps its fee`() = runTest {
        val failed = stateFor(
            swapWith(
                TransactionStatus.REJECTED,
                TransactionStatusDetail(TransactionStatusDetail.Kind.FAILED_ON_CHAIN),
            )
        )
        // It was included, so the fee was charged and is shown.
        assertEquals(true, failed.networkFee != null)

        val waiting = stateFor(waitingSwap)
        assertEquals(true, waiting.networkFee != null)
    }

    private fun genericWith(
        status: TransactionStatus,
        detail: TransactionStatusDetail?,
        ambiguous: Boolean,
    ) = Transaction.Sora2Submission(
        base = TransactionBase(
            txHash = "0x" + "ab".repeat(32),
            blockHash = null,
            fee = BigDecimal.ZERO,
            status = status,
            timestamp = 1673918013,
            statusDetail = detail,
        ),
        networkId = "sora2",
        submissionIsAmbiguous = ambiguous,
        terminalBlockNumber = null,
    )

    @Test
    fun `an entry known only by its hash is never titled submitted when the node refused it`() =
        runTest {
            // The journal row of a refused transaction is still "submission unknown".
            val state = stateFor(
                genericWith(
                    TransactionStatus.REJECTED,
                    TransactionStatusDetail(TransactionStatusDetail.Kind.REFUSED_NOT_VALID),
                    ambiguous = true,
                )
            )

            assertEquals("text-${R.string.tx_outcome_not_sent_title}", state.txTypeTitle)
            assertEquals(TransactionStatus.REJECTED, state.txStatus)
            assertEquals("text-${R.string.tx_outcome_note_refused_invalid}", state.statusNote)
            assertEquals(null, state.explorerUrl)
        }

    @Test
    fun `an entry known only by its hash keeps its submitted and unknown titles`() = runTest {
        val waiting = stateFor(
            genericWith(
                TransactionStatus.PENDING,
                TransactionStatusDetail(TransactionStatusDetail.Kind.WAITING_FOR_CONFIRMATION),
                ambiguous = false,
            )
        )
        assertEquals("text-${R.string.wallet_transaction_submitted}", waiting.txTypeTitle)

        val uncertain = stateFor(
            genericWith(
                TransactionStatus.PENDING,
                TransactionStatusDetail(TransactionStatusDetail.Kind.SUBMISSION_UNCERTAIN),
                ambiguous = true,
            )
        )
        assertEquals("text-${R.string.wallet_transaction_status_unknown}", uncertain.txTypeTitle)
    }

    // --- the page follows a waiting transaction ------------------------------------------------

    private val waitingSwap
        get() = swapWith(
            TransactionStatus.PENDING,
            TransactionStatusDetail(TransactionStatusDetail.Kind.WAITING_FOR_CONFIRMATION),
        )

    private fun shownStatus() = viewModel.txDetailsScreenState.value.basicTxDetailsState.txStatus

    @Test
    fun `the page asks for the status of a waiting transaction and stops once it is final`() =
        runTest {
            every { resourceManager.getString(any()) } answers { "text-${firstArg<Int>()}" }
            // Ticks on every write to the status journal. Nothing is replayed, as in a process
            // that was started after the transaction was sent.
            val journal = MutableSharedFlow<Boolean>()
            var checks = 0
            coEvery { transactionHistoryHandler.checkWaitingTransaction(txHash) } coAnswers {
                checks += 1
                if (checks == 2) {
                    // The second check finds the transaction in a finalized block.
                    coEvery { transactionHistoryHandler.getTransaction(txHash) } returns
                        swapWith(TransactionStatus.COMMITTED, null)
                    journal.emit(true)
                }
            }
            initTestData(waitingSwap, localTransactions = journal)

            advanceTimeBy(5_000)
            assertEquals(0, checks)
            assertEquals(TransactionStatus.PENDING, shownStatus())

            advanceTimeBy(8_000)
            // Before: the page changed only when the background worker next ran, and after a
            // restart it skipped that first change and stayed on "Pending".
            assertEquals(2, checks)
            assertEquals(TransactionStatus.COMMITTED, shownStatus())
            assertEquals(
                null,
                viewModel.txDetailsScreenState.value.basicTxDetailsState.statusNote,
            )

            advanceTimeBy(600_000)
            assertEquals(2, checks)
        }

    @Test
    fun `the first journal write after a restart changes the page`() = runTest {
        every { resourceManager.getString(any()) } answers { "text-${firstArg<Int>()}" }
        coEvery { transactionHistoryHandler.checkWaitingTransaction(txHash) } returns Unit
        val journal = MutableSharedFlow<Boolean>()
        initTestData(waitingSwap, localTransactions = journal)
        advanceTimeBy(1_000)
        assertEquals(TransactionStatus.PENDING, shownStatus())

        // The background worker resolved the transaction: it expired without being included.
        coEvery { transactionHistoryHandler.getTransaction(txHash) } returns swapWith(
            TransactionStatus.REJECTED,
            TransactionStatusDetail(TransactionStatusDetail.Kind.NOT_INCLUDED),
        )
        journal.emit(true)
        advanceTimeBy(1_000)

        // Seen on the emulator before the fix: the journal row was final, the page still said
        // "Pending" and "don't send it again".
        assertEquals(TransactionStatus.REJECTED, shownStatus())
        assertEquals(
            "text-${R.string.tx_outcome_note_not_included}",
            viewModel.txDetailsScreenState.value.basicTxDetailsState.statusNote,
        )
    }

    @Test
    fun `the page stops asking when the transaction can no longer be included`() = runTest {
        every { resourceManager.getString(any()) } answers { "text-${firstArg<Int>()}" }
        coEvery { transactionHistoryHandler.checkWaitingTransaction(txHash) } returns Unit
        initTestData(waitingSwap)

        advanceUntilIdle()

        coVerify(exactly = 80) { transactionHistoryHandler.checkWaitingTransaction(txHash) }
        assertEquals(TransactionStatus.PENDING, shownStatus())
    }

    @Test
    fun `a transaction with a final status is never asked about`() = runTest {
        every { resourceManager.getString(any()) } answers { "text-${firstArg<Int>()}" }
        initTestData(
            swapWith(
                TransactionStatus.REJECTED,
                TransactionStatusDetail(TransactionStatusDetail.Kind.REFUSED_CANNOT_PAY_FEE),
            )
        )

        advanceUntilIdle()

        coVerify(exactly = 0) { transactionHistoryHandler.checkWaitingTransaction(txHash) }
    }

    @Test
    fun `an ordinary confirmed transaction has no status note`() = runTest {
        val state = stateFor(swapWith(TransactionStatus.COMMITTED, null))

        assertEquals(null, state.statusNote)
        assertEquals(explorerUrl("0x" + "ab".repeat(32)), state.explorerUrl)
    }

    @Test
    fun `a failed read never ends the app`() = runTest {
        coEvery { transactionHistoryHandler.getTransaction(txHash) } throws
            IllegalStateException("database is busy")
        coEvery { assetsInteractor.getCurSoraAccount() } throws
            IllegalStateException("database is busy")
        every { transactionHistoryHandler.flowLocalTransactions() } returns flowOf(true)

        viewModel = TxDetailsViewModel(
            assetsInteractor,
            walletInteractor,
            transactionHistoryHandler,
            clipboardManager,
            resourceManager,
            dateTimeFormatter,
            nf,
            soraConfigManager,
            txHash,
        )
        delay(10)

        // The empty page stays; runTest would fail on an uncaught exception in viewModelScope.
        assertEquals(null, viewModel.txDetailsScreenState.value.basicTxDetailsState.statusNote)
    }

    @Test
    fun `onCopyClicked(String) called`() = runTest {
        initTestData()
        val copyText = "text"
        viewModel.onCopyClicked(copyText)

        verify { clipboardManager.addToClipboard(copyText) }
    }
}
