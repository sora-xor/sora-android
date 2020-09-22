/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import jp.co.soramitsu.core_db.model.DepositTransactionLocal

class DepositTransactionStatusConverter {

    @TypeConverter
    fun fromType(type: DepositTransactionLocal.Status): Int {
        return when (type) {
            DepositTransactionLocal.Status.DEPOSIT_PENDING -> 0
            DepositTransactionLocal.Status.DEPOSIT_FAILED -> 1
            DepositTransactionLocal.Status.DEPOSIT_COMPLETED -> 2
            DepositTransactionLocal.Status.DEPOSIT_RECEIVED -> 3
            DepositTransactionLocal.Status.TRANSFER_PENDING -> 4
            DepositTransactionLocal.Status.TRANSFER_FAILED -> 5
            DepositTransactionLocal.Status.TRANSFER_COMPLETED -> 6
        }
    }

    @TypeConverter
    fun toType(state: Int): DepositTransactionLocal.Status {
        return when (state) {
            0 -> DepositTransactionLocal.Status.DEPOSIT_PENDING
            1 -> DepositTransactionLocal.Status.DEPOSIT_FAILED
            2 -> DepositTransactionLocal.Status.DEPOSIT_COMPLETED
            3 -> DepositTransactionLocal.Status.DEPOSIT_RECEIVED
            4 -> DepositTransactionLocal.Status.TRANSFER_PENDING
            5 -> DepositTransactionLocal.Status.TRANSFER_FAILED
            else -> DepositTransactionLocal.Status.TRANSFER_COMPLETED
        }
    }
}