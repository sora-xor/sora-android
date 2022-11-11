/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.add.confirm

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.mockkObject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PoolsManager
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaswapTestData
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaswapTestData.LIQUIDITY_DATA
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaswapTestData.STRATEGIC_BONUS_APY
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaswapTestData.TEST_ASSET
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaswapTestData.XOR_ASSET
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.model.ButtonState
import jp.co.soramitsu.test_shared.MainCoroutineRule
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
class ConfirmAddLiquidityViewModelTest {

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

    private val viewModel by lazy {
        ConfirmAddLiquidityViewModel(
            router,
            walletInteractor,
            polkaswapInteractor,
            poolsManager,
            NumbersFormatter(),
            resourceManager,
            XOR_ASSET.token,
            TEST_ASSET.token,
            PolkaswapTestData.SLIPPAGE_TOLERANCE,
            PolkaswapTestData.LIQUIDITY_DETAILS
        )
    }

    @Before
    fun setUp() {
        mockkObject(FirebaseWrapper)
        given(resourceManager.getString(R.string.common_confirm)).willReturn("Confirm")
        given(resourceManager.getString(R.string.common_insufficient_balance)).willReturn("Insufficient balance")

        runTest {
            given(
                polkaswapInteractor.subscribeReservesCache(
                    XOR_ASSET.token.id,
                    TEST_ASSET.token.id
                )
            ).willReturn(
                flowOf(
                    LIQUIDITY_DATA
                )
            )
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
            ).willReturn(PolkaswapTestData.LIQUIDITY_DETAILS)
            given(
                polkaswapInteractor.isPairEnabled(
                    XOR_ASSET.token.id,
                    TEST_ASSET.token.id,
                )
            ).willReturn(
                flowOf(true)
            )

            given(
                polkaswapInteractor.isPairPresentedInNetwork(
                    XOR_ASSET.token.id,
                    TEST_ASSET.token.id,
                )
            ).willReturn(
                flowOf(true)
            )

            given(polkaswapInteractor.getPoolStrategicBonusAPY(TEST_ASSET.token.id))
                .willReturn(STRATEGIC_BONUS_APY)

            given(walletInteractor.subscribeActiveAssetsOfCurAccount()).willReturn(
                flowOf(
                    listOf(XOR_ASSET, TEST_ASSET)
                )
            )
        }
    }

    @After
    fun tearDown() {
        io.mockk.verify(exactly = 0) { FirebaseWrapper.recordException(any()) }
    }

    @Test
    fun `init viewModel EXPECT properties were set`() {
        assertEquals("0.34678%", viewModel.shareOfPool.value)
        assertEquals("1", viewModel.firstDeposit.value)
        assertEquals(ButtonState(text = "Confirm", enabled = true), viewModel.buttonState.value)
    }

    @Test
    fun `confirm clicked EXPECT observeAddLiquidity called`() = runTest {
        given(
            polkaswapInteractor.observeAddLiquidity(
                XOR_ASSET.token,
                TEST_ASSET.token,
                BigDecimal.ONE,
                BigDecimal.ONE,
                enabled = true,
                presented = true,
                slippageTolerance = PolkaswapTestData.SLIPPAGE_TOLERANCE
            )
        ).willReturn(true)

        viewModel.onConfirm()
        advanceUntilIdle()

        verify(polkaswapInteractor).observeAddLiquidity(
            XOR_ASSET.token,
            TEST_ASSET.token,
            BigDecimal.ONE,
            BigDecimal.ONE,
            enabled = true,
            presented = true,
            slippageTolerance = PolkaswapTestData.SLIPPAGE_TOLERANCE
        )
    }

    @Test
    fun `transaction succeeded EXPECT extrinsic event is true`() = runTest {
        given(
            polkaswapInteractor.observeAddLiquidity(
                XOR_ASSET.token,
                TEST_ASSET.token,
                BigDecimal.ONE,
                BigDecimal.ONE,
                enabled = true,
                presented = true,
                slippageTolerance = PolkaswapTestData.SLIPPAGE_TOLERANCE
            )
        ).willReturn(true)

        viewModel.onConfirm()
        advanceUntilIdle()

        assertTrue(viewModel.extrinsicEvent.value!!)
    }

    @Test
    fun `transaction failed EXPECT extrinsic event is false`() = runTest {
        given(
            polkaswapInteractor.observeAddLiquidity(
                XOR_ASSET.token,
                TEST_ASSET.token,
                BigDecimal.ONE,
                BigDecimal.ONE,
                enabled = true,
                presented = true,
                slippageTolerance = PolkaswapTestData.SLIPPAGE_TOLERANCE
            )
        ).willReturn(false)

        viewModel.onConfirm()
        advanceUntilIdle()

        assertFalse(viewModel.extrinsicEvent.value!!)
    }

    @Test
    fun `confirm clicked EXPECT return to polkaswap`() = runTest {
        given(
            polkaswapInteractor.observeAddLiquidity(
                XOR_ASSET.token,
                TEST_ASSET.token,
                BigDecimal.ONE,
                BigDecimal.ONE,
                enabled = true,
                presented = true,
                slippageTolerance = PolkaswapTestData.SLIPPAGE_TOLERANCE
            )
        ).willReturn(true)

        viewModel.onConfirm()
        advanceUntilIdle()

        verify(router).returnToPolkaswap()
    }
}