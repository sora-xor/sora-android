package jp.co.soramitsu.common.presentation.viewmodel

import androidx.annotation.StringRes
import java.io.IOException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import javax.net.ssl.SSLException
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.network.BoundedHttpTextException

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

    /**
     * A signed transaction was handed to the network and its outcome could not be read
     * (`ExtrinsicSubmissionUnknownException`). It may already be on its way into a block, so
     * the person is told to look at Activity before trying again, never to "try again later".
     */
    SUBMISSION_UNKNOWN(
        R.string.failure_submission_unknown_title,
        R.string.failure_submission_unknown_message,
    ),

    /** A request to the network did not get through. */
    NETWORK(R.string.common_error_general_title, R.string.common_error_network),

    /** Anything else: nothing more is known than that it did not complete. */
    GENERIC(R.string.failure_generic_title, R.string.failure_generic_message),
    ;

    companion object {
        /** The codes of the two gates every signer passes (`WalletSelectionPolicy.kt`). */
        const val SIGNING_WALLET_UNAVAILABLE = "SIGNING_WALLET_UNAVAILABLE"
        const val WALLET_RECOVERY_READ_ONLY = "WALLET_RECOVERY_READ_ONLY"

        /** The message of `ExtrinsicSubmissionUnknownException` (`ExtrinsicManager.kt`). */
        const val EXTRINSIC_SUBMISSION_UNKNOWN = "EXTRINSIC_SUBMISSION_UNKNOWN"

        private const val MAX_CAUSE_DEPTH = 8

        /** Packages whose `IOException`s come from a network request, never from storage. */
        private val NETWORK_PACKAGES = listOf("java.net.", "javax.net.", "io.ktor.", "okhttp3.")

        /**
         * Classified by the failure or the first cause in its chain that says something.
         *
         * A submission whose outcome is unknown is named before its cause is looked at: the
         * cause is normally the network error that hid the answer, and "check the connection
         * and try again" is exactly what must not be said about a transaction that may be on
         * chain.
         */
        fun of(error: Throwable): FailureNotice {
            var current: Throwable? = error
            var depth = 0
            while (current != null && depth < MAX_CAUSE_DEPTH) {
                when {
                    current.message == SIGNING_WALLET_UNAVAILABLE -> return ACCOUNT_READ_ONLY
                    current.message == WALLET_RECOVERY_READ_ONLY -> return WALLET_READ_ONLY
                    current.message == EXTRINSIC_SUBMISSION_UNKNOWN -> return SUBMISSION_UNKNOWN
                    isNetworkFailure(current) -> return NETWORK
                }
                current = current.cause.takeIf { it !== current }
                depth += 1
            }
            return GENERIC
        }

        /**
         * Only failures of a network request. A plain `IOException` is also how local storage
         * reports a full disk or an unreadable file (DataStore, `WalletStorageFailures`); that
         * is not a reason to send someone to check their internet connection.
         */
        private fun isNetworkFailure(error: Throwable): Boolean = when (error) {
            is BoundedHttpTextException,
            is UnknownHostException,
            is SocketException,
            is SocketTimeoutException,
            is ProtocolException,
            is SSLException,
            is UnresolvedAddressException -> true
            is IOException -> NETWORK_PACKAGES.any { error.javaClass.name.startsWith(it) }
            else -> false
        }
    }
}
