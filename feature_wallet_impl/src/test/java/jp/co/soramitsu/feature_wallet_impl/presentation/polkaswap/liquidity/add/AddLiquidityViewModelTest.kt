/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.add

import android.text.SpannableString
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.decimalPartSized
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PoolsManager
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetListMode
import jp.co.soramitsu.feature_wallet_api.domain.model.LiquidityData
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaswapTestData.LIQUIDITY_DATA
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaswapTestData.LIQUIDITY_DETAILS
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaswapTestData.TEST_ASSET
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaswapTestData.XOR_ASSET
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.getOrAwaitValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
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
import org.mockito.kotlin.any
import java.math.BigDecimal

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
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var router: WalletRouter

    private lateinit var viewModel: AddLiquidityViewModel

    private fun setUpViewModel() {
        viewModel = AddLiquidityViewModel(
            router,
            walletInteractor,
            polkaswapInteractor,
            poolsManager,
            NumbersFormatter(),
            resourceManager
        )
    }

    @Before
    fun setUp() = runTest {
        mockkObject(FirebaseWrapper)
        given(walletInteractor.getAssetOrThrow(XOR_ASSET.token.id)).willReturn(XOR_ASSET)
        mockkStatic(String::decimalPartSized)
        every { any<String>().decimalPartSized(any(), any()) } returns SpannableString("")
        mockkStatic(SpannableString::valueOf)
        every { SpannableString.valueOf(any()) } returns SpannableString("")
        given(polkaswapInteractor.subscribeReservesCache(TEST_ASSET.token.id))
            .willReturn(flowOf(LIQUIDITY_DATA))
        given(
            polkaswapInteractor.calcLiquidityDetails(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        ).willReturn(LIQUIDITY_DETAILS)
        given(walletInteractor.subscribeActiveAssetsOfCurAccount()).willReturn(
            flowOf(
                listOf(
                    XOR_ASSET,
                    TEST_ASSET
                )
            )
        )

        given(resourceManager.getString(R.string.choose_tokens)).willReturn("Choose tokens")
        given(resourceManager.getString(R.string.common_enter_amount)).willReturn("Enter amount")
//        given(resourceManager.getString(R.string.common_supply)).willReturn("Supply")
        given(resourceManager.getString(R.string.common_insufficient_balance)).willReturn("Insufficient balance")
        given(resourceManager.getString(R.string.pool_share_title)).willReturn("Share of pool")
        given(resourceManager.getString(R.string.polkaswap_your_position)).willReturn("Your position")
        given(resourceManager.getString(R.string.polkaswap_info_prices_and_fees)).willReturn("Prices and Fee")
        given(resourceManager.getString(R.string.polkaswap_network_fee)).willReturn("Network fee")
//        given(resourceManager.getString(R.string.polkaswap_network_fee_info)).willReturn("Network fee is used")
    }

    @After
    fun tearDown() {
        io.mockk.verify(exactly = 0) { FirebaseWrapper.recordException(any()) }
    }

    @Test
    fun `init viewModel EXPECT initial button state text`() {
        setUpViewModel()

        assertEquals("Choose tokens", viewModel.buttonState.value.text)
    }

    @Test
    fun `choose token clicked EXPECT navigate to asset list screen`() = runTest {
        setUpViewModel()

        viewModel.onChooseToken()

        verify(router).showSelectToken(
            AssetListMode.SELECT_FOR_LIQUIDITY,
            SubstrateOptionsProvider.feeAssetId
        )
    }

    @Test
    fun `slippage tolerance changed EXPECT update slippageTolerance`() = runTest {
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
    fun `amount from changed EXPECT update fromAssetAmount`() = runTest {
        val amount = BigDecimal.valueOf(110.34)
        given(walletInteractor.getActiveAssets()).willReturn(emptyList())
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
    fun `amount to changed EXPECT update toAssetAmount`() = runTest {
        val amount = BigDecimal.valueOf(110.34)
        given(walletInteractor.getActiveAssets()).willReturn(emptyList())
        given(polkaswapInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(polkaswapInteractor.isPairPresentedInNetwork(TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel()

        viewModel.setTokensFromArgs(tokenFrom = XOR_ASSET.token, tokenTo = TEST_ASSET.token)
        viewModel.toAmountChanged(amount)

        assertEquals("110.34", viewModel.toAssetAmount.value)
    }

    @Test
    fun `set tokens from args EXPECT update visible assets`() = runTest {
        given(walletInteractor.getActiveAssets()).willReturn(emptyList())
        given(polkaswapInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(polkaswapInteractor.isPairPresentedInNetwork(TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel()
        advanceUntilIdle()

        viewModel.setTokensFromArgs(tokenFrom = XOR_ASSET.token, tokenTo = TEST_ASSET.token)
        advanceUntilIdle()

        verify(walletInteractor).getActiveAssets()
    }

    @Test
    fun `set tokens from args EXPECT update toToken`() = runTest {
        given(walletInteractor.getActiveAssets()).willReturn(listOf(XOR_ASSET, TEST_ASSET))
        given(polkaswapInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(polkaswapInteractor.isPairPresentedInNetwork(TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel()

        viewModel.setTokensFromArgs(tokenFrom = XOR_ASSET.token, tokenTo = TEST_ASSET.token)

        assertEquals(TEST_ASSET.token, viewModel.toToken.value)
    }

    @Test
    fun `set tokens from args EXPECT update fromToken`() = runTest {
        given(walletInteractor.getActiveAssets()).willReturn(listOf(XOR_ASSET, TEST_ASSET))
        given(polkaswapInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(polkaswapInteractor.isPairPresentedInNetwork(TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel()

        viewModel.setTokensFromArgs(tokenFrom = XOR_ASSET.token, tokenTo = TEST_ASSET.token)

        assertEquals(XOR_ASSET.token, viewModel.fromToken.value)
    }

    @Test
    fun `set tokens from args EXPECT subscribe reserves cache`() = runTest {
        given(walletInteractor.getActiveAssets()).willReturn(listOf(XOR_ASSET, TEST_ASSET))
        given(polkaswapInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(polkaswapInteractor.isPairPresentedInNetwork(TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel()

        viewModel.setTokensFromArgs(tokenFrom = XOR_ASSET.token, tokenTo = TEST_ASSET.token)

        verify(polkaswapInteractor).subscribeReservesCache(TEST_ASSET.token.id)
    }

    @Test
    fun `option selected and INPUT desired EXPECT update amountFrom value`() = runTest {
        given(walletInteractor.getActiveAssets()).willReturn(listOf(XOR_ASSET, TEST_ASSET))
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
        advanceUntilIdle()

        viewModel.setTokensFromArgs(tokenFrom = XOR_ASSET.token, tokenTo = TEST_ASSET.token)
        advanceUntilIdle()
        viewModel.fromAmountFocused()
        advanceUntilIdle()
        viewModel.optionSelected(50)
        advanceUntilIdle()

        assertEquals("0.49965", viewModel.fromAssetAmount.value)
    }

    @Test
    fun `option selected and OUTPUT desired  EXPECT update amountTo value`() = runTest {
        given(walletInteractor.getActiveAssets()).willReturn(listOf(XOR_ASSET, TEST_ASSET))
        given(polkaswapInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(polkaswapInteractor.isPairPresentedInNetwork(TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel()
        advanceUntilIdle()

        viewModel.setTokensFromArgs(tokenFrom = XOR_ASSET.token, tokenTo = TEST_ASSET.token)
        advanceUntilIdle()
        viewModel.toAmountFocused()
        advanceUntilIdle()
        viewModel.optionSelected(50)
        advanceUntilIdle()

        assertEquals("0.5", viewModel.toAssetAmount.value)
    }

    @Test
    fun `pair is not exists EXPECT pairNotExists is true`() = runTest {
        given(walletInteractor.getActiveAssets()).willReturn(listOf(XOR_ASSET, TEST_ASSET))
        given(polkaswapInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(false))
        given(polkaswapInteractor.isPairPresentedInNetwork(TEST_ASSET.token.id))
            .willReturn(flowOf(false))
        setUpViewModel()
        advanceUntilIdle()

        given(
            polkaswapInteractor.getLiquidityData(
                XOR_ASSET.token,
                TEST_ASSET.token,
                enabled = false,
                presented = false
            )
        )
            .willReturn(LiquidityData())
        advanceUntilIdle()
        given(polkaswapInteractor.subscribeReservesCache(TEST_ASSET.token.id))
            .willReturn(flowOf(null))
        advanceUntilIdle()
        viewModel.setTokensFromArgs(tokenFrom = XOR_ASSET.token, tokenTo = TEST_ASSET.token)
        advanceUntilIdle()

        val pair = viewModel.pairNotExists.getOrAwaitValue()
        assertTrue(pair)
    }

    @Test
    fun `pair is exists EXPECT pairNotExists is false`() = runTest {
        given(walletInteractor.getActiveAssets()).willReturn(listOf(XOR_ASSET, TEST_ASSET))
        given(polkaswapInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(polkaswapInteractor.isPairPresentedInNetwork(TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel()

        viewModel.setTokensFromArgs(tokenFrom = XOR_ASSET.token, tokenTo = TEST_ASSET.token)

        assertFalse(viewModel.pairNotExists.value!!)
    }
}
