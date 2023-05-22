/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_api.data.models

import java.math.BigDecimal

data class XorAssetBalance(
    val transferable: BigDecimal,
    val frozen: BigDecimal,
    val totalBalance: BigDecimal,
    val locked: BigDecimal,
    val bonded: BigDecimal,
    val reserved: BigDecimal,
    val redeemable: BigDecimal,
    val unbonding: BigDecimal
)
