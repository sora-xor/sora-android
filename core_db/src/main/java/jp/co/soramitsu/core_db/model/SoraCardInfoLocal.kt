/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "soraCard"
)
data class SoraCardInfoLocal(
    @PrimaryKey val id: String,
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpirationTime: Long,
    val kycStatus: String
)
