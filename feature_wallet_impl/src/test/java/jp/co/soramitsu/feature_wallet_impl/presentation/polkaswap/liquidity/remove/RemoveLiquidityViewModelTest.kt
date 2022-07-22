/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.remove

import android.text.SpannableString
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockkStatic
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.decimalPartSized
import jp.co.soramitsu.common.util.ext.divideBy
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaswapTestData.NETWORK_FEE
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaswapTestData.POOL_DATA
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaswapTestData.TEST_ASSET
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaswapTestData.XOR_ASSET
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaswapTestData.XOR_ASSET_ZERO_BALANCE
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
class RemoveLiquidityViewModelTest {

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
    private lateinit var numbersFormatter: NumbersFormatter

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var router: WalletRouter

    private lateinit var viewModel: RemoveLiquidityViewModel

    private val poolShareAfterTxText = "Pool share"
    private val sbApyText = "SB APY"
    private val networkFeeText = "network fee"

    private fun setUpViewModel() {
        viewModel = RemoveLiquidityViewModel(
            router, walletInteractor, polkaswapInteractor, numbersFormatter, resourceManager
        )
    }

    @Before
    fun setUp() = runTest {
        given(resourceManager.getString(R.string.common_enter_amount)).willReturn("Enter amount")
        given(resourceManager.getString(R.string.pool_button_remove)).willReturn("Remove")
        given(resourceManager.getString(R.string.polkaswap_insufficient_balance)).willReturn("Insufficient %s balance")
        given(resourceManager.getString(R.string.pool_share_title)).willReturn(poolShareAfterTxText)
        given(resourceManager.getString(R.string.polkaswap_sbapy)).willReturn(sbApyText)
        given(resourceManager.getString(R.string.polkaswap_network_fee)).willReturn(networkFeeText)
        given(walletInteractor.getFeeToken()).willReturn(XOR_ASSET.token)
        given(walletInteractor.subscribeActiveAssetsOfCurAccount()).willReturn(
            flowOf(
                listOf(
                    XOR_ASSET,
                    TEST_ASSET
                )
            )
        )

        given(
            polkaswapInteractor.fetchRemoveLiquidityNetworkFee(
                XOR_ASSET.token,
                TEST_ASSET.token
            )
        ).willReturn(NETWORK_FEE)
        given(polkaswapInteractor.subscribePoolsCache()).willReturn(flowOf(listOf(POOL_DATA)))


    }

    @Test
    fun `init viewModel EXPECT initial button state text`() {
        setUpViewModel()

        assertEquals("Enter amount", viewModel.buttonState.value.text)
    }

    @Test
    fun `init viewModel EXPECT xor token setted`() = runTest {
        setUpViewModel()
        advanceUntilIdle()
        assertEquals(viewModel.fromToken.value, XOR_ASSET.token)
    }

    @Test
    fun `slippage tolerance changed EXPECT update slippageTolerance`() = runTest {
        setUpViewModel()

        viewModel.slippageChanged(1.0f)

        assertEquals(1.0f, viewModel.slippageToleranceLiveData.value)
    }

    @Test
    fun `slippage tolerance clicked EXPECT showSlippageToleranceBottomSheet event`() {
        setUpViewModel()

        viewModel.slippageChanged(1.0f)
        viewModel.slippageToleranceClicked()

        assertEquals(1.0f, viewModel.showSlippageToleranceBottomSheet.value)
    }

    @Test
    fun `amount from changed EXPECT update toAssetAmount`() = runTest {
        val amount = BigDecimal.valueOf(0.5)
        given(
            numbersFormatter.formatBigDecimal(
                BigDecimal.ONE.divideBy(BigDecimal.ONE, 18),
                8
            )
        ).willReturn("1")

        given(
            numbersFormatter.formatBigDecimal(
                amount,
                XOR_ASSET.token.precision
            )
        ).willReturn("0.5")

        mockkStatic("jp.co.soramitsu.common.util.ext.StringExtKt")
        every { any<String>().decimalPartSized(ticker = any()) } returns SpannableString("sized")
        setUpViewModel()
        advanceUntilIdle()
        viewModel.setTokensFromArgs(tokenFrom = XOR_ASSET.token, tokenTo = TEST_ASSET.token)
        advanceUntilIdle()
        viewModel.fromAmountChanged(amount)
        advanceUntilIdle()

        assertEquals("0.5", viewModel.fromAssetAmount.value)
        assertEquals("0.5", viewModel.toAssetAmount.value)
        assertEquals("Remove", viewModel.buttonState.value.text)
        assertTrue(viewModel.buttonState.value.enabled)
    }

