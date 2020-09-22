/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import jp.co.soramitsu.core_db.converters.BigDecimalConverter
import jp.co.soramitsu.core_db.converters.ReferendumStatusLocalConverter
import java.math.BigDecimal

@Entity(tableName = "referendums")
@TypeConverters(ReferendumStatusLocalConverter::class, BigDecimalConverter::class)
data class ReferendumLocal(
    val description: String,
    val detailedDescription: String,
    val fundingDeadline: Long,
    @PrimaryKey val id: String,
    val imageLink: String,
    val name: String,
    val status: ReferendumStatusLocal,
    val statusUpdateTime: Long,
    val opposeVotes: BigDecimal,
    val supportVotes: BigDecimal,
    val userOpposeVotes: BigDecimal,
    val userSupportVotes: BigDecimal
)

enum class ReferendumStatusLocal {
    CREATED,
    ACCEPTED,
    REJECTED
}