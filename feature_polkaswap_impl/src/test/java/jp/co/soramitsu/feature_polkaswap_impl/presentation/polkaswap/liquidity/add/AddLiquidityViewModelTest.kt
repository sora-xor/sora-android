/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.polkaswap.liquidity.add

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.PoolDex
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.equalTo
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.common_wallet.domain.model.LiquidityData
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.liquidityadd.LiquidityAddViewModel
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_data.PolkaswapTestData.LIQUIDITY_DATA
import jp.co.soramitsu.test_data.PolkaswapTestData.LIQUIDITY_DETAILS
import jp.co.soramitsu.test_data.PolkaswapTestData.TEST_ASSET
import jp.co.soramitsu.test_data.PolkaswapTestData.XOR_ASSET
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito
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

    private val mockedUri = Mockito.mock(Uri::class.java)

    @Mock
    private lateinit var mainRouter: MainRouter

    private lateinit var viewModel: LiquidityAddViewModel

    private fun setUpViewModel(secondTokenId: String?) {
        viewModel = LiquidityAddViewModel(
                assetsInteractor,
                assetsRouter,
                router,
                mainRouter,
                walletInteractor,
                poolsInteractor,
                NumbersFormatter(),
                resourceManager,
                TestTokens.xorToken.id,
                secondTokenId,
        )
    }

    @Before
    fun setUp() = runTest {
        mockkObject(FirebaseWrapper)
//        mockkStatic(SpannableString::valueOf)
//        every { SpannableString.valueOf(any()) } returns SpannableString("")
        mockkStatic(Uri::parse)
        every { Uri.parse(any()) } returns mockedUri
        mockkStatic(Token::iconUri)
        every { TestTokens.xorToken.iconUri() } returns mockedUri
        every { TestTokens.valToken.iconUri() } returns mockedUri
        every { TestTokens.pswapToken.iconUri() } returns mockedUri
        every { TestTokens.xstusdToken.iconUri() } returns mockedUri
        every { TestTokens.xstToken.iconUri() } returns mockedUri
        given(poolsInteractor.subscribeReservesCache(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(LIQUIDITY_DATA))
        given(
            poolsInteractor.calcLiquidityDetails(
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
        given(poolsInteractor.getPoolDexList()).willReturn(
            listOf(
                PoolDex(
                    0,
                    TestTokens.xorToken.id,
                    TestTokens.xorToken.symbol
                ),
                PoolDex(1,
                    TestTokens.xstusdToken.id,
                    TestTokens.xstusdToken.symbol,
                )
            )
        )
        given(assetsInteractor.subscribeAssetsActiveOfCurAccount()).willReturn(
            flowOf(
                listOf(
                    XOR_ASSET,
                    TEST_ASSET
                )
            )
        )
        given(walletInteractor.getFeeToken()).willReturn(TestTokens.xorToken)

        given(resourceManager.getString(R.string.common_supply)).willReturn("Supply")
        given(resourceManager.getString(R.string.common_confirm)).willReturn("Confirm")
        given(resourceManager.getString(R.string.choose_tokens)).willReturn("Choose tokens")
        given(resourceManager.getString(R.string.common_enter_amount)).willReturn("Enter amount")
        given(resourceManager.getString(R.string.common_insufficient_balance)).willReturn("Insufficient balance")
    }

    @After
    fun tearDown() {
        io.mockk.verify(exactly = 0) { FirebaseWrapper.recordException(any()) }
    }

    @Test
    fun `init viewModel EXPECT initial button state text`() = runTest {
        setUpViewModel(null)
        advanceUntilIdle()
        assertEquals("Choose tokens", viewModel.addState.btnState.text)
    }

    @Test
    fun `init viewModel with both tokens EXPECT initial button state text`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(TestTokens.valToken.id)
        advanceUntilIdle()
        assertEquals("Enter amount", viewModel.addState.btnState.text)
    }

    @Test
    fun `choose token clicked EXPECT navigate to asset list screen`() = runTest {
        setUpViewModel(null)
        advanceUntilIdle()
        //viewModel.setTokensFromArgs(XOR_ASSET.token, null)
        viewModel.onToken2Click()
        advanceUntilIdle()

//        verify(router).showSelectToken(
//            AssetListMode.SELECT_FOR_LIQUIDITY,
//            SubstrateOptionsProvider.feeAssetId
//        )
    }

    @Test
    fun `slippage tolerance changed EXPECT update slippageTolerance`() = runTest {
        setUpViewModel(null)
        advanceUntilIdle()
        viewModel.slippageChanged(1.0)
        advanceUntilIdle()
        assertEquals(1.0, viewModel.addState.slippage, 0.01)
    }

    @Test
    fun `amount from changed EXPECT update fromAssetAmount`() = runTest {
        setUpViewModel(null)
        advanceUntilIdle()
        viewModel.onAmount1Change(BigDecimal("110.34"))
        advanceUntilIdle()
        assertEquals(BigDecimal("110.34"), viewModel.addState.assetState1?.amount)
    }

    @Test
    fun `amount to changed EXPECT update toAssetAmount`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(TEST_ASSET.token.id)
        advanceUntilIdle()
        viewModel.onAmount2Change(BigDecimal("110.34"))
        advanceUntilIdle()
        assertEquals(BigDecimal("110.34"), viewModel.addState.assetState2?.amount)
    }

    @Test
    fun `set tokens from args EXPECT update visible assets`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(TEST_ASSET.token.id)
        advanceUntilIdle()

        verify(poolsInteractor).isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id)
        verify(poolsInteractor).isPairPresentedInNetwork(XOR_ASSET.token.id, TEST_ASSET.token.id)
        verify(poolsInteractor).subscribeReservesCache(XOR_ASSET.token.id, TEST_ASSET.token.id)
    }

    @Test
    fun `set tokens from args EXPECT update toToken`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(TEST_ASSET.token.id)
        advanceUntilIdle()

        assertEquals(TEST_ASSET.token, viewModel.addState.assetState2?.token)
    }

    @Test
    fun `set tokens from args EXPECT update fromToken`() = runTest {
        setUpViewModel(null)
        advanceUntilIdle()

        assertEquals(XOR_ASSET.token, viewModel.addState.assetState1?.token)
    }

    @Test
    fun `option selected and INPUT desired EXPECT update amountFrom value`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(TEST_ASSET.token.id)
        advanceUntilIdle()
        viewModel.onAmount1Focused()
        advanceUntilIdle()
        viewModel.optionSelected(50)
        advanceUntilIdle()

        assertTrue(viewModel.addState.assetState1?.amount?.equalTo(BigDecimal(0.5)) == true)
    }

    @Test
    fun `option selected and OUTPUT desired  EXPECT update amountTo value`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(TEST_ASSET.token.id)
        advanceUntilIdle()
        viewModel.onAmount2Focused()
        advanceUntilIdle()
        viewModel.optionSelected(50)
        advanceUntilIdle()

        assertTrue(viewModel.addState.assetState2?.amount?.equalTo(BigDecimal(0.5)) == true)
    }

    @Test
    fun `pair is not exists EXPECT pairNotExists is true`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(false))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(false))
        given(poolsInteractor.subscribeReservesCache(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(null))
        given(
            poolsInteractor.getLiquidityData(
                XOR_ASSET.token,
                TEST_ASSET.token,
                enabled = false,
                presented = false
            )
        )
            .willReturn(LiquidityData())
        setUpViewModel(TEST_ASSET.token.id)
        advanceUntilIdle()

        val pair = viewModel.addState.pairNotExist
        assertTrue(pair == true)
    }

    @Test
    fun `pair is exists EXPECT pairNotExists is false`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(TEST_ASSET.token.id)
        advanceUntilIdle()

        val pair = viewModel.addState.pairNotExist
        assertTrue(pair == false)
    }

    @Test
    fun `assets balance are zero EXPECT clean up liquidity details`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, TEST_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(TEST_ASSET.token.id)
        advanceUntilIdle()

        viewModel.onAmount1Change(BigDecimal("110.34"))
        viewModel.onAmount2Change(BigDecimal("110.34"))
        advanceUntilIdle()
        assertEquals("1", viewModel.addState.prices.pair1Value)
    }
}
