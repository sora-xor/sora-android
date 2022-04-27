/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "accounts",
)
data class SoraAccountLocal(
    @PrimaryKey val substrateAddress: String,
    val accountName: String,
)
