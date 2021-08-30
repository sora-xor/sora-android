/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.confirmation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetBalance
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.TextFormatter
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.times
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
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
    fun setUp() = runBlockingTest {
        viewModel = TransactionConfirmationViewModel(
            walletInteractor,
            ethInteractor,
            router,
            progress,
            resourceManager,
            numbersFormatter,
            textFormatter,
            BigDecimal.ONE,
            BigDecimal.TEN,
            BigDecimal.ZERO,
            BigDecimal.ONE,
            "id2",
            "peerFullName",
            "peerId",
            TransferType.VAL_TRANSFER,
            "",
            clip,
        )
    }

    @Test
    fun `test init`() {
        viewModel.balanceFormattedLiveData.observeForever {
            assertEquals("0.34", it)
        }
        viewModel.inputTokenLastNameLiveData.observeForever {
            assertEquals("pswap", it)
        }
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
    fun `next click`() = runBlockingTest {
        viewModel.nextClicked()
        verify(router).returnToWalletFragment()
    }

    private fun assetList() = listOf(
        Asset(
            Token("token_id", "token name", "token symbol", 18, true, 0),
            true,
            true,
            1,
            AssetBalance(
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE
            ),
        ),
        Asset(
            Token("token2_id", "token2 name", "token2 symbol", 18, true, 0),
            true,
            true,
            2,
            AssetBalance(
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE
            )
        )
    )
}
