/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain

import jp.co.soramitsu.sora.substrate.blockexplorer.SoraConfigManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.given

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class InvitationInteractorTest {

    @Mock
    private lateinit var manager: SoraConfigManager

    private lateinit var interactor: InvitationInteractor

    private val invitationLink = "github.io"

    @Before
    fun setUp() = runTest {
        given(manager.getInviteLink()).willReturn(invitationLink)
        interactor = InvitationInteractor(manager)
    }

    @Test
    fun `getInviteLink() calls getInvitationLink from userRepository`() = runTest {
        val link = interactor.getInviteLink()
        assertTrue(link.contains(invitationLink))
    }
}
