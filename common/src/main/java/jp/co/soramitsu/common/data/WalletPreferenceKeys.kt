package jp.co.soramitsu.common.data

/**
 * Canonical registry for every preference key that can retain wallet identity or signing
 * material. Backup preflight and explicit deletion use the same registry so a newly added
 * credential cannot silently escape either safety boundary.
 */
object WalletPreferenceKeys {
    const val PRIVATE_KEY = "prefs_priv_key"
    const val PUBLIC_KEY = "prefs_pub_key"
    const val KEY_NONCE = "prefs_key_nonce"
    const val MNEMONIC = "prefs_mnemonic"
    const val SEED = "prefs_seed"
    const val LEGACY_ADDRESS = "prefs_address_pure"
    const val WATCH_ONLY = "wallet.watchOnly."
    const val NEEDS_MIGRATION = "needs_migration"
    const val IS_MIGRATION_FETCHED = "is_migration_fetched"
    const val CURRENT_ACCOUNT_ADDRESS = "cur_account_address"
    const val REGISTRATION_STATE = "registration_state"
    const val PIN_CODE = "user_pin_code"

    val encryptedCredentialPrefixes = listOf(
        PRIVATE_KEY,
        PUBLIC_KEY,
        KEY_NONCE,
        MNEMONIC,
        SEED,
    )

    val scopedEvidencePrefixes = encryptedCredentialPrefixes + WATCH_ONLY

    private val scopedStatePrefixes = scopedEvidencePrefixes + listOf(
        NEEDS_MIGRATION,
        IS_MIGRATION_FETCHED,
    )

    val generalEvidenceKeys = setOf(
        LEGACY_ADDRESS,
        CURRENT_ACCOUNT_ADDRESS,
        REGISTRATION_STATE,
        PIN_CODE,
    )

    fun scopedStringKeys(walletId: String): List<String> =
        (listOf(LEGACY_ADDRESS) + encryptedCredentialPrefixes).map { it + walletId }

    fun scopedBooleanKeys(walletId: String): List<String> = listOf(
        WATCH_ONLY + walletId,
        NEEDS_MIGRATION + walletId,
        IS_MIGRATION_FETCHED + walletId,
    )

    val legacyUnsuffixedStringKeys =
        listOf(LEGACY_ADDRESS) + encryptedCredentialPrefixes

    fun isWalletStateKey(key: String): Boolean =
        key in generalEvidenceKeys ||
            key in legacyUnsuffixedStringKeys ||
            scopedStatePrefixes.any { prefix ->
                key.length > prefix.length && key.startsWith(prefix)
            }
}
