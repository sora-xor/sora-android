/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.polkaswap.liquidity.remove

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_data.PolkaswapTestData.NETWORK_FEE
import jp.co.soramitsu.test_data.PolkaswapTestData.POOL_DATA
import jp.co.soramitsu.test_data.PolkaswapTestData.TEST_ASSET
import jp.co.soramitsu.test_data.PolkaswapTestData.XOR_ASSET
import jp.co.soramitsu.test_data.PolkaswapTestData.XOR_ASSET_ZERO_BALANCE
import jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.liquidityremove.LiquidityRemoveViewModel
import jp.co.soramitsu.test_data.TestTokens
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
    private lateinit var assetsInteractor: AssetsInteractor

    @Mock
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var poolsInteractor: PoolsInteractor

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var assetsRouter: AssetsRouter

    @Mock
    private lateinit var router: WalletRouter

    @Mock
    private lateinit var mainRouter: MainRouter

    private lateinit var viewModel: LiquidityRemoveViewModel

    private val poolShareAfterTxText = "Pool share"
    private val sbApyText = "SB APY"
    private val networkFeeText = "network fee"

    private fun setUpViewModel() {
        viewModel = LiquidityRemoveViewModel(
            assetsInteractor,
            assetsRouter,
            router,
            mainRouter,
            walletInteractor,
            poolsInteractor,
            NumbersFormatter(),
            resourceManager,
            TestTokens.xorToken.id,
            TestTokens.valToken.id
        )
    }

    @Before
    fun setUp() = runTest {
        given(resourceManager.getString(R.string.xor)).willReturn("XOR")
        given(resourceManager.getString(R.string.common_enter_amount)).willReturn("Enter amount")
        given(resourceManager.getString(R.string.common_confirm)).willReturn("Confirm")
        given(resourceManager.getString(R.string.pool_button_remove)).willReturn("Remove")
        given(resourceManager.getString(R.string.polkaswap_insufficient_balance)).willReturn("Insufficient %s balance")
        given(assetsInteractor.subscribeAssetsActiveOfCurAccount()).willReturn(
            flowOf(
                listOf(
                    XOR_ASSET,
                    TEST_ASSET
                )
            )
        )

        given(
            poolsInteractor.fetchRemoveLiquidityNetworkFee(
                XOR_ASSET.token,
                TEST_ASSET.token
            )
        ).willReturn(NETWORK_FEE)
        given(
            poolsInteractor.subscribePoolCache(
                XOR_ASSET.token.id,
                TEST_ASSET.token.id
            )
        ).willReturn(flowOf(POOL_DATA))
        given(walletInteractor.getFeeToken()).willReturn(TestTokens.xorToken)

    }

    @Test
    fun `init viewModel EXPECT initial button state text`() = runTest {
        setUpViewModel()
        advanceUntilIdle()
        assertEquals("Enter amount", viewModel.removeState.btnState.text)
    }

    @Test
    fun `init viewModel EXPECT xor token setted`() = runTest {
        setUpViewModel()
        advanceUntilIdle()
        assertEquals("XOR", viewModel.removeState.assetState1?.token?.symbol)
    }

    @Test
    fun `slippage tolerance changed EXPECT update slippageTolerance`() = runTest {
        setUpViewModel()
        advanceUntilIdle()
        viewModel.slippageChanged(1.0)
        assertEquals(1.0, viewModel.removeState.slippage, 0.1)
    }

    @Test
    fun `amount from changed EXPECT update toAssetAmount`() = runTest {
        setUpViewModel()
        advanceUntilIdle()
        viewModel.onAmount1Change(BigDecimal(0.5))
        advanceUntilIdle()

        assertEquals(BigDecimal(0.5), viewModel.removeState.assetState1?.amount)
        assertEquals(BigDecimal(0.5), viewModel.removeState.assetState2?.amount)
        assertEquals("Remove", viewModel.removeState.btnState.text)
        assertTrue(viewModel.removeState.btnState.enabled)
    }

    @Test
    fun `amount to changed EXPECT update fromAssetAmount`() = runTest {
        val amount = BigDecimal.valueOf(0.35)

        setUpViewModel()
        advanceUntilIdle()
        viewModel.onAmount2Change(BigDecimal("0.35"))
        advanceUntilIdle()

        assertEquals(BigDecimal("0.35"), viewModel.removeState.assetState1?.amount)
        assertEquals(BigDecimal("0.35"), viewModel.removeState.assetState2?.amount)
        assertTrue(viewModel.removeState.btnState.enabled)
    }

    @Test
    fun `amount from changed EXPECT button disabled and insufficient balance text added`() =
        runTest {
            val amount = BigDecimal.valueOf(0.5)

            given(assetsInteractor.subscribeAssetsActiveOfCurAccount()).willReturn(
                flowOf(
                    listOf(
                        XOR_ASSET_ZERO_BALANCE,
                        TEST_ASSET
                    )
                )
            )
            setUpViewModel()
            advanceUntilIdle()
            viewModel.onAmount1Change(BigDecimal("0.5"))
            advanceUntilIdle()

            assertEquals(
                "Insufficient ${XOR_ASSET_ZERO_BALANCE.token.symbol} balance",
                viewModel.removeState.btnState.text,
            )
            assertFalse(viewModel.removeState.btnState.enabled)
        }
}
