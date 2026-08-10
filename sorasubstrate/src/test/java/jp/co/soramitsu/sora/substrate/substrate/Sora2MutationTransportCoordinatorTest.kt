package jp.co.soramitsu.sora.substrate.substrate

import jp.co.soramitsu.sora.substrate.runtime.Sora2RuntimeContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class Sora2MutationTransportCoordinatorTest {

    @Test
    fun `requested and unreviewed sockets fail while exact connected socket admits`() {
        val coordinator = Sora2MutationTransportCoordinator()

        coordinator.setAddress(REVIEWED)
        assertTransportRejected(coordinator)

        coordinator.observeConnectedAddress("wss://mof2.sora.org")
        assertTransportRejected(coordinator)

        coordinator.observeConnectedAddress("$REVIEWED/")
        coordinator.requireReviewedTransport()

        coordinator.setAddress("wss://custom.example")
        coordinator.observeConnectedAddress("wss://custom.example")
        assertTransportRejected(coordinator)
    }

    @Test
    fun `switch during lease is deferred and release revokes admission before switch`() {
        val coordinator = admittedCoordinator()
        val lease = coordinator.acquire()

        assertNull(coordinator.requestSwitch(DEFERRED))
        coordinator.requireReviewedTransport()

        var switched = false
        lease.closeAndRunDeferredSwitch { address ->
            assertTransportRejected(coordinator)
            assertEquals(DEFERRED, address)
            switched = true
        }

        assertTrue(switched)
        assertTransportRejected(coordinator)
    }

    @Test
    fun `lease close is idempotent and cannot invoke deferred switch twice`() {
        val coordinator = admittedCoordinator()
        val lease = coordinator.acquire()
        assertNull(coordinator.requestSwitch(DEFERRED))
        var switchCount = 0

        lease.closeAndRunDeferredSwitch { switchCount += 1 }
        lease.closeAndRunDeferredSwitch { switchCount += 1 }

        assertEquals(1, switchCount)
        assertTransportRejected(coordinator)
    }

    private fun admittedCoordinator() = Sora2MutationTransportCoordinator().also {
        it.setAddress(REVIEWED)
        it.observeConnectedAddress(REVIEWED)
        it.requireReviewedTransport()
    }

    private fun assertTransportRejected(
        coordinator: Sora2MutationTransportCoordinator,
    ) {
        val error = try {
            coordinator.requireReviewedTransport()
            fail("Expected reviewed transport admission to fail")
            null
        } catch (error: Throwable) {
            error
        }
        assertEquals("SORA2_MUTATION_TRANSPORT_UNREVIEWED", error?.message)
    }

    private companion object {
        const val REVIEWED = Sora2RuntimeContract.RUNTIME_WS_ENDPOINT
        const val DEFERRED = "wss://custom.example"
    }
}
