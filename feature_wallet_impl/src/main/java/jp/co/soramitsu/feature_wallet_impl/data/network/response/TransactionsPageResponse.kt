/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.network.response

import java.math.BigDecimal
import java.math.BigInteger

data class TransactionsPageResponse(val errors: List<Any>, val data: List<TransactionRemote>)

data class TransactionRemote(val attributes: TransactionRemoteAttributes, val id: String)

data class TransactionRemoteAttributes(
    val block_hash: String,
    val transaction_hash: String,
    val transaction_timestamp: Double,
    val value: BigDecimal,
    val fee: BigDecimal,
    val assetId: String,
    val sender: TransactionRemoteAttributesPeer,
    val destination: TransactionRemoteAttributesPeer,
)

data class TransactionRemoteAttributesPeer(val attributes: TransactionRemoteAttributesData)

data class TransactionRemoteAttributesData(val address: String)

data class ExtrinsicsPageResponse(val errors: List<Any>, val data: List<ExtrinsicRemote>)

data class ExtrinsicRemote(val attributes: ExtrinsicRemoteAttributes, val id: String)

data class ExtrinsicRemoteAttributes(
    val extrinsic_hash: String,
    val block_hash: String,
    val success: Int,
    val error: Int,
    val params: List<ExtrinsicRemoteAttributeParams>,
    val transaction_timestamp: Double,
    val fee: BigInteger,
)

data class ExtrinsicRemoteAttributeParams(
    val name: String,
    val value: Any,
)

data class ExtrinsicSwapResponse(val errors: List<Any>, val data: ExtrinsicSwapDetails)

data class ExtrinsicSwapDetails(val attributes: ExtrinsicSwapDetailsAttr)

data class ExtrinsicSwapDetailsAttr(val params: List<ExtrinsicRemoteAttributeParams>)
