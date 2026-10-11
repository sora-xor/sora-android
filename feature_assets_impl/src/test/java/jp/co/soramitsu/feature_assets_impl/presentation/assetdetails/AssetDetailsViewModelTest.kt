/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause
*/

package jp.co.soramitsu.feature_assets_impl.presentation.assetdetails

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common_wallet.data.XorAssetBalance
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_impl.presentation.screens.assetdetails.AssetDetailsViewModel
import jp.co.soramitsu.feature_blockexplorer_api.domain.TransactionHistoryHandler
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_data.TestAssets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

/**
 * The asset page of an account with locked XOR (audit F4, LIVE-2). With the correct transferable
 * amount, an account whose XOR is all locked has nothing transferable; the page must still say
 * how much it holds and why none of it can be sent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AssetDetailsViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private val assetsInteractor = mockk<AssetsInteractor>()
    private val resourceManager = mockk<ResourceManager>().also {
        every { it.getString(R.string.asset_details_your_pools) } returns "Your %s pools"
        every { it.getString(R.string.send_locked_balance_note) } returns "%1\$s is locked"
    }
    private val poolsInteractor = mockk<PoolsInteractor>().also {
        every { it.subscribePoolsCacheOfCurAccount() } returns flowOf(emptyList())
    }
    private val historyHandler = mockk<TransactionHistoryHandler>().also {
        every { it.flowLocalTransactions() } returns emptyFlow()
        coEvery { it.getCachedEvents(any(), any()) } returns emptyList()
    }

    private fun TestScope.viewModel(asset: Asset): AssetDetailsViewModel {
        coEvery { assetsInteractor.getAssetOrThrow(asset.token.id) } returns asset
        val coroutineManager = mockk<CoroutineManager>().also {
            every { it.io } returns StandardTestDispatcher(testScheduler)
        }
        return AssetDetailsViewModel(
            assetId = asset.token.id,
            assetsInteractor = assetsInteractor,
            assetsRouter = mockk<AssetsRouter>(relaxed = true),
            walletRouter = mockk<WalletRouter>(relaxed = true),
            clipboardManager = mockk<BasicClipboardManager>(relaxed = true),
            numbersFormatter = NumbersFormatter(),
            poolsInteractor = poolsInteractor,
            polkaswapRouter = mockk<PolkaswapRouter>(relaxed = true),
            transactionHistoryHandler = historyHandler,
            resourceManager = resourceManager,
            coroutineManager = coroutineManager,
        )
    }

    /** free 110 XOR, all of it frozen by staking: nothing can be sent. */
    private val allLocked = TestAssets.xorAsset().copy(
        balance = TestAssets.lockedBalance(
            transferable = BigDecimal.ZERO,
            locked = BigDecimal("110"),
        )
    )

    private fun liveBreakdown(
        transferable: String,
        locked: String,
        reserved: String = "0",
    ) = XorAssetBalance(
        transferable = BigDecimal(transferable),
        frozen = BigDecimal(locked) + BigDecimal(reserved),
        totalBalance = BigDecimal(transferable) + BigDecimal(locked) + BigDecimal(reserved),
        locked = BigDecimal(locked),
        bonded = BigDecimal.ZERO,
        reserved = BigDecimal(reserved),
        redeemable = BigDecimal.ZERO,
        unbonding = BigDecimal.ZERO,
    )

    @Test
    fun `an account whose XOR is all locked sees the frozen amount, the reason and the breakdown`() =
        runTest {
            coEvery { assetsInteractor.getXorBalance(any()) } returns
                liveBreakdown(transferable = "0", locked = "110")
            val viewModel = viewModel(allLocked)
            advanceUntilIdle()

            val page = viewModel.state
            assertFalse(page.loading)
            assertEquals("0 XOR", page.state.transferableBalance)
            // Before the fix the breakdown was only read when something was transferable, so
            // this account saw "Frozen 0 XOR", no note, and a card that did not open.
            assertEquals("110 XOR", page.state.frozenBalance)
            assertEquals("110 XOR is locked", page.state.lockedNote)
            assertFalse(page.state.isTransferableBalanceAvailable)
            coVerify(exactly = 1) { assetsInteractor.getXorBalance(any()) }

            viewModel.onBalanceClick()
            advanceUntilIdle()

            val breakdown = viewModel.state.state.xorBalance
            assertNotNull(breakdown)
            assertTrue(breakdown.toString().contains("110 XOR"))
        }

    @Test
    fun `when the network cannot be asked the amounts saved with the balance are shown`() =
        runTest {
            coEvery { assetsInteractor.getXorBalance(any()) } throws IOException("socket closed")
            val viewModel = viewModel(allLocked)
            advanceUntilIdle()

            val page = viewModel.state
            assertFalse(page.loading)
            assertEquals("110 XOR", page.state.frozenBalance)
            assertEquals("110 XOR is locked", page.state.lockedNote)
            // A page that shows everything it has does not also raise an error dialog.
            assertNull(viewModel.errorLiveData.value)
            assertNull(viewModel.alertDialogLiveData.value)
            assertNull(viewModel.errorFromResourceLiveData.value)

            viewModel.onBalanceClick()
            advanceUntilIdle()
            assertNotNull(viewModel.state.state.xorBalance)
        }

    @Test
    fun `a selected node that never answers does not leave the page loading`() = runTest {
        coEvery { assetsInteractor.getXorBalance(any()) } coAnswers { awaitCancellation() }
        val viewModel = viewModel(allLocked)
        runCurrent()
        assertTrue(viewModel.state.loading)

        advanceTimeBy(AssetDetailsViewModel.XOR_BREAKDOWN_READ_TIMEOUT_MILLIS + 1)
        runCurrent()

        assertFalse(viewModel.state.loading)
        assertEquals("110 XOR", viewModel.state.state.frozenBalance)
        assertEquals("110 XOR is locked", viewModel.state.state.lockedNote)
    }

    @Test
    fun `right after the update the card follows the network, not the row the old version saved`() =
        runTest {
            // The store version saved this row: it knew no locks, so all 110 XOR looked liquid.
            val savedByOldVersion = TestAssets.xorAsset(BigDecimal("110"))
            coEvery { assetsInteractor.getXorBalance(any()) } returns
                liveBreakdown(transferable = "0", locked = "110")
            val viewModel = viewModel(savedByOldVersion)
            advanceUntilIdle()

            // Never "110 XOR liquid and 110 XOR frozen" on one card.
            val page = viewModel.state.state
            assertEquals("0 XOR", page.transferableBalance)
            assertEquals("110 XOR", page.frozenBalance)
            assertEquals("110 XOR is locked", page.lockedNote)
            assertFalse(page.isTransferableBalanceAvailable)
        }

    @Test
    fun `part of the balance locked keeps Send and names the locked part`() = runTest {
        val partlyLocked = TestAssets.xorAsset().copy(
            balance = TestAssets.lockedBalance(
                transferable = BigDecimal("12"),
                locked = BigDecimal("3.5"),
                reserved = BigDecimal("2"),
            )
        )
        coEvery { assetsInteractor.getXorBalance(any()) } returns
            liveBreakdown(transferable = "12", locked = "3.5", reserved = "2")
        val viewModel = viewModel(partlyLocked)
        advanceUntilIdle()

        val page = viewModel.state.state
        assertEquals("12 XOR", page.transferableBalance)
        assertEquals("5.5 XOR", page.frozenBalance)
        assertEquals("3.5 XOR is locked", page.lockedNote)
        assertTrue(page.isTransferableBalanceAvailable)
    }

    @Test
    fun `XOR without locks and other assets show no locked note`() = runTest {
        coEvery { assetsInteractor.getXorBalance(any()) } returns
            liveBreakdown(transferable = "12", locked = "0", reserved = "2")
        val xor = viewModel(
            TestAssets.xorAsset().copy(
                balance = TestAssets.lockedBalance(
                    transferable = BigDecimal("12"),
                    locked = BigDecimal.ZERO,
                    reserved = BigDecimal("2"),
                )
            )
        )
        advanceUntilIdle()
        assertEquals("2 XOR", xor.state.state.frozenBalance)
        assertNull(xor.state.state.lockedNote)

        val other = viewModel(TestAssets.valAsset(BigDecimal("5")))
        advanceUntilIdle()
        assertNull(other.state.state.frozenBalance)
        assertNull(other.state.state.lockedNote)
        // Only XOR has a breakdown to read.
        coVerify(exactly = 1) { assetsInteractor.getXorBalance(any()) }
    }
}
