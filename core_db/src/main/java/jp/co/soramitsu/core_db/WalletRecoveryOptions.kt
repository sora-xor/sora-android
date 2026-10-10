package jp.co.soramitsu.core_db

/**
 * Wallet key material found on the device that no account row owns.
 *
 * Nothing listed here is deleted, rewritten or moved: it is ignored for the active wallet set and
 * kept byte-identical so its owner can adopt it later (for example by importing the matching
 * recovery phrase, which re-uses the stored keys after proving they derive the same address).
 * Only database sidecars without their database and invalid backup directories are renamed aside
 * ([setAsidePaths]); renaming keeps every byte.
 */
data class WalletQuarantine(
    /** Address-scoped key sets or watch-only markers with no account row. */
    val unlinkedWalletIds: Set<String> = emptySet(),
    /** Addresses whose private key, public key and nonce are not all present. */
    val incompleteKeySetWalletIds: Set<String> = emptySet(),
    /** A release-era `prefs_address_pure` value that names no account row. */
    val unlinkedLegacyOwner: String? = null,
    /** Release-era unsuffixed keys whose owner is not a recorded account row. */
    val unscopedMaterialWithoutRecordedOwner: Boolean = false,
    /**
     * Release-era unsuffixed keys whose owner could not be verified (the stored phrase, keys or
     * recorded address do not agree), so no account was made for them. Their phrase can still be
     * shown (if it decrypts) or imported. Not adoptable as they are.
     */
    val unrestorableLegacyOwner: String? = null,
    /** A selected account (`cur_account_address`) that is not an account row. */
    val staleSelection: String? = null,
    /** Account rows with a blank or invalid address. They are ignored, never deleted. */
    val blankAccountRows: Int = 0,
    /** The database is missing but earlier upgrade backups remain on the device. */
    val retainedBackupWithoutDatabase: Boolean = false,
    /** The database is missing but the wrapped wallet key remains on the device. */
    val retainedWrappedKeyWithoutDatabase: Boolean = false,
    /** Files or directories renamed aside (same filesystem) during this preparation. */
    val setAsidePaths: List<String> = emptyList(),
) {
    /**
     * Addresses whose complete keys are on this device without an account, for "Restore wallets
     * found on this phone" ([jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository]
     * `adoptRetainedWallet`). Includes a release-era owner whose unsuffixed keys remain; adoption
     * proves they derive the address before anything is added. Incomplete key sets are excluded:
     * they can only be completed by importing their recovery phrase.
     */
    val retainedWalletIds: Set<String>
        get() = (unlinkedWalletIds - incompleteKeySetWalletIds) + listOfNotNull(unlinkedLegacyOwner)

    /** Any wallet key material is kept on this device that no account uses. */
    val hasRetainedMaterial: Boolean
        get() = unlinkedWalletIds.isNotEmpty() ||
            incompleteKeySetWalletIds.isNotEmpty() ||
            unlinkedLegacyOwner != null ||
            unscopedMaterialWithoutRecordedOwner ||
            unrestorableLegacyOwner != null ||
            retainedWrappedKeyWithoutDatabase

    val isEmpty: Boolean
        get() = this == NONE

    companion object {
        val NONE = WalletQuarantine()
    }
}

/** Facts gathered during startup preparation that decide which recovery paths are offered. */
data class RecoveryFacts(
    val hasWalletFiles: Boolean = false,
    val hasRecoveryPhraseOrSeed: Boolean = false,
    val databaseAtCurrentVersion: Boolean = false,
    val hasMigrationRecoverySnapshot: Boolean = false,
    /**
     * The installed database can be opened read-only right now: it is at the current schema,
     * passes `PRAGMA quick_check`, its wallet tables can be read, and it holds at least one
     * account. Computed with the same checks as `WalletUpgradeBackup.authorizeSafeReadOnly`.
     */
    val accountsReadableReadOnly: Boolean = false,
    /**
     * The wallet key cannot be used right now (the Keystore key is missing, or the Keystore is
     * busy). Anything that decrypts, including the app PIN check on the main screen, fails, so
     * read-only browsing is not offered until a retry succeeds.
     */
    val keyMaterialUnavailable: Boolean = false,
)

/**
 * What "Set up this device again (keeps old data)" renames aside at the next app start. Nothing
 * is ever deleted and the Android Keystore key is never touched.
 */
enum class SetUpAgainScope {
    /** Not offered: the state may pass on its own (retry) or needs free space. */
    NONE,

    /**
     * Only the database and its sidecar files. The database holds no secrets. The encrypted
     * preferences and the wrapped key stay in place, so every stored key set stays on the device:
     * it is listed as a retained wallet, importing its phrase re-uses it after proving it matches,
     * and "Restore wallets found on this phone" adopts it.
     */
    DATABASE_ONLY,

