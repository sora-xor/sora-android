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
import java.math.BigDecimal

@Entity(tableName = "transfer_transactions")
@TypeConverters(TransactionStatusConverter::class, TransactionTypeConverter::class)
data class TransferTransactionLocal(
    @PrimaryKey val txHash: String,
    val status: Status,
    val assetId: String,
    val myAddress: String,
    val amount: BigDecimal,
    val timestamp: Long,
    val peerId: String,
    val type: Type,
    val fee: BigDecimal,
    val blockHash: String?,
    val eventSuccess: Boolean?,
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
        REWARD,
        DEPOSIT
    }
}
