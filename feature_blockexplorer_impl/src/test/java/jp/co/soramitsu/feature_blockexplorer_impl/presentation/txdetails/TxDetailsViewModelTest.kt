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
import jp.co.soramitsu.feature_blockexplorer_impl.testdata.TestTransactions
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_data.TestAccounts
import jp.co.soramitsu.test_data.TestTokens
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
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

    private suspend fun initTestData(tx: Transaction = TestTransactions.sendFailedTx) {
        coEvery { transactionHistoryHandler.getTransaction(txHash) } returns tx
        coEvery { soraConfigManager.getTransactionExplorerUrl(tx.base.txHash) } returns explorerUrl(tx.base.txHash)
        every { transactionHistoryHandler.flowLocalTransactions() } returns flowOf(true)
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
        every { resourceManager.getString(R.string.demeter_staked_liquidity) } returns "staked liquidity"
        every { resourceManager.getString(R.string.demeter_unstaked_liquidity) } returns "unstaked liquidity"
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

    @Test
    fun `onCopyClicked(String) called`() = runTest {
        initTestData()
        val copyText = "text"
        viewModel.onCopyClicked(copyText)

        verify { clipboardManager.addToClipboard(copyText) }
    }
}