    /**
     * The database, the encrypted preference files and the wrapped-key file. Used only when this
     * phone cannot use the stored keys at all (the Keystore key is missing) or the preference
     * files cannot be read. The files are kept in `no_backup/wallet-quarantine-*-set-up-again/`;
     * the app cannot read keys from there, so the UI asks the person to write down every phrase
     * it can still show first.
     */
    ALL_WALLET_FILES,
}

enum class WalletRecoveryKind {
    /** May pass on its own: busy Keystore, I/O, a file changed while it was read. */
    TRANSIENT,

    /** Not enough free storage to make the one-time upgrade backup. */
    STORAGE,

    /** Wallet verification was interrupted or failed and can be run again. */
    MIGRATION,

    /** This phone cannot unlock the stored keys (for example the Keystore key is missing). */
    KEYS_UNAVAILABLE,

    /** Stored data does not match what the app expects. */
    INTEGRITY,
}

/**
 * The forward paths for a blocking or degraded wallet state. Nothing here deletes data.
 *
 * Each flag is backed by an action that works in that state:
 * - [canRetry]: `WalletUpgradeBackup.retry` (always available).
 * - [canReadOnly]: `WalletUpgradeBackup.authorizeSafeReadOnly` returns `ROOM_READ_ONLY` and the
 *   main screen can open (its PIN check decrypts, so never while the wallet key is unavailable).
 * - [canShowPhraseAfterPin]: a stored phrase or seed exists and the Keystore is expected to
 *   unlock it (not offered when this phone cannot unlock the stored keys).
 * - [canSetUpAgain]: `WalletUpgradeBackup.requestSetUpAgain` followed by an app restart. What is
 *   renamed aside is [setUpAgainScope] (kept, never deleted; the Keystore key is untouched), and
 *   the app then starts like a new install. Not offered for transient or storage states.
 * - [canRestoreFromPhrase] / [canRestoreFromDrive]: a restore can complete from this state. While
 *   any recovery state is active, wallet writes are disabled, so the restore runs after
 *   [canSetUpAgain]'s restart ([restoreNeedsSetUpAgain]). For transient and storage states the
 *   forward path is Try again (automatic) or freeing space, so restore is not offered there. The
 *   UI still checks that Google services are available for Drive.
 * - [canExport]: `WalletUpgradeBackup.createRecoveryArchive` for support.
 */
