/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import jp.co.soramitsu.core_db.converters.AmountConverter
import jp.co.soramitsu.core_db.converters.DepositTransactionStatusConverter
import java.math.BigDecimal

@Entity(tableName = "deposit_transactions")
@TypeConverters(DepositTransactionStatusConverter::class, AmountConverter::class)
data class DepositTransactionLocal(
    @PrimaryKey val depositTxHash: String,
    val transferTxHash: String,
    val status: Status,
    val assetId: String?,
    val details: String,
    val peerName: String,
    val partialAmount: BigDecimal,
    val amount: BigDecimal,
    val timestamp: Long,
    val peerId: String?,
    val reason: String?,
    val depositFee: BigDecimal,
    val transferFee: BigDecimal
) {
    enum class Status {
        DEPOSIT_PENDING,
        DEPOSIT_FAILED,
        DEPOSIT_COMPLETED,
        DEPOSIT_RECEIVED,
        TRANSFER_PENDING,
        TRANSFER_FAILED,
        TRANSFER_COMPLETED
    }
}