    @Test
    fun `amount to changed EXPECT update fromAssetAmount`() = runTest {
        val amount = BigDecimal.valueOf(0.35)
        given(
            numbersFormatter.formatBigDecimal(
                BigDecimal.ONE.divideBy(BigDecimal.ONE, 18),
                8
            )
        ).willReturn("1")

        given(
            numbersFormatter.formatBigDecimal(
                amount,
                XOR_ASSET.token.precision
            )
        ).willReturn("0.35")

        mockkStatic("jp.co.soramitsu.common.util.ext.StringExtKt")
        every { any<String>().decimalPartSized(ticker = any()) } returns SpannableString("sized")
        setUpViewModel()
        advanceUntilIdle()
        viewModel.setTokensFromArgs(tokenFrom = XOR_ASSET.token, tokenTo = TEST_ASSET.token)
        advanceUntilIdle()
        viewModel.toAmountChanged(amount)
        advanceUntilIdle()

        assertEquals("0.35", viewModel.fromAssetAmount.value)
        assertEquals("0.35", viewModel.toAssetAmount.value)
        assertTrue(viewModel.buttonState.value.enabled)
    }

    @Test
    fun `amount from changed EXPECT button disabled and insufficient balance text added`() =
        runTest {
            val amount = BigDecimal.valueOf(0.5)
            given(
                numbersFormatter.formatBigDecimal(
                    BigDecimal.ONE.divideBy(BigDecimal.ONE, 18),
                    8
                )
            ).willReturn("1")

            given(
                numbersFormatter.formatBigDecimal(
                    amount,
                    XOR_ASSET.token.precision
                )
            ).willReturn("0.5")

            given(walletInteractor.subscribeActiveAssetsOfCurAccount()).willReturn(
                flowOf(
                    listOf(
                        XOR_ASSET_ZERO_BALANCE,
                        TEST_ASSET
                    )
                )
            )
            mockkStatic("jp.co.soramitsu.common.util.ext.StringExtKt")
            every { any<String>().decimalPartSized(ticker = any()) } returns SpannableString("sized")
            setUpViewModel()
            advanceUntilIdle()
            viewModel.setTokensFromArgs(
                tokenFrom = XOR_ASSET_ZERO_BALANCE.token,
                tokenTo = TEST_ASSET.token
            )
            advanceUntilIdle()
            viewModel.fromAmountChanged(amount)
            advanceUntilIdle()

            assertEquals(
                "Insufficient ${XOR_ASSET_ZERO_BALANCE.token.symbol} balance",
                viewModel.buttonState.value.text
            )
            assertFalse(viewModel.buttonState.value.enabled)
        }

    @Test
    fun `setTokenFromArgsCalled EXPECT tokens and price setted`() {
        setUpViewModel()
        viewModel.setTokensFromArgs(XOR_ASSET.token, TEST_ASSET.token)

        assertEquals(viewModel.fromToken.value, XOR_ASSET.token)
        assertEquals(viewModel.toToken.value, TEST_ASSET.token)
        assertEquals(
            viewModel.priceDetailsTitles.value,
            "${XOR_ASSET.token.symbol}/${TEST_ASSET.token.symbol}" to "${TEST_ASSET.token.symbol}/${XOR_ASSET.token.symbol}"
        )
    }

    @Test
    fun `setTokenFromArgsCalled EXPECT subscribe to pooldata`() = runTest {
        setUpViewModel()
        advanceUntilIdle()
        viewModel.setTokensFromArgs(XOR_ASSET.token, TEST_ASSET.token)
        advanceUntilIdle()

        verify(polkaswapInteractor).subscribePoolsCache()
    }
}