data class WalletRecoveryOptions(
    val code: String,
    val kind: WalletRecoveryKind,
    val canRetry: Boolean,
    /** Retry without user action (with backoff): transient states, and storage on resume. */
    val retryAutomatically: Boolean,
    val canRestoreFromPhrase: Boolean,
    val canRestoreFromDrive: Boolean,
    /** Some recovery phrase or seed is stored and the Keystore may be able to decrypt it. */
    val canShowPhraseAfterPin: Boolean,
    /** Balances and history can be shown read-only (signing disabled). */
    val canReadOnly: Boolean,
    /** An encrypted on-device archive can be exported for support. */
    val canExport: Boolean,
    val canContactSupport: Boolean = true,
    /** "Set up this device again (keeps old data)" is available. */
    val canSetUpAgain: Boolean = true,
    /** A phrase or Drive restore first needs [canSetUpAgain] and a restart. */
    val restoreNeedsSetUpAgain: Boolean = true,
    /** What [canSetUpAgain] sets aside; [SetUpAgainScope.NONE] when it is not offered. */
    val setUpAgainScope: SetUpAgainScope = SetUpAgainScope.DATABASE_ONLY,
    /** For [WalletRecoveryKind.STORAGE]: free bytes needed to continue. */
    val requiredBytes: Long? = null,
    /** For [WalletRecoveryKind.STORAGE]: free bytes found. */
    val availableBytes: Long? = null,
) {
    companion object {
        const val KEYSTORE_TRANSIENT = "KEYSTORE_UNAVAILABLE"
        const val KEYSTORE_KEY_UNAVAILABLE = "KEYSTORE_KEY_UNAVAILABLE"
        const val STORAGE_FULL = "STORAGE_FULL"
        const val STORAGE_IO = "STORAGE_IO"
        const val DATABASE_LOCKED = "DATABASE_LOCKED"
        const val PREFERENCES_INVENTORY_INVALID = "PREFERENCES_INVENTORY_INVALID"

        /** Codes meaning the wallet key cannot be used right now; nothing can be decrypted. */
        private val keyMaterialUnavailableCodes = setOf(
            KEYSTORE_TRANSIENT,
            KEYSTORE_KEY_UNAVAILABLE,
        )

        /** Integrity codes caused by the preference files themselves, not by the database. */
        private val preferenceFileCodes = setOf(PREFERENCES_INVENTORY_INVALID)

        fun isKeyMaterialUnavailable(code: String?): Boolean =
            code != null && code in keyMaterialUnavailableCodes

        /**
         * Set-up-again moves key material only when it cannot be used here anyway: the Keystore
         * key is missing, or the preference files cannot be read. Every other database or
         * verification problem sets aside the database alone, so retained keys stay adoptable.
         */
        fun setUpAgainScopeFor(code: String, kind: WalletRecoveryKind): SetUpAgainScope =
            when (kind) {
                WalletRecoveryKind.TRANSIENT,
                WalletRecoveryKind.STORAGE -> SetUpAgainScope.NONE
                WalletRecoveryKind.KEYS_UNAVAILABLE -> SetUpAgainScope.ALL_WALLET_FILES
                WalletRecoveryKind.INTEGRITY ->
                    if (code in preferenceFileCodes) {
                        SetUpAgainScope.ALL_WALLET_FILES
                    } else {
                        SetUpAgainScope.DATABASE_ONLY
                    }
                WalletRecoveryKind.MIGRATION -> SetUpAgainScope.DATABASE_ONLY
            }

        private val transientCodes = setOf(
            KEYSTORE_TRANSIENT,
            STORAGE_IO,
            DATABASE_LOCKED,
            "IO",
            "PERMISSION",
            "SOURCE_CHANGED",
            "RECOVERY_SOURCE_CHANGED",
            "BACKUP_PUBLICATION_LOCK_FAILED",
            "STORAGE_CAPACITY_UNAVAILABLE",
            "CREATE_STAGING",
            "CREATE_BACKUP_FILE",
            "CREATE_BACKUP_DIRECTORY",
            "SIZE_MISMATCH",
            "HASH_MISMATCH",
            "BACKUP_SOURCE_INVALID",
            "DATABASE_SIDECAR_QUARANTINE_FAILED",
            "DATABASE_PATH_UNREADABLE",
            "SET_UP_AGAIN_INCOMPLETE",
        )

        fun kindOf(code: String, inMemoryOnly: Boolean = false): WalletRecoveryKind = when {
            code == "INSUFFICIENT_BACKUP_STORAGE" || code == STORAGE_FULL ->
                WalletRecoveryKind.STORAGE
            code == KEYSTORE_KEY_UNAVAILABLE -> WalletRecoveryKind.KEYS_UNAVAILABLE
            code in transientCodes -> WalletRecoveryKind.TRANSIENT
            code.startsWith("WALLET_MIGRATION_") || code.startsWith("MIGRATION_") ->
                WalletRecoveryKind.MIGRATION
            inMemoryOnly -> WalletRecoveryKind.TRANSIENT
            else -> WalletRecoveryKind.INTEGRITY
        }

        fun forState(
            code: String,
            transientFailure: Boolean,
            facts: RecoveryFacts,
            requiredBytes: Long? = null,
            availableBytes: Long? = null,
        ): WalletRecoveryOptions {
            val kind = kindOf(code, inMemoryOnly = transientFailure)
            val setUpAgainScope = setUpAgainScopeFor(code, kind)
            val canSetUpAgain = setUpAgainScope != SetUpAgainScope.NONE
            val keyMaterialUnavailable =
                isKeyMaterialUnavailable(code) || facts.keyMaterialUnavailable
            return WalletRecoveryOptions(
                code = code,
                kind = kind,
                canRetry = true,
                retryAutomatically = kind == WalletRecoveryKind.TRANSIENT ||
                    kind == WalletRecoveryKind.STORAGE,
                // Writes are disabled in every recovery state, so a restore runs after "Set up
                // this device again" (which keeps the old files aside) and a restart.
                canRestoreFromPhrase = canSetUpAgain,
                canRestoreFromDrive = canSetUpAgain,
                canShowPhraseAfterPin = facts.hasRecoveryPhraseOrSeed &&
                    kind != WalletRecoveryKind.KEYS_UNAVAILABLE,
                // The main screen decrypts the app PIN as soon as it opens, so read-only needs a
                // usable wallet key as well as a readable database.
                canReadOnly = !keyMaterialUnavailable &&
                    (facts.hasMigrationRecoverySnapshot || facts.accountsReadableReadOnly),
                canExport = facts.hasWalletFiles,
                canSetUpAgain = canSetUpAgain,
                restoreNeedsSetUpAgain = canSetUpAgain,
                setUpAgainScope = setUpAgainScope,
                requiredBytes = requiredBytes,
                availableBytes = availableBytes,
            )
        }
    }
}
