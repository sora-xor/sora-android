/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sora_card_impl.presentation.get.card

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkObject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.Big100
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.sora.substrate.blockexplorer.BlockExplorerManager
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import jp.co.soramitsu.test_data.SoraCardTestData
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.getOrAwaitValue
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.given
import org.mockito.kotlin.verify

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class GetSoraCardViewModelTest {

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
    private lateinit var blockExplorerManager: BlockExplorerManager

    @Mock
    private lateinit var assetsRouter: AssetsRouter

    @Mock
    private lateinit var polkaswapRouter: PolkaswapRouter

    @Mock
    private lateinit var walletRouter: WalletRouter

    @Mock
    private lateinit var mainRouter: MainRouter

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var viewModel: GetSoraCardViewModel

    @Mock
    private lateinit var connectionManager: ConnectionManager

    @Before
    fun setUp() = runTest {
        given(assetsInteractor.subscribeAssetOfCurAccount(SubstrateOptionsProvider.feeAssetId))
            .willReturn(flowOf(TestAssets.xorAsset(balance = Big100)))
        given(blockExplorerManager.getXorPerEurRatio()).willReturn(2.34)
        given(walletInteractor.subscribeSoraCardInfo()).willReturn(flowOf(SoraCardTestData.SORA_CARD_INFO))
        given(connectionManager.connectionState).willReturn(flowOf(true))

        mockkObject(OptionsProvider)
        every { OptionsProvider.header } returns "test android client"

        viewModel = GetSoraCardViewModel(
            assetsInteractor,
            assetsRouter,
            walletInteractor,
            blockExplorerManager,
            walletRouter,
            mainRouter,
            polkaswapRouter,
            resourceManager,
            NumbersFormatter(),
            connectionManager,
        )
    }

    @Test
    fun `init EXPECT toolbar title`() {
        val s = viewModel.toolbarState.getOrAwaitValue()
        assertTrue(s.type is SoramitsuToolbarType.Small)
        assertEquals(R.string.get_sora_card_title, s.basic.title)
    }

    @Test
    fun `init EXPECT subscribe fee asset balance`() = runTest {
        advanceUntilIdle()

        verify(assetsInteractor).subscribeAssetOfCurAccount(SubstrateOptionsProvider.feeAssetId)
    }

    @Test
    fun `init EXPECT subscribe sora card info`() = runTest {
        advanceUntilIdle()

        verify(walletInteractor).subscribeSoraCardInfo()
    }

    @Test
    fun `enable sora card EXPECT set up launcher`() {
        viewModel.onEnableCard()

        assertEquals(
            SoraCardTestData.registrationLauncher,
            viewModel.launchSoraCardRegistration.value
        )
    }

    @Test
    fun `on already have card EXPECT set up launcher`() {
        viewModel.onAlreadyHaveCard()

        assertEquals(SoraCardTestData.signInLauncher, viewModel.launchSoraCardSignIn.value)
    }

    @Test
    fun `on get more xor EXPECT get more xor alert state is true`() {
        viewModel.onGetMoreXor()

        assertTrue(viewModel.state.value.getMorXorAlert)
    }

    @Test
    fun `on dismiss get more xor alert EXPECT get more xor alert state is false`() {
        viewModel.onDismissGetMoreXorAlert()

        assertFalse(viewModel.state.value.getMorXorAlert)
    }

    @Test
    fun `on buy crypto EXPECT navigate to buy crypto`() {
        viewModel.onBuyCrypto()

        verify(assetsRouter).showBuyCrypto()
    }

    @Test
    fun `on swap EXPECT navigate swap`() {
        viewModel.onSwap()

        verify(polkaswapRouter).showSwap(tokenToId = SubstrateOptionsProvider.feeAssetId)
    }

    @Test
    fun `updateSoraCardInfo EXPECT update data`() = runTest {
        viewModel.updateSoraCardInfo(
            accessToken = "accessToken",
            refreshToken = "refreshToken",
            accessTokenExpirationTime = Long.MAX_VALUE,
            kycStatus = "kycStatus"
        )
        advanceUntilIdle()

        verify(walletInteractor).updateSoraCardInfo(
            accessToken = "accessToken",
            refreshToken = "refreshToken",
            accessTokenExpirationTime = Long.MAX_VALUE,
            kycStatus = "kycStatus"
        )
    }

    @Test
    fun `onSeeBlacklist EXPECT open web view`() = runTest {
        given(resourceManager.getString(R.string.sora_card_blacklisted_countires_title))
            .willReturn("Title")

        viewModel.onSeeBlacklist()

        verify(mainRouter).showWebView("Title", "https://soracard.com/blacklist/")
    }
}