/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Completable
import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeManager
import jp.co.soramitsu.common.domain.InvitationHandler
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
class OnboardingViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Rule
    @JvmField
    val rxSchedulerRule = RxSchedulersRule()

    @Mock
    private lateinit var invitationHandler: InvitationHandler

    @Mock
    private lateinit var runtime: RuntimeManager

    private lateinit var onboardingViewModel: OnboardingViewModel

    @Before
    fun setUp() {
        given(runtime.start()).willReturn(Completable.complete())
        onboardingViewModel = OnboardingViewModel(invitationHandler, runtime)
    }

    @Test
    fun `success verification code entered`() {
        onboardingViewModel.startedWithInviteAction()

        verify(invitationHandler).invitationApplied()
    }
}