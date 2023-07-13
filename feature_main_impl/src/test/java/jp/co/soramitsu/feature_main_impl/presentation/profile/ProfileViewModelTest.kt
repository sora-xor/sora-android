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

package jp.co.soramitsu.feature_main_impl.presentation.profile

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.domain.ChainNode
import jp.co.soramitsu.common.domain.SoraCardInformation
import jp.co.soramitsu.feature_assets_api.data.models.XorAssetBalance
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_referral_api.ReferralRouter
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.feature_select_node_api.SelectNodeRouter
import jp.co.soramitsu.feature_sora_card_api.domain.SoraCardInteractor
import jp.co.soramitsu.feature_sora_card_api.domain.models.SoraCardAvailabilityInfo
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.oauth.common.model.KycStatus
import jp.co.soramitsu.sora.substrate.blockexplorer.SoraConfigManager
import jp.co.soramitsu.test_data.SoraCardTestData
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_shared.MainCoroutineRule
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
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
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

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
    private lateinit var polkaswapRouter: PolkaswapRouter

    @Mock
    private lateinit var assetsInteractor: AssetsInteractor

    @Mock
    private lateinit var soraCardInteractor: SoraCardInteractor

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
            polkaswapRouter,
            walletInteractor,
            router,
            walletRouter,
            referralRouter,
            selectNodeRouter,
            soraConfigManager,
            assetsInteractor,
            soraCardInteractor,
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
        whenever(walletInteractor.subscribeSoraCardInfo()).thenReturn(
            flowOf(
                SoraCardInformation(
                    "accesstoken",
                    0,
                    "",
                )
            )
        )
        initViewModel()

        advanceUntilIdle()

        profileViewModel.state.let {
            assertEquals("node", it.nodeName)
            assertEquals(true, it.nodeConnected)
        }
    }

    @Test
    fun `call showSoraCard with no state EXPECT navigate to get sora card`() = runTest {
        whenever(walletInteractor.subscribeSoraCardInfo()).thenReturn(
            flowOf(
                SoraCardInformation(
                    "accesstoken",
                    0,
                    "",
                )
            )
        )
        initViewModel()
        advanceUntilIdle()
        profileViewModel.showSoraCard()

        verify(router).showGetSoraCard()
    }

    @Test
    fun `call showSoraCard with state EXPECT navigate to sora card sdk state screen`() = runTest {
        whenever(walletInteractor.subscribeSoraCardInfo()).thenReturn(flowOf(SoraCardInformation("accesstoken", 0, KycStatus.Failed.toString())))
        whenever(soraCardInteractor.subscribeToSoraCardAvailabilityFlow()).thenReturn(flowOf(
            SoraCardAvailabilityInfo(
                xorBalance = BigDecimal.ONE,
                enoughXor = true,
            )
        ))
        initViewModel()
        advanceUntilIdle()
        profileViewModel.showSoraCard()
        advanceUntilIdle()
        assertNotNull(profileViewModel.launchSoraCardSignIn.value)
    }

    @Test
    fun `call showBuyCrypto EXPECT navigate to buy crypto screen`() {
        initViewModel()

        profileViewModel.showBuyCrypto()

        verify(assetsRouter).showBuyCrypto()
    }
}
