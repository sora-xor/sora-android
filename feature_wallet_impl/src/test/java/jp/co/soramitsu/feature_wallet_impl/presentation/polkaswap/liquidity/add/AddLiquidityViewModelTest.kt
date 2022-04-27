/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.add

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetListMode
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaswapTestData.TEST_ASSET
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaswapTestData.TOKEN
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaswapTestData.XOR_ASSET
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runBlockingTest
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
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PoolsManager
import jp.co.soramitsu.feature_wallet_api.domain.model.LiquidityData
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaswapTestData.LIQUIDITY_DATA
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaswapTestData.POOL_DATA
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

@FlowPreview
@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class AddLiquidityViewModelTest {

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
    private lateinit var poolsManager: PoolsManager

    @Mock
    private lateinit var numbersFormatter: NumbersFormatter

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var router: WalletRouter

    private lateinit var viewModel: AddLiquidityViewModel

    private fun setUpViewModel() {
        viewModel = AddLiquidityViewModel(
            router, walletInteractor, polkaswapInteractor, poolsManager, numbersFormatter, resourceManager
        )
    }

    @Before
    fun setUp() {
        given(walletInteractor.subscribeVisibleAssetsOfCurAccount()).willReturn(
            flowOf(
                listOf(
                    XOR_ASSET,
                    TEST_ASSET
                )
            )
        )

        given(resourceManager.getString(R.string.choose_tokens)).willReturn("Choose tokens")
    }

    @Test
    fun `init viewModel EXPECT initial button state text`() {
        setUpViewModel()

        assertEquals("Choose tokens", viewModel.buttonState.value.text)
    }

    @Test
    fun `choose token clicked EXPECT navigate to asset list screen`() = runBlockingTest {
        setUpViewModel()

        viewModel.onChooseToken()

        verify(router).showSelectToken(
            AssetListMode.SELECT_FOR_LIQUIDITY,
            OptionsProvider.feeAssetId
        )
    }

    @Test
    fun `slippage tolerance changed EXPECT update slippageTolerance`() = runBlockingTest {
        setUpViewModel()

        viewModel.slippageChanged(1.0f)

        assertEquals(1.0f, viewModel.slippageTolerance.value)
    }

    @Test
    fun `slippage tolerance clicked EXPECT showSlippageToleranceBottomSheet event`() {
        setUpViewModel()

        viewModel.slippageChanged(1.0f)
        viewModel.slippageToleranceClicked()

        assertEquals(1.0f, viewModel.showSlippageToleranceBottomSheet.value)
    }

    @Test
    fun `amount from changed EXPECT update fromAssetAmount`() = runBlockingTest {
        val amount = BigDecimal.valueOf(110.34)
        given(numbersFormatter.formatBigDecimal(amount, TOKEN.precision)).willReturn("110.34")
        given(walletInteractor.getVisibleAssets()).willReturn(emptyList())
        given(polkaswapInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(polkaswapInteractor.isPairPresentedInNetwork(TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel()

        viewModel.setTokensFromArgs(tokenFrom = XOR_ASSET.token, tokenTo = TEST_ASSET.token)
        viewModel.fromAmountChanged(amount)

        assertEquals("110.34", viewModel.fromAssetAmount.value)
    }

    @Test
    fun `amount to changed EXPECT update toAssetAmount`() = runBlockingTest {
        val amount = BigDecimal.valueOf(110.34)
        given(numbersFormatter.formatBigDecimal(amount, TOKEN.precision)).willReturn("110.34")
        given(walletInteractor.getVisibleAssets()).willReturn(emptyList())
        given(polkaswapInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(polkaswapInteractor.isPairPresentedInNetwork(TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(
            polkaswapInteractor.fetchAddLiquidityNetworkFee(
                XOR_ASSET.token,
                TEST_ASSET.token,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                pairEnabled = true,
                pairPresented = true,
                slippageTolerance = 0.5f
            )
        ).willReturn(0.0007.toBigDecimal())
        setUpViewModel()

        viewModel.setTokensFromArgs(tokenFrom = XOR_ASSET.token, tokenTo = TEST_ASSET.token)
        viewModel.toAmountChanged(amount)

        assertEquals("110.34", viewModel.toAssetAmount.value)
    }

    @Test
    fun `set tokens from args EXPECT update visible assets`() = runBlockingTest {
        given(walletInteractor.getVisibleAssets()).willReturn(emptyList())
        given(polkaswapInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(polkaswapInteractor.isPairPresentedInNetwork(TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel()

        viewModel.setTokensFromArgs(tokenFrom = XOR_ASSET.token, tokenTo = TEST_ASSET.token)

        verify(walletInteractor).getVisibleAssets()
    }

    @Test
    fun `set tokens from args EXPECT update toToken`() = runBlockingTest {
        given(walletInteractor.getVisibleAssets()).willReturn(listOf(XOR_ASSET, TEST_ASSET))
        given(polkaswapInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(polkaswapInteractor.isPairPresentedInNetwork(TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel()

        viewModel.setTokensFromArgs(tokenFrom = XOR_ASSET.token, tokenTo = TEST_ASSET.token)

        assertEquals(TEST_ASSET.token, viewModel.toToken.value)
    }

    @Test
    fun `set tokens from args EXPECT update fromToken`() = runBlockingTest {
        given(walletInteractor.getVisibleAssets()).willReturn(listOf(XOR_ASSET, TEST_ASSET))
        given(polkaswapInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(polkaswapInteractor.isPairPresentedInNetwork(TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel()

        viewModel.setTokensFromArgs(tokenFrom = XOR_ASSET.token, tokenTo = TEST_ASSET.token)

        assertEquals(XOR_ASSET.token, viewModel.fromToken.value)
    }

    @Test
    fun `set tokens from args EXPECT subscribe reserves cache`() = runBlockingTest {
        given(walletInteractor.getVisibleAssets()).willReturn(listOf(XOR_ASSET, TEST_ASSET))
        given(polkaswapInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(polkaswapInteractor.isPairPresentedInNetwork(TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel()

        viewModel.setTokensFromArgs(tokenFrom = XOR_ASSET.token, tokenTo = TEST_ASSET.token)

        verify(polkaswapInteractor).subscribeReservesCache(TOKEN.id)
    }

    @Test
    fun `option selected and INPUT desired EXPECT update amountFrom value`() = runBlockingTest {
        given(walletInteractor.getVisibleAssets()).willReturn(listOf(XOR_ASSET, TEST_ASSET))
        given(polkaswapInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(polkaswapInteractor.isPairPresentedInNetwork(TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(
            polkaswapInteractor.fetchAddLiquidityNetworkFee(
                XOR_ASSET.token,
                TEST_ASSET.token,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                pairEnabled = true,
                pairPresented = true,
                slippageTolerance = 0.5f
            )
        ).willReturn(0.0007.toBigDecimal())
        given(
            numbersFormatter.formatBigDecimal(
                BigDecimal("0.49965000000000000000000"),
                XOR_ASSET.token.precision
            )
        )
            .willReturn("0.49965000000000000000000")
        setUpViewModel()

        viewModel.setTokensFromArgs(tokenFrom = XOR_ASSET.token, tokenTo = TEST_ASSET.token)
        viewModel.fromAmountFocused()
        viewModel.optionSelected(50)

        assertEquals("0.49965000000000000000000", viewModel.fromAssetAmount.value)
    }


    @Test
    fun `option selected and OUTPUT desired  EXPECT update amountTo value`() = runBlockingTest {
        given(walletInteractor.getVisibleAssets()).willReturn(listOf(XOR_ASSET, TEST_ASSET))
        given(polkaswapInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(polkaswapInteractor.isPairPresentedInNetwork(TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(
            numbersFormatter.formatBigDecimal(
                BigDecimal("0.500000000000000000"),
                TEST_ASSET.token.precision
            )
        )
            .willReturn("0.500000000000000000")
        setUpViewModel()

        viewModel.setTokensFromArgs(tokenFrom = XOR_ASSET.token, tokenTo = TEST_ASSET.token)
        viewModel.toAmountFocused()
        viewModel.optionSelected(50)

        assertEquals("0.500000000000000000", viewModel.toAssetAmount.value)
    }

    @Test
    fun `pair is not exists EXPECT pairNotExists is true`() = runBlockingTest {
        given(walletInteractor.getVisibleAssets()).willReturn(listOf(XOR_ASSET, TEST_ASSET))
        given(polkaswapInteractor.subscribeReservesCache(TEST_ASSET.token.id))
            .willReturn(flowOf(null))
        given(polkaswapInteractor.getLiquidityData(
            XOR_ASSET.token,
            TEST_ASSET.token,
            enabled = false,
            presented = false
        ))
            .willReturn(LiquidityData())
        given(polkaswapInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(false))
        given(polkaswapInteractor.isPairPresentedInNetwork(TEST_ASSET.token.id))
            .willReturn(flowOf(false))
        setUpViewModel()

        viewModel.setTokensFromArgs(tokenFrom = XOR_ASSET.token, tokenTo = TEST_ASSET.token)

        assertTrue(viewModel.pairNotExists.value!!)
    }

    @Test
    fun `pair is exists EXPECT pairNotExists is false`() = runBlockingTest {
        given(walletInteractor.getVisibleAssets()).willReturn(listOf(XOR_ASSET, TEST_ASSET))
        given(polkaswapInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(polkaswapInteractor.isPairPresentedInNetwork(TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel()

        viewModel.setTokensFromArgs(tokenFrom = XOR_ASSET.token, tokenTo = TEST_ASSET.token)

        assertFalse(viewModel.pairNotExists.value!!)
    }
}
