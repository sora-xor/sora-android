/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.model

import androidx.room.Embedded

data class AssetTokenLocal(
    @Embedded
    val assetLocal: AssetLocal,
    @Embedded
    val tokenLocal: TokenLocal,
)

data class AssetNTokenLocal(
    @Embedded
    val assetLocal: AssetLocal?,
    @Embedded
    val tokenLocal: TokenLocal,
)
