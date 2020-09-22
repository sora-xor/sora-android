package jp.co.soramitsu.common.domain

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PushHandlerTest {

    private lateinit var pushHandler: PushHandler

    @Before fun setup() {
        pushHandler = PushHandler()
    }

    @Test fun `invitations handler test`() {
        var pushesCount = 0

        pushHandler.observeNewPushes()
            .subscribe { pushesCount++ }

        assertEquals(0, pushesCount)

        pushHandler.pushReceived()
        assertEquals(1, pushesCount)

        pushHandler.pushReceived()
        pushHandler.pushReceived()
        assertEquals(3, pushesCount)
    }
}