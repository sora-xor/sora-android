package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pendingNetworkTransactions",
    foreignKeys = [
        ForeignKey(
            entity = WalletIdentityLocal::class,
            parentColumns = ["walletId"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        )
    ],
    indices = [
        Index("walletId"),
        Index(value = ["networkId", "chainId", "transactionHash"], unique = true),
    ],
)
data class PendingNetworkTransactionLocal(
    @PrimaryKey val localId: String,
    val walletId: String,
    val networkId: String,
    /**
     * Exact chain UUID/genesis hash at creation; null only for retained pre-v77 rows. Taira rows
     * additionally carry the admitted deployment-manifest digest in [localId], because a reviewed
     * epoch mapping may reuse either known UUID.
     */
    val chainId: String?,
    val transactionHash: String?,
    val assetId: String,
    val amount: String,
    val recipient: String,
    val state: String,
    val submissionIsAmbiguous: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
