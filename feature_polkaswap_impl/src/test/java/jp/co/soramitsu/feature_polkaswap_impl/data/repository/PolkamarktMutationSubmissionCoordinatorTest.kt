package jp.co.soramitsu.feature_polkaswap_impl.data.repository

import jp.co.soramitsu.common.account.WalletMutationCoordinator
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PolkamarktMutationSubmissionCoordinatorTest {

    @Test
    fun `queued mutation cannot prepare the same nonce before first transport handoff`() = runTest {
        var nextChainNonce = 7
        val preparedNonces = mutableListOf<Int>()
        val preparedHashes = mutableListOf<String>()
        val firstPrepared = CompletableDeferred<Unit>()
        val allowFirstTransportHandoff = CompletableDeferred<Unit>()
        val secondAttempted = CompletableDeferred<Unit>()
        val secondPrepared = CompletableDeferred<Unit>()

        val first = async {
            PolkamarktMutationSubmissionCoordinator.withNonceReservedUntilTransport {
                val nonce = WalletMutationCoordinator.withLock {
                    nextChainNonce.also {
                        preparedNonces += it
                        preparedHashes += transactionHashFor(it)
                    }
                }
                firstPrepared.complete(Unit)
                allowFirstTransportHandoff.await()
                WalletMutationCoordinator.withLock {
                    nextChainNonce = nonce + 1
                }
            }
        }
        firstPrepared.await()

        val second = async {
            secondAttempted.complete(Unit)
            PolkamarktMutationSubmissionCoordinator.withNonceReservedUntilTransport {
                val nonce = WalletMutationCoordinator.withLock {
                    nextChainNonce.also {
                        preparedNonces += it
                        preparedHashes += transactionHashFor(it)
                    }
                }
                secondPrepared.complete(Unit)
                WalletMutationCoordinator.withLock {
                    nextChainNonce = nonce + 1
                }
            }
        }
        secondAttempted.await()
        runCurrent()

        assertFalse(secondPrepared.isCompleted)
        assertEquals(listOf(7), preparedNonces)

        allowFirstTransportHandoff.complete(Unit)
        first.await()
        second.await()

        assertEquals(listOf(7, 8), preparedNonces)
        assertEquals(
            listOf(transactionHashFor(7), transactionHashFor(8)),
            preparedHashes,
        )
        assertEquals(2, preparedHashes.distinct().size)
    }

    @Test
    fun `app wide admission rejects a second screen and releases after completion`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async {
            PolkamarktMutationAdmissionGate.withAdmission {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()

        val rejected = runCatching {
            PolkamarktMutationAdmissionGate.withAdmission { "second" }
        }.exceptionOrNull()

        assertTrue(rejected is IllegalStateException)
        assertEquals("POLKAMARKT_MUTATION_IN_FLIGHT", rejected?.message)
        release.complete(Unit)
        first.await()
        assertEquals(
            "after",
            PolkamarktMutationAdmissionGate.withAdmission { "after" },
        )
    }

    @Test
    fun `app wide admission lease releases on cancellation`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val active = async {
            PolkamarktMutationAdmissionGate.withAdmission {
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        entered.await()

        active.cancelAndJoin()

        assertEquals(
            "recovered",
            PolkamarktMutationAdmissionGate.withAdmission { "recovered" },
        )
    }

    private fun transactionHashFor(nonce: Int): String =
        "0x${nonce.toString(16).padStart(64, '0')}"
}
