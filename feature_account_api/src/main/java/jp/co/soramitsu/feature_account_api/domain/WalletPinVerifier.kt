package jp.co.soramitsu.feature_account_api.domain

import java.security.MessageDigest
import javax.inject.Inject
import jp.co.soramitsu.common.account.WalletMutationCoordinator
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository

class WalletPinLockedException(val remainingMillis: Long) : Exception("PIN_LOCKED")

/** Shared persistent attempt budget for wallet unlock and per-request signature approval. */
class WalletPinVerifier @Inject constructor(private val repository: UserRepository) {
    internal var now: () -> Long = System::currentTimeMillis

    suspend fun verify(pin: String): Boolean = WalletMutationCoordinator.withLock {
        val tries = repository.retrievePinTriesUsed().coerceAtLeast(0)
        val started = repository.retrieveTimerStartedTimestamp()
        val duration = lockMillis(tries)
        val remaining = if (started > 0) {
            (duration - (now() - started)).coerceIn(0, duration)
        } else 0
        if (remaining > 0) throw WalletPinLockedException(remaining)
        val stored = repository.retrievePin()
        if (stored.isNotEmpty() && MessageDigest.isEqual(stored.toByteArray(), pin.toByteArray())) {
            repository.resetTriesUsed()
            repository.resetTimerStartedTimestamp()
            true
        } else {
            val next = (tries.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            repository.savePinTriesUsed(next)
            val lock = lockMillis(next)
            if (lock > 0) {
                repository.saveTimerStartedTimestamp(now())
                throw WalletPinLockedException(lock)
            }
            false
        }
    }

    companion object {
        fun lockMillis(tries: Int): Long = when {
            tries >= 6 -> 30 * 60_000L
            tries == 5 -> 15 * 60_000L
            tries == 4 -> 5 * 60_000L
            tries == 3 -> 60_000L
            else -> 0L
        }
    }
}
