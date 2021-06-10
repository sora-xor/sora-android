/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.invite

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.SingleTransformer
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.util.TimerWrapper
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.InvitationInteractor
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
@Ignore("temp. will be enabled after invitation is done")
class InviteViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()
    @Rule
    @JvmField
    val schedulersRule = RxSchedulersRule()

    @Mock
    private lateinit var interactor: InvitationInteractor
    @Mock
    private lateinit var router: MainRouter
    @Mock
    private lateinit var progress: WithProgress
    @Mock
    private lateinit var invitationHandler: InvitationHandler
    @Mock
    private lateinit var timer: TimerWrapper

    private lateinit var inviteViewModel: InviteViewModel

    @Before
    fun setUp() {
        given(progress.progressCompose<Any>()).willReturn(SingleTransformer { upstream -> upstream })
        given(invitationHandler.observeInvitationApplies()).willReturn(Observable.just(""))

        inviteViewModel = InviteViewModel(
            interactor,
            router,
            progress,
        )
    }

    @Test
    fun `send invitation`() {
        val inviteLink = "test invite link"

        given(interactor.getInviteLink()).willReturn(Single.just(inviteLink))
        given(timer.start(anyLong(), anyLong())).willReturn(Observable.just(0))

        inviteViewModel.sendInviteClick()

        verify(interactor).getInviteLink()

        inviteViewModel.shareCodeLiveData.observeForever {
            assertEquals(inviteLink, it)
        }

        assertEquals(inviteLink, inviteViewModel.shareCodeLiveData.value)
    }

}