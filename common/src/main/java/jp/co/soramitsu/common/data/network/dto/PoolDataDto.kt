/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.network.dto

import java.math.BigInteger

data class PoolDataDto(
    val baseAssetId: String,
    val assetId: String,
    val reservesFirst: BigInteger,
    val reservesSecond: BigInteger,
    val totalIssuance: BigInteger,
    val poolProvidersBalance: BigInteger
)
