/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_main_impl.presentation.profile

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.domain.ChainNode
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_referral_api.ReferralRouter
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.feature_select_node_api.SelectNodeRouter
import jp.co.soramitsu.feature_blockexplorer_api.data.ProductionFeatureManager
import jp.co.soramitsu.feature_blockexplorer_api.data.ProductionFeatureState
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

@ExperimentalCoroutinesApi
class ProfileViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @MockK
    private lateinit var interactor: MainInteractor

    @MockK
    private lateinit var polkaswapRouter: PolkaswapRouter

    @MockK
    private lateinit var router: MainRouter

    @MockK
    private lateinit var referralRouter: ReferralRouter

    @MockK
    private lateinit var selectNodeRouter: SelectNodeRouter

    @MockK
    private lateinit var nodeManager: NodeManager

    @MockK
    private lateinit var productionFeatureManager: ProductionFeatureManager

    private lateinit var profileViewModel: ProfileViewModel

    private fun initViewModel() {
        profileViewModel = ProfileViewModel(
            interactor,
            polkaswapRouter,
            router,
            referralRouter,
            selectNodeRouter,
            productionFeatureManager,
            nodeManager,
        )
    }

    @Before
    fun setUp() = runTest {
        every { interactor.flowSelectedNode() } returns
            flowOf(
                ChainNode(
                    chain = "SORA",
                    name = "node",
                    address = "address",
                    isSelected = true,
                    isDefault = true
                )
            )
        coEvery { productionFeatureManager.getState() } returns ProductionFeatureState(
            nexusAvailable = true,
            nexusSendsAvailable = false,
            polkamarktVisible = true,
            polkamarktMutationsAvailable = false,
            tairaAvailable = true,
            tairaVisible = true,
            tairaPreferenceIsExplicit = false,
        )
        every { nodeManager.connectionState } returns flowOf(true)
    }

    @Test
    fun `init succesfull`() = runTest {
        initViewModel()
        advanceUntilIdle()
        profileViewModel.state.value.let {
            assertEquals("node", it.nodeName)
            assertEquals(true, it.nodeConnected)
        }
    }

    @Test
    fun `each settings action routes to its own destination once`() = runTest {
        every { router.showAccountList() } returns Unit
        every { router.showAppSettings() } returns Unit
        every { router.showLoginSecurity() } returns Unit
        every { router.showInformation() } returns Unit
        every { router.showDebugMenu() } returns Unit
        every { polkaswapRouter.showPolkamarkt() } returns Unit
        every { referralRouter.showReferrals() } returns Unit
        every { selectNodeRouter.showSelectNode() } returns Unit
        initViewModel()
        profileViewModel.showAccountList(); profileViewModel.showAppSettings()
        profileViewModel.showLogin(); profileViewModel.showAbout(); profileViewModel.showDebugMenu()
        profileViewModel.showPolkamarkt(); profileViewModel.showReferral(); profileViewModel.showSelectNode()
        io.mockk.verify(exactly = 1) {
            router.showAccountList(); router.showAppSettings(); router.showLoginSecurity()
            router.showInformation(); router.showDebugMenu(); polkaswapRouter.showPolkamarkt()
            referralRouter.showReferrals(); selectNodeRouter.showSelectNode()
        }
    }

    @Test
    fun `node stream failure is reported while independent feature state remains available`() = runTest {
        every { interactor.flowSelectedNode() } returns kotlinx.coroutines.flow.flow { throw IllegalStateException("node read failed") }
        initViewModel(); advanceUntilIdle()
        org.junit.Assert.assertNotNull(profileViewModel.errorFromResourceLiveData.value)
        assertEquals(true, profileViewModel.state.value.polkamarktVisible)
    }

    @Test
    fun `late feature configuration preserves the already connected node`() = runTest {
        coEvery { productionFeatureManager.getState() } coAnswers {
            delay(2000)
            ProductionFeatureState(false, false, false, false, false, false, false)
        }
        initViewModel()
        advanceTimeBy(1500)
        runCurrent()
        assertEquals("node", profileViewModel.state.value.nodeName)
        assertEquals(true, profileViewModel.state.value.nodeConnected)
        advanceUntilIdle()
        assertEquals("node", profileViewModel.state.value.nodeName)
        assertEquals(true, profileViewModel.state.value.nodeConnected)
        assertEquals(false, profileViewModel.state.value.polkamarktVisible)
    }
}
