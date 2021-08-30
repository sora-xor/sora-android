/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import jp.co.soramitsu.core_db.converters.ExtrinsicStatusConverter
import jp.co.soramitsu.core_db.converters.ExtrinsicTypeConverter
import java.math.BigDecimal

@Entity(
    tableName = "extrinsic_params",
    primaryKeys = ["extrinsicId", "paramName"],
    foreignKeys = [
        ForeignKey(
            entity = ExtrinsicLocal::class,
            parentColumns = ["txHash"],
            childColumns = ["extrinsicId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        )
    ]
)
data class ExtrinsicParamLocal(
    val extrinsicId: String,
    val paramName: String,
    val paramValue: String,
)

@Entity(tableName = "extrinsics")
@TypeConverters(ExtrinsicStatusConverter::class, ExtrinsicTypeConverter::class)
data class ExtrinsicLocal(
    @PrimaryKey val txHash: String,
    val blockHash: String?,
    val fee: BigDecimal,
    val status: ExtrinsicStatus,
    val timestamp: Long,
    val type: ExtrinsicType,
    val eventSuccess: Boolean?,
    val localPending: Boolean,
)

enum class ExtrinsicStatus {
    PENDING,
    COMMITTED,
    REJECTED
}

enum class ExtrinsicType {
    TRANSFER,
    SWAP,
}

enum class ExtrinsicParam(val paramName: String) {
    PEER("peerId"),
    TOKEN("tokenId"),
    TOKEN2("token2Id"),
    AMOUNT("amount"),
    AMOUNT2("amount2"),
    AMOUNT3("amount3"),
    TRANSFER_TYPE("transferType"),
    SWAP_MARKET("swapMarket")
}

enum class ExtrinsicTransferTypes {
    OUT, IN
}
