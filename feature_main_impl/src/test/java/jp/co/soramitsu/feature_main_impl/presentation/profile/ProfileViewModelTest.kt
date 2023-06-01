/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.profile

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.domain.ChainNode
import jp.co.soramitsu.common.domain.SoraCardInformation
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_referral_api.ReferralRouter
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.feature_select_node_api.SelectNodeRouter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.oauth.common.model.KycStatus
import jp.co.soramitsu.sora.substrate.blockexplorer.SoraConfigManager
import jp.co.soramitsu.test_shared.MainCoroutineRule
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class ProfileViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var interactor: MainInteractor

    @Mock
    private lateinit var assetsRouter: AssetsRouter

    @Mock
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var router: MainRouter

    @Mock
    private lateinit var walletRouter: WalletRouter

    @Mock
    private lateinit var referralRouter: ReferralRouter

    @Mock
    private lateinit var selectNodeRouter: SelectNodeRouter

    @Mock
    private lateinit var nodeManager: NodeManager

    @Mock
    private lateinit var soraConfigManager: SoraConfigManager

    private lateinit var profileViewModel: ProfileViewModel

    private fun initViewModel() {
        profileViewModel = ProfileViewModel(
            assetsRouter,
            interactor,
            walletInteractor,
            router,
            walletRouter,
            referralRouter,
            selectNodeRouter,
            soraConfigManager,
            nodeManager,
        )
    }

    @Before
    fun setUp() = runTest {
        whenever(interactor.flowSelectedNode()).thenReturn(
            flowOf(
                ChainNode(
                    chain = "SORA",
                    name = "node",
                    address = "address",
                    isSelected = true,
                    isDefault = true
                )
            )
        )

        whenever(soraConfigManager.getSoraCard()).thenReturn(true)

        whenever(nodeManager.connectionState).thenReturn(
            flowOf(
                true
            )
        )
    }

    @Test
    fun `init succesfull`() = runTest {
        whenever(walletInteractor.subscribeSoraCardInfo()).thenReturn(flowOf(SoraCardInformation("id", "accesstoken", "refreshToken", 0, "")))
        initViewModel()

        advanceUntilIdle()

        profileViewModel.state.let {
            assertEquals("node", it.nodeName)
            assertEquals(true, it.nodeConnected)
        }
    }

    @Test
    fun `call showSoraCard with no state EXPECT navigate to get sora card`() = runTest {
        whenever(walletInteractor.subscribeSoraCardInfo()).thenReturn(flowOf(SoraCardInformation("id", "accesstoken", "refreshToken", 0, "")))
        initViewModel()
        advanceUntilIdle()
        profileViewModel.showSoraCard()

        verify(router).showGetSoraCard()
    }

    @Test
    fun `call showSoraCard with state EXPECT navigate to sora card sdk state screen`() = runTest {
        whenever(walletInteractor.subscribeSoraCardInfo()).thenReturn(flowOf(SoraCardInformation("id", "accesstoken", "refreshToken", 0, KycStatus.Failed.toString())))
        initViewModel()
        advanceUntilIdle()
        profileViewModel.showSoraCard()
        advanceUntilIdle()
        assertEquals(jp.co.soramitsu.oauth.base.sdk.SoraCardInfo(accessToken="accesstoken", accessTokenExpirationTime=0, refreshToken="refreshToken"), profileViewModel.launchSoraCardSignIn.value?.soraCardInfo)

    }

    @Test
    fun `call showBuyCrypto EXPECT navigate to buy crypto screen`() {
        initViewModel()

        profileViewModel.showBuyCrypto()

        verify(assetsRouter).showBuyCrypto()
    }
}