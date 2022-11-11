/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.profile

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_referral_api.ReferralRouter
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.feature_select_node_api.SelectNodeRouter
import jp.co.soramitsu.feature_select_node_api.domain.model.Node
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
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
    private lateinit var router: MainRouter

    @Mock
    private lateinit var referralRouter: ReferralRouter

    @Mock
    private lateinit var selectNodeRouter: SelectNodeRouter

    @Mock
    private lateinit var nodeManager: NodeManager

    private lateinit var profileViewModel: ProfileViewModel

    @Before
    fun setUp() = runTest {
        whenever(interactor.flowSelectedNode()).thenReturn(
            flowOf(
                Node(
                    chain = "SORA",
                    name = "node",
                    address = "address",
                    isSelected = true,
                    isDefault = true
                )
            )
        )

        profileViewModel = ProfileViewModel(
            interactor,
            router,
            referralRouter,
            selectNodeRouter,
            nodeManager
        )
    }

    @Test
    fun `help card clicked`() {
        profileViewModel.btnHelpClicked()
        verify(router).showFaq()
    }

    @Test
    fun `invite card clicked`() {
        profileViewModel.profileFriendsClicked()
        verify(referralRouter).showReferrals()
    }

    @Test
    fun `about item clicked`() {
        profileViewModel.profileAboutClicked()
        verify(router).showAbout()
    }

    @Test
    fun `select node clicked`() {
        profileViewModel.selectNodeClicked()
        verify(selectNodeRouter).showSelectNode()
    }


    @Test
    fun `get selected node`() = runTest {
        advanceUntilIdle()

        verify(interactor).flowSelectedNode()
    }

    @Test
    fun `get selected node update state`() = runTest {
        advanceUntilIdle()

        assertEquals(profileViewModel.selectedNode.value?.address, "address")
    }

    @Test
    fun `initialize expect subscribe to connection state`() = runTest {
        advanceUntilIdle()

        verify(nodeManager).connectionState()
    }
}