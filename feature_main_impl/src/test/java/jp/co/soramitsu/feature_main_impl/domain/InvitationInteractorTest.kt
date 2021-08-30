/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain

import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class InvitationInteractorTest {

    @Mock
    private lateinit var userRepository: UserRepository

    private lateinit var interactor: InvitationInteractor

    @Before
    fun setUp() {
        interactor = InvitationInteractor(userRepository)
    }

    @Test
    fun `getInviteLink() calls getInvitationLink from userRepository`() = runBlockingTest {
        val invitationLink = "test invite link"
        given(userRepository.getInvitationLink()).willReturn(invitationLink)

        assertEquals(invitationLink, interactor.getInviteLink())
        verify(userRepository).getInvitationLink()
        verifyNoMoreInteractions(userRepository)
    }
}
