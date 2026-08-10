package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.polkamarkt

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PolkamarktPendingObservationPolicyTest {

    @Test
    fun `pending observer failure resubscribes and receives repaired state`() = runTest {
        var subscriptions = 0
        var failures = 0
        val received = mutableListOf<String>()

        flow {
            subscriptions += 1
            if (subscriptions == 1) error("transient Room failure")
            emit("repaired")
        }
            .retryPolkamarktPendingObservation(
                isCurrentAccount = { true },
                onFailure = { failures += 1 },
            )
            .collect { received += it }

        assertEquals(2, subscriptions)
        assertEquals(1, failures)
        assertEquals(listOf("repaired"), received)
    }

    @Test
    fun `authoritative empty pending state clears only the recovery error`() {
        assertNull(
            PolkamarktPendingObservationPolicy.resolvedError(
                recoveryRequired = false,
                currentError = PolkamarktPendingObservationPolicy.RECOVERY_ERROR,
            ),
        )
        assertEquals(
            "POLKAMARKT_OTHER_ERROR",
            PolkamarktPendingObservationPolicy.resolvedError(
                recoveryRequired = false,
                currentError = "POLKAMARKT_OTHER_ERROR",
            ),
        )
    }

    @Test
    fun `PI lag retries exact reconciliation until Room becomes terminal`() = runTest {
        var reconciliationPasses = 0
        var pendingChecks = 0
        val retryDelays = mutableListOf<Long>()

        reconcilePolkamarktPendingUntilTerminal(
            isCurrentAccount = { true },
            reconcileOnce = {
                reconciliationPasses += 1
                false
            },
            hasUnresolvedPending = {
                pendingChecks += 1
                pendingChecks < 3
            },
            onFailure = { error("Unexpected recovery failure: $it") },
            delayBeforeRetry = { retryDelays += it },
        )

        assertEquals(3, reconciliationPasses)
        assertEquals(listOf(1_000L, 2_000L), retryDelays)
    }

    @Test
    fun `partial reconciliation resets backoff while unresolved rows remain`() = runTest {
        var reconciliationPasses = 0
        var pendingChecks = 0
        val retryDelays = mutableListOf<Long>()

        reconcilePolkamarktPendingUntilTerminal(
            isCurrentAccount = { true },
            reconcileOnce = {
                reconciliationPasses += 1
                reconciliationPasses == 1
            },
            hasUnresolvedPending = {
                pendingChecks += 1
                pendingChecks < 3
            },
            onFailure = { error("Unexpected recovery failure: $it") },
            delayBeforeRetry = { retryDelays += it },
        )

        assertEquals(3, reconciliationPasses)
        assertEquals(listOf(1_000L, 1_000L), retryDelays)
    }

    @Test
    fun `account switch stops PI lag recovery without another pass`() = runTest {
        var currentAccount = true
        var reconciliationPasses = 0

        reconcilePolkamarktPendingUntilTerminal(
            isCurrentAccount = { currentAccount },
            reconcileOnce = {
                reconciliationPasses += 1
                false
            },
            hasUnresolvedPending = { true },
            onFailure = { error("Unexpected recovery failure: $it") },
            delayBeforeRetry = { currentAccount = false },
        )

        assertEquals(1, reconciliationPasses)
    }
}
