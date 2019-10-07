/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import jp.co.soramitsu.core_db.converters.TransactionStatusConverter
import jp.co.soramitsu.core_db.converters.TransactionTypeConverter

@Entity(tableName = "transactions")
@TypeConverters(TransactionStatusConverter::class, TransactionTypeConverter::class)
data class TransactionLocal(
    @PrimaryKey val transactionId: String,
    val status: Status,
    val assetId: String?,
    val details: String,
    val peerName: String,
    val amount: Double,
    val timestamp: Long,
    val peerId: String?,
    val reason: String?,
    val type: Type,
    val fee: Double
) {
    enum class Status {
        PENDING,
        COMMITTED,
        REJECTED
    }

    enum class Type {
        INCOMING,
        OUTGOING,
        WITHDRAW,
        REWARD
    }
}