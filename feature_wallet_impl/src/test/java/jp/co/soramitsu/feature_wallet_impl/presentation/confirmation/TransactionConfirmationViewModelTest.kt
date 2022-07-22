/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.confirmation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import io.mockk.every
import io.mockk.mockkObject
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.TextFormatter
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.getOrAwaitValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyBoolean
import org.mockito.BDDMockito.anyInt
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.times
import org.mockito.BDDMockito.verifyNoInteractions
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import java.math.BigDecimal

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class TransactionConfirmationViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var ethInteractor: EthereumInteractor

    @Mock
    private lateinit var numbersFormatter: NumbersFormatter

    @Mock
    private lateinit var textFormatter: TextFormatter

    @Mock
    private lateinit var router: WalletRouter

    @Mock
    private lateinit var progress: WithProgress

    @Mock
    private lateinit var clip: ClipboardManager

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var mockOb: Observer<Unit>

    private lateinit var viewModel: TransactionConfirmationViewModel

    @Before
    fun setUp() = runTest {
        given(walletInteractor.getAssetOrThrow(any())).willReturn(assetList().first())
        given(walletInteractor.getFeeToken()).willReturn(tokens().first())
        given(
            numbersFormatter.formatBigDecimal(
                any(),
                anyInt(),
                anyBoolean()
            )
        ).willReturn("123.456")
        viewModel = TransactionConfirmationViewModel(
            walletInteractor,
            router,
            resourceManager,
            numbersFormatter,
            clip,
            progress,
            BigDecimal.ONE,
            BigDecimal.TEN,
            "id2",
            "peerFullName",
            TransferType.VAL_TRANSFER,
        )
    }

    @Test
    fun `test init`() {
        val b = viewModel.balanceFormattedLiveData.getOrAwaitValue()
        assertEquals("123.456", b.amount)
        val i = viewModel.inputTokenSymbolLiveData.getOrAwaitValue()
        assertEquals("XOR", i)
        val t = viewModel.inputTokenNameLiveData.getOrAwaitValue()
        assertEquals("Sora token", t)
    }

    @Test
    fun `back click`() {
        viewModel.backButtonPressed()
        verify(router).popBackStackFragment()
    }

    @Test
    fun `copy address click`() {
        viewModel.copyAddress()
        viewModel.copiedAddressEvent.observeForever(mockOb)
        verify(mockOb, times(1)).onChanged(Unit)
    }

    @Test
    fun `next click`() = runTest {
        given(
            walletInteractor.observeTransfer(
                any(),
                any(),
                any(),
                any()
            )
        ).willReturn(true)
        viewModel.nextClicked()
        advanceUntilIdle()
        val success = viewModel.transactionSuccessEvent.getOrAwaitValue()
        assertEquals(Unit, success)
        verify(router).returnToWalletFragment()
    }

    @Test
    fun `next click false`() = runTest {
        given(
            walletInteractor.observeTransfer(
                any(),
                any(),
                any(),
                any()
            )
        ).willReturn(false)
        viewModel.nextClicked()
        advanceUntilIdle()
        viewModel.transactionSuccessEvent.observeForever(mockOb)
        verifyNoInteractions(mockOb)
        verify(router).returnToWalletFragment()
    }

    @Test
    fun `next click error`() = runTest {
        val t = IllegalStateException()
        given(
            walletInteractor.observeTransfer(
                any(),
                any(),
                any(),
                any()
            )
        ).willThrow(t)
        mockkObject(FirebaseWrapper)
        every { FirebaseWrapper.recordException(t) } returns Unit
        viewModel.nextClicked()
        advanceUntilIdle()
        viewModel.transactionSuccessEvent.observeForever(mockOb)
        verifyNoInteractions(mockOb)
        io.mockk.verify(exactly = 1) { FirebaseWrapper.recordException(t) }
        verify(router).returnToWalletFragment()
    }

    private fun tokens() = listOf(
        TestTokens.xorToken, TestTokens.valToken,
    )

    private fun assetList() = listOf(
        TestAssets.xorAsset(), TestAssets.valAsset(), TestAssets.pswapAsset()
    )
}
