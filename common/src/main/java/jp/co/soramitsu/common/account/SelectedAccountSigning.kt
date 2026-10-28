package jp.co.soramitsu.common.account

import kotlinx.coroutines.flow.Flow

/**
 * What a screen needs to know about the selected account's ability to sign on this phone.
 *
 * Display only. The signer never reads this: it applies its own rule to the stored key every
 * time (`WalletSigningGate`), so a stale value here can neither allow nor block a signature.
 */
data class SelectedAccountSigning(
    /** The selected account's address, or "" when there is none. */
    val address: String = "",
    /**
     * The account is listed but its stored signing key cannot be used on this phone. It still
     * shows balances and history and can receive; sending, swapping and every other transaction
     * is off for this account only.
     */
    val readOnly: Boolean = false,
) {
    companion object {
        val NONE = SelectedAccountSigning()
    }
}

/** Follows the selected account and whether it can sign. Never throws to its collector. */
fun interface SelectedAccountSigningSource {
    fun flow(): Flow<SelectedAccountSigning>
}

/**
 * Where a screen sends the person to repair, back up or remove an account: Account options.
 * (The same destination as `MainRouter.showAccountDetails`, reachable from every module.)
 */
interface AccountOptionsRouter {
    fun showAccountDetails(address: String)
}
