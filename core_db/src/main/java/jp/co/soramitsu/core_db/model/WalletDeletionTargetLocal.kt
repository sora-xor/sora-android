package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "walletDeletionTargets",
    primaryKeys = ["operationId", "walletId"],
    foreignKeys = [
        ForeignKey(
            entity = WalletDeletionOperationLocal::class,
            parentColumns = ["operationId"],
            childColumns = ["operationId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        )
    ],
    indices = [Index("operationId")],
)
data class WalletDeletionTargetLocal(
    val operationId: String,
    val walletId: String,
)
