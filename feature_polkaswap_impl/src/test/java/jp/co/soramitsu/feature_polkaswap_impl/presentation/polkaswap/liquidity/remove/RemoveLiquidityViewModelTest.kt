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

package jp.co.soramitsu.feature_polkaswap_impl.presentation.polkaswap.liquidity.remove

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor
import jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.liquidityremove.LiquidityRemoveViewModel
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_data.PolkaswapTestData.COMMON_POOL_DATA
import jp.co.soramitsu.test_data.PolkaswapTestData.NETWORK_FEE
import jp.co.soramitsu.test_data.PolkaswapTestData.VAL_ASSET
import jp.co.soramitsu.test_data.PolkaswapTestData.XOR_ASSET
import jp.co.soramitsu.test_data.PolkaswapTestData.XOR_ASSET_ZERO_BALANCE
import jp.co.soramitsu.test_data.TestAssets
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
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
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
    private lateinit var demeterFarmingInteractor: jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor

    private lateinit var viewModel: LiquidityRemoveViewModel

    private val poolShareAfterTxText = "Pool share"
    private val sbApyText = "SB APY"
    private val networkFeeText = "network fee"

    private fun setUpViewModel(
        firstTokenId: String? = null,
        secondTokenId: String? = null
    ) {
        viewModel = LiquidityRemoveViewModel(
            assetsInteractor = assetsInteractor,
            assetsRouter = assetsRouter,
            router = router,
            walletInteractor = walletInteractor,
            poolsInteractor = poolsInteractor,
            numbersFormatter = NumbersFormatter(),
            resourceManager = resourceManager,
            token1Id = firstTokenId ?: TestTokens.xorToken.id,
            token2Id = secondTokenId ?: TestTokens.valToken.id,
            demeterFarmingInteractor = demeterFarmingInteractor,
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
                    VAL_ASSET
                )
            )
        )

        given(
            poolsInteractor.fetchRemoveLiquidityNetworkFee(
                XOR_ASSET.token,
                VAL_ASSET.token
            )
        ).willReturn(NETWORK_FEE)

        given(
            poolsInteractor.subscribePoolCacheOfCurAccount(
                XOR_ASSET.token.id,
                VAL_ASSET.token.id
            )
        ).willReturn(flowOf(COMMON_POOL_DATA))
        given(walletInteractor.getFeeToken()).willReturn(TestTokens.xorToken)

        given(
            assetsInteractor.isNotEnoughXorLeftAfterTransaction(
                networkFeeInXor = any(),
                xorChange = any(),
            )
        ).willReturn(false)
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
                        VAL_ASSET
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

    @Test
    fun `WHEN user enters amount including one XOR EXPECT transaction reminder is checked`() =
        runTest {
            setUpViewModel(
                firstTokenId = TestAssets.xorAsset().token.id,
                secondTokenId = TestAssets.valAsset().token.id
            )

            advanceUntilIdle()

            viewModel.onAmount1Change(BigDecimal.TEN)

            advanceUntilIdle()

            verify(
                mock = assetsInteractor,
                mode = times(1)
            ).isNotEnoughXorLeftAfterTransaction(
                xorChange = -BigDecimal.ONE, // is not TEN due to basePooled in POOL_DATA
                networkFeeInXor = NETWORK_FEE,
            )
        }
}
