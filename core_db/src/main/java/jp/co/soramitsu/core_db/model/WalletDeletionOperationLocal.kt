package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "walletDeletionOperations",
    indices = [
        Index(value = ["activeSlot"], unique = true),
        Index(value = ["operationId"], unique = true),
    ],
)
data class WalletDeletionOperationLocal(
    @PrimaryKey val operationId: String,
    val activeSlot: Int,
    val formatVersion: Int,
    val scope: String,
    val phase: String,
    val expectedWalletCount: Int,
    val targetCount: Int,
    val selectedBefore: String,
    val selectedAfter: String,
    val beforeSnapshotHash: String,
    val afterSnapshotHash: String,
    val beforePreferencesHash: String,
    val afterPreferencesHash: String,
    val requestDigest: String,
    val removeLegacyUnsuffixed: Boolean,
    val requestedAt: Long,
    val updatedAt: Long,
    val failureCode: String?,
)

object WalletDeletionContract {
    const val ACTIVE_SLOT = 1
    const val FORMAT_VERSION = 1
    // Keep every journal query below Android SQLite's conservative 999 bind-variable limit.
    const val MAX_TARGET_WALLETS = 900
    const val SCOPE_SINGLE = "SINGLE"
    const val SCOPE_ALL = "ALL"
    const val PHASE_CONFIRMED = "CONFIRMED"
    const val PHASE_DATABASE_COMMITTED = "DATABASE_COMMITTED"
    const val PHASE_PREFERENCES_COMMITTED = "PREFERENCES_COMMITTED"
}
