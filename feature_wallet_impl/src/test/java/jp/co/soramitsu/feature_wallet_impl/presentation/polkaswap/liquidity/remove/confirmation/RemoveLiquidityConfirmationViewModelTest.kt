/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.remove.confirmation

import android.text.SpannableString
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockkStatic
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.decimalPartSized
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaswapTestData.NETWORK_FEE
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaswapTestData.POOL_DATA
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaswapTestData.TEST_ASSET
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaswapTestData.XOR_ASSET
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

@FlowPreview
@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class RemoveLiquidityConfirmationViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var polkaswapInteractor: PolkaswapInteractor

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var router: WalletRouter

    private lateinit var viewModel: RemoveLiquidityConfirmationViewModel

    private val amount = BigDecimal.ONE
    private val amountString = "1.00"
    private val slippage = 0.5f
    private val descriptionText = "string 0.5 string"
    private val poolShareAfterTxText = "Pool share"
    private val sbApyText = "SB APY"
    private val networkFeeText = "network fee"

    @Before
    fun setUp() = runTest {
        given(
            polkaswapInteractor.fetchRemoveLiquidityNetworkFee(
                XOR_ASSET.token,
                TEST_ASSET.token
            )
        ).willReturn(NETWORK_FEE)
        given(walletInteractor.subscribeActiveAssetsOfCurAccount()).willReturn(
            flowOf(
                listOf(
                    XOR_ASSET,
                    TEST_ASSET
                )
            )
        )
        given(polkaswapInteractor.subscribePoolsCache()).willReturn(flowOf(listOf(POOL_DATA)))
        given(resourceManager.getString(R.string.remove_pool_confirmation_description)).willReturn("string %s string")
        given(resourceManager.getString(R.string.pool_share_title)).willReturn(poolShareAfterTxText)
        given(resourceManager.getString(R.string.polkaswap_sbapy)).willReturn(sbApyText)
        given(resourceManager.getString(R.string.polkaswap_network_fee)).willReturn(networkFeeText)
        given(resourceManager.getString(R.string.polkaswap_insufficient_balance)).willReturn("string %s string")
        given(resourceManager.getString(R.string.common_confirm)).willReturn("confirm")

        mockkStatic("jp.co.soramitsu.common.util.ext.StringExtKt")
        every { any<String>().decimalPartSized(ticker = any()) } returns SpannableString("sized")
        every { any<String>().decimalPartSized() } returns SpannableString("sized")

        viewModel = RemoveLiquidityConfirmationViewModel(
            router, walletInteractor, polkaswapInteractor, NumbersFormatter(), resourceManager
        )
    }

    @Test
    fun `set bundle args called EXPECT fromToken, fromAmount, toToken, toAmount, description`() =
        runTest {
            viewModel.setBundleArgs(
                XOR_ASSET.token,
                amount,
                TEST_ASSET.token,
                amount,
                slippage,
                10.0
            )

            assertEquals(viewModel.fromToken.value, XOR_ASSET.token)
            assertEquals(viewModel.toToken.value, TEST_ASSET.token)
            assertEquals("1", viewModel.fromAssetAmount.value)
            assertEquals("1", viewModel.toAssetAmount.value)
            assertEquals(viewModel.descriptionTextLiveData.value, descriptionText)
        }

    @Test
    fun `next button clicked EXPECT polkaswapInteractor removeLiquidity is called`() = runTest {
        given(
            polkaswapInteractor.removeLiquidity(
                XOR_ASSET.token,
                TEST_ASSET.token,
                1.0.toBigDecimal(),
                0.995.toBigDecimal(),
                0.995.toBigDecimal(),
                NETWORK_FEE
            )
        ).willReturn(true)
        viewModel.setBundleArgs(XOR_ASSET.token, amount, TEST_ASSET.token, amount, slippage, 10.0)
        advanceUntilIdle()
        viewModel.nextBtnClicked()
        advanceUntilIdle()
        delay(1000)
        verify(polkaswapInteractor).removeLiquidity(
            XOR_ASSET.token,
            TEST_ASSET.token,
            1.0.toBigDecimal(),
            0.995.toBigDecimal(),
            0.995.toBigDecimal(),
            NETWORK_FEE
        )
        verify(router).returnToPolkaswap()
    }
}
