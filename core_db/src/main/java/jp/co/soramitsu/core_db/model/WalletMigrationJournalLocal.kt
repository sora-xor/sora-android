package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "walletMigrationJournal")
data class WalletMigrationJournalLocal(
    @PrimaryKey val migrationId: String,
    val state: String,
    val legacyAccountCount: Int,
    val verifiedAccountCount: Int,
    val selectedWalletId: String,
    val integrityHash: String,
    val failureCode: String?,
    val startedAt: Long,
    val completedAt: Long?,
)

object WalletMigrationIds {
    const val NETWORK_ACCOUNTS_V1 = "wallet-network-v1"
}
