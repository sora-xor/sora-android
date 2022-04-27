/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.network.response

import androidx.annotation.Keep
import java.math.BigDecimal

@Keep
data class PoolsInfoResponse(val data: PoolsInfoResponsePoolXykEntity)

@Keep
data class PoolsInfoResponsePoolXykEntity(val poolXYKEntities: PoolsInfoResponseNodes)

@Keep
data class PoolsInfoResponseNodes(val nodes: List<PoolsInfoResponseNodesElement>)

@Keep
data class PoolsInfoResponseNodesElement(val pools: PoolsInfoResponsePools)

@Keep
data class PoolsInfoResponsePools(val edges: List<PoolsInfoResponseDataElement>)

@Keep
data class PoolsInfoResponseDataElement(val node: PoolsInfoResponseData)

@Keep
data class PoolsInfoResponseData(
    val targetAssetId: String,
    val priceUSD: BigDecimal,
    val strategicBonusApy: BigDecimal
)
