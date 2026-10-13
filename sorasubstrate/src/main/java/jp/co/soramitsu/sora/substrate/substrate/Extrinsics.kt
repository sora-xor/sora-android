/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.sora.substrate.substrate

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.dto.PolkamarktMarketId
import jp.co.soramitsu.common_wallet.domain.model.WithDesired
import jp.co.soramitsu.sora.substrate.runtime.Method
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.mapCodeToken
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.xsubstrate.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId

fun ExtrinsicBuilder.setReferrer(referrer: String) =
    this.call(
        Pallete.Referrals.palletName,
        Method.SET_REFERRER.methodName,
        mapOf(
            "referrer" to referrer.toAccountId()
        )
    )

fun ExtrinsicBuilder.swap(
    dexId: Int,
    inputAssetId: String,
    outputAssetId: String,
    amount: BigInteger,
    limit: BigInteger,
    filter: String,
    markets: List<String>,
    desired: WithDesired,
) =
    this.call(
        Pallete.LIQUIDITY_PROXY.palletName,
        Method.SWAP.methodName,
        mapOf(
            "dex_id" to dexId.toBigInteger(),
            "input_asset_id" to inputAssetId.mapCodeToken(),
            "output_asset_id" to outputAssetId.mapCodeToken(),
            "swap_amount" to DictEnum.Entry(
                name = desired.backString,
                value = Struct.Instance(
                    (if (desired == WithDesired.INPUT) "desired_amount_in" to "min_amount_out" else "desired_amount_out" to "max_amount_in").let {
                        mapOf(
                            it.first to amount,
                            it.second to limit,
                        )
                    }
                )
            ),
            "selected_source_types" to markets.map {
                DictEnum.Entry(it, null)
            },
            "filter_mode" to DictEnum.Entry(
                name = filter,
                value = null
            ),
        )
    )

fun ExtrinsicBuilder.referralBond(amount: BigInteger) =
    this.call(
        Pallete.Referrals.palletName,
        Method.RESERVE.methodName,
        mapOf(
            "balance" to amount
        )
    )

fun ExtrinsicBuilder.referralUnbond(amount: BigInteger) =
    this.call(
        Pallete.Referrals.palletName,
        Method.UNRESERVE.methodName,
        mapOf(
            "balance" to amount
        )
    )

fun ExtrinsicBuilder.transfer(
    assetId: String,
    to: String,
    amount: BigInteger
) =
    this.call(
        Pallete.ASSETS.palletName,
        Method.TRANSFER.methodName,
        mapOf(
            "asset_id" to assetId.mapCodeToken(),
            "to" to to.toAccountId(),
            "amount" to amount
        )
    )

fun ExtrinsicBuilder.migrate(
    irohaAddress: String,
    irohaPublicKey: String,
    signature: String
) =
    this.call(
        Pallete.IROHA_MIGRATION.palletName,
        Method.MIGRATE.methodName,
        mapOf(
            "iroha_address" to irohaAddress.toByteArray(charset("UTF-8")),
            "iroha_public_key" to irohaPublicKey.toByteArray(charset("UTF-8")),
            "iroha_signature" to signature.toByteArray(charset("UTF-8"))
        )
    )

fun ExtrinsicBuilder.polkamarktBuy(
    marketId: Long,
    outcome: String,
    collateralIn: BigInteger,
    minimumSharesOut: BigInteger,
) = polkamarktCall(
    PolkamarktRuntimeCallFactory.buy(
        marketId = marketId,
        outcome = outcome,
        collateralIn = collateralIn,
        minimumSharesOut = minimumSharesOut,
    )
)

fun ExtrinsicBuilder.polkamarktSell(
    marketId: Long,
    outcome: String,
    sharesIn: BigInteger,
    minimumCollateralOut: BigInteger,
) = polkamarktCall(
    PolkamarktRuntimeCallFactory.sell(
        marketId = marketId,
        outcome = outcome,
        sharesIn = sharesIn,
        minimumCollateralOut = minimumCollateralOut,
    )
)

