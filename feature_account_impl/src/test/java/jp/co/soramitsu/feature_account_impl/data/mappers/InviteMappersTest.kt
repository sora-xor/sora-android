/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.mappers

import jp.co.soramitsu.feature_account_api.domain.model.InvitedUser
import jp.co.soramitsu.feature_account_impl.data.network.model.InvitedRemote
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class InviteMappersTest {

    @Test
    fun `map invited dto to invite user called`() {
        val invitedRemote = InvitedRemote(
            "firstName",
            "lastName"
        )
        val invitedUser = InvitedUser(
            "firstName",
            "lastName"
        )

        assertEquals(invitedUser, mapInvitedDtoToInvitedUser(invitedRemote))
    }

    @Test
    fun `map invited dto to invite user called if null`() {
        assertNull(mapInvitedDtoToInvitedUser(null))
    }
}