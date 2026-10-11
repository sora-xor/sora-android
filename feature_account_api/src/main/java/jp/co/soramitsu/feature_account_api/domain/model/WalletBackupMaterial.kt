package jp.co.soramitsu.feature_account_api.domain.model

/**
 * What can be shown, exported or backed up for one account. A stored recovery phrase or seed is
 * offered only when it re-derives the account's address; anything else stored on the phone for
 * that account is never presented as its backup.
 */
class WalletBackupMaterial(
    /** The account's stored signing key signs for its address. */
    val canSign: Boolean,
    /** The words of the stored phrase when it re-derives this account; otherwise empty. */
    val phraseWords: List<String>,
    /**
     * The 32-byte seed (hex, no prefix) that re-derives this account, from the stored seed or the
     * matching phrase; otherwise empty.
     */
    val seedHex: String,
    /** A phrase is stored for this account but it is unreadable or derives another wallet. */
    val storedPhraseDoesNotMatch: Boolean,
    /** A seed is stored for this account but it is unreadable or derives another wallet. */
    val storedSeedDoesNotMatch: Boolean,
) {
    val hasPhrase: Boolean get() = phraseWords.isNotEmpty()
    val hasSeed: Boolean get() = seedHex.isNotEmpty()

    /**
     * Neither the stored phrase nor the stored seed re-derives this account. The encrypted JSON
     * export (made from the stored key itself) is its backup.
     */
    val onlyJsonExport: Boolean get() = !hasPhrase && !hasSeed
}

/**
 * Importing a recovery phrase or seed found values stored for its address that differ from it or
 * cannot be read, and the stored signing key does not prove the address. Nothing was written.
 * The way forward keeps the account: "Repair this account with its recovery phrase", confirmed by
 * the person (with the PIN when one is set), keeps a copy of the old values on the device and
 * writes the proven ones.
 */
class WalletRepairAvailableException(
    val address: String,
    /** An account with this address is listed (read-only); otherwise only its keys are stored. */
    val accountExists: Boolean,
    /** A PIN is set on this phone and must be entered to confirm the repair. */
    val pinRequired: Boolean,
) : Exception("WALLET_REPAIR_AVAILABLE")
