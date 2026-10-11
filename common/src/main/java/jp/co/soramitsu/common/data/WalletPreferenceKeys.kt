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

    /**
     * Key material that "Remove all accounts" leaves in place when it does not belong to one of
     * the removed accounts: credentials and watch-only markers of key sets with no account row
     * (for example a create-account flow abandoned in an older release) and the release-era
     * unsuffixed set when its owner is not being removed. These sets are invisible in the app,
     * so removing every account must not silently delete them.
     */
    fun isRetainedKeyMaterial(key: String): Boolean =
        key == LEGACY_ADDRESS ||
            encryptedCredentialPrefixes.any { prefix -> key.startsWith(prefix) } ||
            (key.length > WATCH_ONLY.length && key.startsWith(WATCH_ONLY))

    /**
     * Names the key sets that "Remove all accounts" left on this phone. Removing every account
     * also removes the app PIN, so nothing the previous owner knew protects those key sets any
     * more: they can be brought back only by entering their own recovery phrase, raw seed or
     * backup file (which adopts the stored keys unchanged), never by "Restore this wallet".
     *
     * The value is a comma-separated list of key-set ids: the address a credential key is
     * suffixed with, or [RETAINED_UNSUFFIXED_ID] for the release-era unsuffixed set. It is
     * written in the same atomic edit as the removal. It is not key material and not part of the
     * wallet-state hash; no key value is changed, moved or deleted because of it.
     */
    const val RETAINED_NEEDS_PHRASE = "wallet.retainedNeedsPhrase"

    /** The id of the release-era unsuffixed key set inside [RETAINED_NEEDS_PHRASE]. */
    const val RETAINED_UNSUFFIXED_ID = "unsuffixed"

    private const val RETAINED_ID_SEPARATOR = ","

    fun decodeRetainedNeedsPhrase(value: String?): Set<String> =
        value.orEmpty().split(RETAINED_ID_SEPARATOR).filter(String::isNotBlank).toSet()

    fun encodeRetainedNeedsPhrase(ids: Set<String>): String =
        ids.filter(String::isNotBlank).sorted().joinToString(RETAINED_ID_SEPARATOR)

    /**
     * The id of the key set a retained credential key belongs to, or null when [key] is not an
     * encrypted credential (for example `prefs_address_pure` or a watch-only marker, which hold
     * no secret).
     */
    fun retainedKeySetId(key: String): String? {
        val prefix = encryptedCredentialPrefixes.firstOrNull(key::startsWith) ?: return null
        return key.removePrefix(prefix).ifEmpty { RETAINED_UNSUFFIXED_ID }
    }

    /** What may remain in the preferences after "Remove all accounts". */
    fun isKeptByRemoveAll(key: String): Boolean =
        isRetainedKeyMaterial(key) || key == RETAINED_NEEDS_PHRASE

    fun isWalletStateKey(key: String): Boolean =
        key in generalEvidenceKeys ||
            key in legacyUnsuffixedStringKeys ||
            scopedStatePrefixes.any { prefix ->
                key.length > prefix.length && key.startsWith(prefix)
            }
}
