/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.recovery

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class RecoveryViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var interactor: OnboardingInteractor

    @Mock
    private lateinit var router: OnboardingRouter

    @Mock
    private lateinit var progress: WithProgress

    private lateinit var privacyViewModel: RecoveryViewModel

    @Before
    fun setUp() {
        privacyViewModel = RecoveryViewModel(interactor, router, progress)
    }

    @Test
    fun `on back pressed clicked`() {
        privacyViewModel.backButtonClick()
        verify(router).onBackButtonPressed()
    }

    @Test
    fun `btn next clicked`() = runBlockingTest {
        val mnemonic = "faculty soda zero quote reopen rubber jazz feed casual shed veteran badge"

        given(interactor.runRecoverFlow(mnemonic, "")).willReturn(Unit)
        given(interactor.isMnemonicValid(mnemonic)).willReturn(true)

        privacyViewModel.btnNextClick(mnemonic, "")

        verify(progress).showProgress()
        verify(progress).hideProgress()
        verify(router).showMainScreen()
    }
}