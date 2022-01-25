/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.pool.model

data class PoolModel(
    val token1Name: String,
    val token1IconResource: Int,
    val token2Name: String,
    val token2IconResource: Int,
    val token1Pooled: String,
    val token2Pooled: String,
    val poolShare: String
)
