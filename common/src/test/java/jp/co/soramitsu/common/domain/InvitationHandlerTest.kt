package jp.co.soramitsu.common.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class InvitationHandlerTest {

    private lateinit var invitationHandler: InvitationHandler

    private val dispatcher = TestCoroutineDispatcher()

    @Before
    fun setup() {
        invitationHandler = InvitationHandler()
    }

    @Test
    fun `invitations handler test`() = runBlocking {
        val actual = mutableListOf<String>()
        launch(dispatcher) {
            invitationHandler.observeInvitationApplies().take(3).collect { actual.add(it) }
        }
        invitationHandler.invitationApplied()
        invitationHandler.invitationApplied()
        invitationHandler.invitationApplied()

        assertEquals(listOf("", "", ""), actual)
    }

}