fun ExtrinsicBuilder.polkamarktClaimTraderPayout(
    marketId: Long,
) = polkamarktCall(PolkamarktRuntimeCallFactory.claimTraderPayout(marketId))

fun ExtrinsicBuilder.polkamarktClaimTraderPayouts(
    marketIds: List<Long>,
) = polkamarktCall(PolkamarktRuntimeCallFactory.claimTraderPayouts(marketIds))

fun ExtrinsicBuilder.polkamarktClaimCreatorFees(
    marketId: Long,
) = polkamarktCall(PolkamarktRuntimeCallFactory.claimCreatorFees(marketId))

internal data class PolkamarktRuntimeCall(
    val pallet: String,
    val method: String,
    val arguments: Map<String, Any>,
)

internal object PolkamarktRuntimeCallFactory {
    fun buy(
        marketId: Long,
        outcome: String,
        collateralIn: BigInteger,
        minimumSharesOut: BigInteger,
    ) = PolkamarktRuntimeCall(
        pallet = POLKAMARKT_PALLET,
        method = "buy",
        arguments = mapOf(
            "market_id" to PolkamarktMarketId.toScale(marketId),
            "outcome" to polkamarktOutcome(outcome),
            "collateral_in" to collateralIn,
            "min_shares_out" to minimumSharesOut,
        ),
    )

    fun sell(
        marketId: Long,
        outcome: String,
        sharesIn: BigInteger,
        minimumCollateralOut: BigInteger,
    ) = PolkamarktRuntimeCall(
        pallet = POLKAMARKT_PALLET,
        method = "sell",
        arguments = mapOf(
            "market_id" to PolkamarktMarketId.toScale(marketId),
            "outcome" to polkamarktOutcome(outcome),
            "shares_in" to sharesIn,
            "min_collateral_out" to minimumCollateralOut,
        ),
    )

    fun claimTraderPayout(marketId: Long) = PolkamarktRuntimeCall(
        pallet = POLKAMARKT_PALLET,
        method = "claim_market",
        arguments = mapOf("market_id" to PolkamarktMarketId.toScale(marketId)),
    )

    fun claimTraderPayouts(marketIds: List<Long>) = PolkamarktRuntimeCall(
        pallet = POLKAMARKT_PALLET,
        method = "claim_markets",
        arguments = mapOf(
            "market_ids" to marketIds.map { PolkamarktMarketId.toScale(it) }
        ),
    )

    fun claimCreatorFees(marketId: Long) = PolkamarktRuntimeCall(
        pallet = POLKAMARKT_PALLET,
        method = "claim_creator_fees",
        arguments = mapOf("market_id" to PolkamarktMarketId.toScale(marketId)),
    )
}

private fun ExtrinsicBuilder.polkamarktCall(call: PolkamarktRuntimeCall) = this.call(
    call.pallet,
    call.method,
    call.arguments,
)

private fun polkamarktOutcome(value: String): DictEnum.Entry<Any?> {
    require(value == "Yes" || value == "No") { "POLKAMARKT_INVALID_OUTCOME" }
    return DictEnum.Entry<Any?>(value, null)
}

private const val POLKAMARKT_PALLET = "Polkamarkt"

fun ExtrinsicBuilder.removeLiquidity(
    dexId: Int,
    outputAssetIdA: String,
    outputAssetIdB: String,
    markerAssetDesired: BigInteger,
    outputAMin: BigInteger,
    outputBMin: BigInteger
) =
    this.call(
        Pallete.POOL_XYK.palletName,
        Method.WITHDRAW_LIQUIDITY.methodName,
        mapOf(
            "dex_id" to dexId.toBigInteger(),
            "output_asset_a" to outputAssetIdA.mapCodeToken(),
            "output_asset_b" to outputAssetIdB.mapCodeToken(),
            "marker_asset_desired" to markerAssetDesired,
            "output_a_min" to outputAMin,
            "output_b_min" to outputBMin
        )
    )

