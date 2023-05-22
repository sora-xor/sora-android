/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.model

import androidx.room.Embedded

data class TokenWithFiatLocal(
    @Embedded
    val token: TokenLocal,
    @Embedded
    val price: FiatTokenPriceLocal?,
)

data class AssetTokenWithFiatLocal(
    @Embedded
    val token: TokenLocal,
    @Embedded
    val price: FiatTokenPriceLocal?,
    @Embedded
    val assetLocal: AssetLocal?,
)

data class BasePoolWithTokenLocal(
    @Embedded
    val base: PoolBaseTokenLocal,
    @Embedded
    val token: TokenLocal,
)
