package jp.co.soramitsu.common.domain

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class InvitationHandlerTest {

    private lateinit var invitationHandler: InvitationHandler

    @Before fun setup() {
        invitationHandler = InvitationHandler()
    }

    @Test fun `invitations handler test`() {
        var invitationsCount = 0

        invitationHandler.observeInvitationApplies()
            .subscribe { invitationsCount++ }

        assertEquals(0, invitationsCount)

        invitationHandler.invitationApplied()
        assertEquals(1, invitationsCount)

        invitationHandler.invitationApplied()
        invitationHandler.invitationApplied()
        assertEquals(3, invitationsCount)
    }

}