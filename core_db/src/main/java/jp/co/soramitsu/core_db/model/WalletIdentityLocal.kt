package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "walletIdentities",
    primaryKeys = ["walletId"],
    foreignKeys = [
        ForeignKey(
            entity = SoraAccountLocal::class,
            parentColumns = ["substrateAddress"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        )
    ],
    indices = [Index("walletId", unique = true)],
)
data class WalletIdentityLocal(
    val walletId: String,
    val displayName: String,
    val secretSource: String,
    val migrationState: String,
    val derivationVersion: Int,
)
