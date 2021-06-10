/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain

import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class InvitationInteractorTest {

    @Rule
    @JvmField
    var schedulersRule = RxSchedulersRule()

    @Mock
    private lateinit var userRepository: UserRepository

    private lateinit var interactor: InvitationInteractor

    @Before
    fun setUp() {
        interactor = InvitationInteractor(userRepository)
    }

    @Test
    fun `getInviteLink() calls getInvitationLink from userRepository`() {
        val invitationLink = "test invite link"
        given(userRepository.getInvitationLink()).willReturn(Single.just(invitationLink))

        interactor.getInviteLink()
            .test()
            .assertResult(invitationLink)

        verify(userRepository).getInvitationLink()
        verifyNoMoreInteractions(userRepository)
    }
}
