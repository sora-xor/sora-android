package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "networkAccounts",
    primaryKeys = ["walletId", "networkId"],
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
        Index(value = ["networkId", "address"], unique = true),
    ],
)
data class NetworkAccountLocal(
    val walletId: String,
    val networkId: String,
    val publicKey: String,
    val address: String,
    val derivationPath: String,
    val derivationVersion: Int,
    val enabled: Boolean,
)
