/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class InvitationHandlerTest {

    private lateinit var invitationHandler: InvitationHandler

    @Before
    fun setup() {
        invitationHandler = InvitationHandler()
    }

    @Test
    fun `invitations handler test`() = runTest {
        val actual = mutableListOf<String>()
        launch {
            invitationHandler.observeInvitationApplies().take(3).collect { actual.add(it) }
        }
        invitationHandler.invitationApplied()
        advanceUntilIdle()
        invitationHandler.invitationApplied()
        advanceUntilIdle()
        invitationHandler.invitationApplied()
        advanceUntilIdle()
        assertEquals(listOf("", "", ""), actual)
    }

}