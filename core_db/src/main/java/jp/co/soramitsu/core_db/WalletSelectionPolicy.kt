package jp.co.soramitsu.core_db

import jp.co.soramitsu.core_db.model.WalletMigrationIds

/**
 * Chooses the selected account when the stored selection is empty or names no account row (for
 * example after an interrupted deletion in an older release). Deterministic: the release-era owner
 * when it is an account, otherwise the lowest address. Never creates or removes an account.
 */
object WalletSelectionPolicy {

    fun fallback(walletIds: Collection<String>, legacyOwner: String?): String? {
        val rows = walletIds.filter(String::isNotBlank)
        if (rows.isEmpty()) return null
        if (!legacyOwner.isNullOrBlank() && legacyOwner in rows) return legacyOwner
        return rows.minOrNull()
    }
}

/**
 * The single rule every SORA2 signer applies before using a key: the account row exists, its
 * identity is verified, wallet verification is not pending, no deletion is in progress and its
 * SORA2 network account is enabled for the same address. A wallet whose keys are unavailable is
 * read-only for that wallet only.
 */
object WalletSigningGate {

    suspend fun isAvailable(database: AppDatabase, walletId: String): Boolean =
        unavailableReason(database, walletId) == null

    /** Null when [walletId] may sign; otherwise the reason it may not. */
    suspend fun unavailableReason(database: AppDatabase, walletId: String): String? {
        val walletDao = database.walletIdentityDao()
        if (walletDao.hasActiveDeletionOperation()) return "WALLET_DELETION_ACTIVE"
        val migration = walletDao.getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1)
        val sora2 = walletDao.getNetworkAccount(walletId, "sora2")
        val available = database.accountDao().getAccount(walletId) != null &&
            walletDao.getWallet(walletId)?.migrationState == "VERIFIED" &&
            (migration == null || migration.state == "VERIFIED") &&
            sora2?.let { account -> account.enabled && account.address == walletId } == true
        return if (available) null else "SIGNING_WALLET_UNAVAILABLE"
    }
}
