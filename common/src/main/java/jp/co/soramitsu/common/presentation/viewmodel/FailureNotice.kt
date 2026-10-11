package jp.co.soramitsu.common.presentation.viewmodel

import androidx.annotation.StringRes
import java.io.IOException
import jp.co.soramitsu.common.R

/**
 * The message for a failure that carries no text of its own. It says only what the app knows
 * from the failure itself, and it never asks the person to remove an account, clear data or
 * reinstall.
 */
enum class FailureNotice(
    @param:StringRes val titleResource: Int,
    @param:StringRes val messageResource: Int,
) {
    /**
     * The signer refused the selected account because its stored signing key cannot be used on
     * this phone (`WalletSigningGate`). It refuses before anything is signed or sent.
     */
    ACCOUNT_READ_ONLY(R.string.account_read_only_title, R.string.account_read_only_refused),

    /** Wallet changes are off for the whole app (read-only or recovery mode). */
    WALLET_READ_ONLY(R.string.safe_mode_read_only_title, R.string.safe_mode_read_only_body),

    /** A request to the network did not get through. */
    NETWORK(R.string.common_error_general_title, R.string.common_error_network),

    /** Anything else: nothing more is known than that it did not complete. */
    GENERIC(R.string.failure_generic_title, R.string.failure_generic_message),
    ;

    companion object {
        /** The codes of the two gates every signer passes (`WalletSelectionPolicy.kt`). */
        const val SIGNING_WALLET_UNAVAILABLE = "SIGNING_WALLET_UNAVAILABLE"
        const val WALLET_RECOVERY_READ_ONLY = "WALLET_RECOVERY_READ_ONLY"

        private const val MAX_CAUSE_DEPTH = 8

        /** Classified by the failure or the first cause in its chain that says something. */
        fun of(error: Throwable): FailureNotice {
            var current: Throwable? = error
            var depth = 0
            while (current != null && depth < MAX_CAUSE_DEPTH) {
                when {
                    current.message == SIGNING_WALLET_UNAVAILABLE -> return ACCOUNT_READ_ONLY
                    current.message == WALLET_RECOVERY_READ_ONLY -> return WALLET_READ_ONLY
                    current is IOException -> return NETWORK
                }
                current = current.cause.takeIf { it !== current }
                depth += 1
            }
            return GENERIC
        }
    }
}
