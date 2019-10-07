/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import jp.co.soramitsu.core_db.converters.ProjectVotesConverter
import java.math.BigDecimal

@Entity(tableName = "votes_history")
@TypeConverters(ProjectVotesConverter::class)
data class VotesHistoryLocal(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val message: String,
    val timestamp: String,
    val votes: BigDecimal
)