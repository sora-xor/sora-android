/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.profile

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_referral_api.ReferralRouter
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

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

    private lateinit var profileViewModel: ProfileViewModel

    @Before
    fun setUp() = runTest {
        profileViewModel = ProfileViewModel(interactor, router, referralRouter)
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
    fun `passphrase item clicked`() {
        profileViewModel.onPassphraseClick()
        verify(router).showPin(PinCodeAction.OPEN_PASSPHRASE)
    }

    @Test
    fun `about item clicked`() {
        profileViewModel.profileAboutClicked()
        verify(router).showAbout()
    }

    @Test
    fun `votes item clicked`() {
        profileViewModel.onVotesClick()
        verify(router).showVotesHistory()
    }
}