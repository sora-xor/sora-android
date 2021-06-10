/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.model

import java.math.BigDecimal

data class Asset(
    val id: String,
    val assetName: String,
    val symbol: String,
    val display: Boolean,
    val hidingAllowed: Boolean,
    val position: Int,
    val roundingPrecision: Int,
    val precision: Int,
    var balance: BigDecimal,
    val iconShadow: Int = 0,
    val isMintable: Boolean = false,
)
