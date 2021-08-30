/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.common.util.ext.unsafeCast
import jp.co.soramitsu.core_db.model.ExtrinsicLocal
import jp.co.soramitsu.core_db.model.ExtrinsicParam
import jp.co.soramitsu.core_db.model.ExtrinsicParamLocal
import jp.co.soramitsu.core_db.model.ExtrinsicStatus
import jp.co.soramitsu.core_db.model.ExtrinsicType
import jp.co.soramitsu.feature_wallet_api.domain.model.WithDesired
import jp.co.soramitsu.feature_wallet_impl.data.network.response.ExtrinsicRemote
import jp.co.soramitsu.feature_wallet_impl.data.network.response.ExtrinsicSwapResponse
import java.math.BigDecimal
import kotlin.math.roundToLong

object SwapRemoteMapper {

    private fun <T> getParam(r: ExtrinsicSwapResponse, paramName: String): T {
        return requireNotNull(
            r.data.attributes.params.find { p -> p.name == paramName },
            { "[$paramName] param not found in mapping" }
        ).value.unsafeCast()
    }

    fun mapSwapRemoteToLocal(
        txs: List<Pair<ExtrinsicRemote, ExtrinsicSwapResponse>>,
        tokens: List<Token>,
    ): Pair<List<ExtrinsicLocal>, List<ExtrinsicParamLocal>> {
        val params = mutableListOf<ExtrinsicParamLocal>()
        val feeToken = tokens.first { it.id == OptionsProvider.feeAssetId }
        val extrinsics = txs.map {
            val txHash = it.first.attributes.extrinsic_hash
            val fromTokenId = getParam<String>(it.second, "input_asset_id")
            val toTokenId = getParam<String>(it.second, "output_asset_id")
            val fromToken = tokens.first { t -> t.id == fromTokenId }
            val toToken = tokens.first { t -> t.id == toTokenId }
            // val filter = getParam<String>(it.second, "filter_mode")
            val market = getParam<List<String>>(it.second, "selected_source_types")
            val amount = getParam<Any>(it.second, "swap_amount")
            val desiredInput =
                amount.safeCast<Map<String, Any>>()?.get(WithDesired.INPUT.backString)
            val desiredOutput =
                amount.safeCast<Map<String, Any>>()?.get(WithDesired.OUTPUT.backString)
            val amounts = when {
                desiredInput != null -> {
                    val map = desiredInput.safeCast<Map<String, Double>>()
                    if (map != null) {
                        val amount1: BigDecimal =
                            map["min_amount_out"]?.toBigDecimal() ?: BigDecimal.ZERO
                        val amount2: BigDecimal =
                            map["desired_amount_in"]?.toBigDecimal() ?: BigDecimal.ZERO
                        amount2 to amount1
                    } else {
                        BigDecimal.ZERO to BigDecimal.ZERO
                    }
                }
                desiredOutput != null -> {
                    val map = desiredOutput.safeCast<Map<String, Double>>()
                    if (map != null) {
                        val amount1: BigDecimal =
                            map["max_amount_in"]?.toBigDecimal() ?: BigDecimal.ZERO
                        val amount2: BigDecimal =
                            map["desired_amount_out"]?.toBigDecimal() ?: BigDecimal.ZERO
                        amount1 to amount2
                    } else {
                        BigDecimal.ZERO to BigDecimal.ZERO
                    }
                }
                else -> BigDecimal.ZERO to BigDecimal.ZERO
            }
            params.add(
                ExtrinsicParamLocal(
                    txHash,
                    ExtrinsicParam.TOKEN.paramName,
                    fromTokenId
                )
            )
            params.add(
                ExtrinsicParamLocal(
                    txHash,
                    ExtrinsicParam.TOKEN2.paramName,
                    toTokenId
                )
            )
            params.add(
                ExtrinsicParamLocal(
                    txHash,
                    ExtrinsicParam.AMOUNT.paramName,
                    mapBalance(amounts.first.toBigInteger(), fromToken.precision).toString()
                )
            )
            params.add(
                ExtrinsicParamLocal(
                    txHash,
                    ExtrinsicParam.AMOUNT2.paramName,
                    mapBalance(amounts.second.toBigInteger(), toToken.precision).toString()
                )
            )
            params.add(
                ExtrinsicParamLocal(
                    txHash,
                    ExtrinsicParam.AMOUNT3.paramName,
                    BigDecimal.ZERO.toString()
                )
            )
            params.add(
                ExtrinsicParamLocal(
                    txHash,
                    ExtrinsicParam.SWAP_MARKET.paramName,
                    if (market.isEmpty()) "" else market[0]
                )
            )
            ExtrinsicLocal(
                txHash,
                it.first.attributes.block_hash,
                mapBalance(it.first.attributes.fee, feeToken.precision),
                ExtrinsicStatus.COMMITTED,
                it.first.attributes.transaction_timestamp.roundToLong() * 1000,
                ExtrinsicType.SWAP,
                it.first.attributes.success == 1,
                false
            )
        }
        return extrinsics to params
    }
}
