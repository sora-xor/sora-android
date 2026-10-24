package jp.co.soramitsu.common.account

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Process-wide serialization boundary for wallet identity changes and SORA2 signing.
 *
 * Room transactions protect database rows, but wallet deletion also updates encrypted
 * preferences. Keeping signing and identity mutation behind the same lock closes the interval
 * where a caller could retain a key in memory while an authenticated deletion commits.
 */
object WalletMutationCoordinator {
    private val mutex = Mutex()
    private val lockContext = LockContext()

    /**
     * Repository operations may call a signer while already holding this boundary. The marker
     * makes that same coroutine re-entrant without making the underlying mutex generally
     * re-entrant. Callers must not fan out wallet mutations into child coroutines from [block].
     */
    suspend fun <T> withLock(block: suspend () -> T): T {
        if (currentCoroutineContext()[LockContextKey] != null) return block()
        return mutex.withLock {
            withContext(lockContext) { block() }
        }
    }

    private object LockContextKey : CoroutineContext.Key<LockContext>

    private class LockContext :
        AbstractCoroutineContextElement(LockContextKey)
}
