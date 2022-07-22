/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain

import io.mockk.mockkObject
import jp.co.soramitsu.common.domain.FlavorOptionsProvider
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
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
    fun `getInviteLink() calls getInvitationLink from userRepository`() = runTest {
        val invitationLink = "github.io"
        mockkObject(FlavorOptionsProvider)
        val link = interactor.getInviteLink()
        assertTrue(link.contains(invitationLink))
    }
}