fun ExtrinsicBuilder.register(
    dexId: Int,
    baseAssetId: String,
    targetAssetId: String
) = this.call(
    Pallete.TRADING_PAIR.palletName,
    Method.REGISTER.methodName,
    mapOf(
        "dex_id" to dexId.toBigInteger(),
        "base_asset_id" to baseAssetId.mapCodeToken(),
        "target_asset_id" to targetAssetId.mapCodeToken(),
    )
)

fun ExtrinsicBuilder.initializePool(
    dexId: Int,
    baseAssetId: String,
    targetAssetId: String
) = this.call(
    Pallete.POOL_XYK.palletName,
    Method.INITIALIZE_POOL.methodName,
    mapOf(
        "dex_id" to dexId.toBigInteger(),
        "asset_a" to baseAssetId.mapCodeToken(),
        "asset_b" to targetAssetId.mapCodeToken(),
    )
)

fun ExtrinsicBuilder.depositLiquidity(
    dexId: Int,
    baseAssetId: String,
    targetAssetId: String,
    baseAssetAmount: BigInteger,
    targetAssetAmount: BigInteger,
    amountFromMin: BigInteger,
    amountToMin: BigInteger
) = this.call(
    Pallete.POOL_XYK.palletName,
    Method.DEPOSIT_LIQUIDITY.methodName,
    mapOf(
        "dex_id" to dexId.toBigInteger(),
        "input_asset_a" to baseAssetId.mapCodeToken(),
        "input_asset_b" to targetAssetId.mapCodeToken(),
        "input_a_desired" to baseAssetAmount,
        "input_b_desired" to targetAssetAmount,
        "input_a_min" to amountFromMin,
        "input_b_min" to amountToMin
    )
)

fun ExtrinsicBuilder.faucetTransfer(
    assetId: String,
    target: String,
    amount: BigInteger
) =
    this.call(
        Pallete.FAUCET.palletName,
        Method.TRANSFER.methodName,
        mapOf(
            "asset_id" to assetId.mapCodeToken(),
            "target" to target.toAccountId(),
            "amount" to amount
        )
    )

fun ExtrinsicBuilder.depositDemeter(
    baseAssetId: String,
    targetAssetId: String,
    rewardAssetId: String,
    isFarm: Boolean = true,
    amount: BigInteger
) =
    this.call(
        Pallete.DEMETER_FARMING.palletName,
        Method.DEMETER_STAKE.methodName,
        mapOf(
            "base_asset" to baseAssetId.mapCodeToken(),
            "pool_asset" to targetAssetId.mapCodeToken(),
            "reward_asset" to rewardAssetId.mapCodeToken(),
            "is_farm" to isFarm,
            "pooled_tokens" to amount
        )
    )

fun ExtrinsicBuilder.withdrawDemeter(
    baseAssetId: String,
    targetAssetId: String,
    rewardAssetId: String,
    isFarm: Boolean = true,
    amount: BigInteger
) =
    this.call(
        Pallete.DEMETER_FARMING.palletName,
        Method.DEMETER_UNSTAKE.methodName,
        mapOf(
            "base_asset" to baseAssetId.mapCodeToken(),
            "pool_asset" to targetAssetId.mapCodeToken(),
            "reward_asset" to rewardAssetId.mapCodeToken(),
            "is_farm" to isFarm,
            "pooled_tokens" to amount
        )
    )

fun ExtrinsicBuilder.claimDemeter(
    baseAssetId: String,
    targetAssetId: String,
    rewardAssetId: String,
    isFarm: Boolean = true,
) =
    this.call(
        Pallete.DEMETER_FARMING.palletName,
        Method.DEMETER_REWARDS.methodName,
        mapOf(
            "base_asset" to baseAssetId.mapCodeToken(),
            "pool_asset" to targetAssetId.mapCodeToken(),
            "reward_asset" to rewardAssetId.mapCodeToken(),
            "is_farm" to isFarm,
        )
    )
