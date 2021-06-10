/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.profile

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Single
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class ProfileViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Rule
    @JvmField
    val rxSchedulerRule = RxSchedulersRule()

    @Mock
    private lateinit var interactor: MainInteractor

    @Mock
    private lateinit var router: MainRouter

    @Mock
    private lateinit var numbersFormatter: NumbersFormatter

    private lateinit var profileViewModel: ProfileViewModel

    @Before
    fun setUp() {
        given(interactor.isBiometryEnabled()).willReturn(Single.just(true))
        given(interactor.isBiometryAvailable()).willReturn(Single.just(true))
        profileViewModel = ProfileViewModel(interactor, router, numbersFormatter)
    }

    @Test
    fun `help card clicked`() {
        profileViewModel.btnHelpClicked()
        verify(router).showFaq()
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