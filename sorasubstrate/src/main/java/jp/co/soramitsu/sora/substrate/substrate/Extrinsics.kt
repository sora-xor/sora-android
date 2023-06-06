/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.substrate

import java.math.BigInteger
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.shared_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.sora.substrate.models.WithDesired
import jp.co.soramitsu.sora.substrate.runtime.Method
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.mapAssetId

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
            "input_asset_id" to Struct.Instance(
                mapOf("code" to inputAssetId.mapAssetId())
            ),
            "output_asset_id" to Struct.Instance(
                mapOf("code" to outputAssetId.mapAssetId())
            ),
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
            "asset_id" to Struct.Instance(
                mapOf("code" to assetId.mapAssetId())
            ),
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
            "output_asset_a" to Struct.Instance(
                mapOf("code" to outputAssetIdA.mapAssetId())
            ),
            "output_asset_b" to Struct.Instance(
                mapOf("code" to outputAssetIdB.mapAssetId())
            ),
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
        "base_asset_id" to Struct.Instance(
            mapOf("code" to baseAssetId.mapAssetId())
        ),
        "target_asset_id" to Struct.Instance(
            mapOf("code" to targetAssetId.mapAssetId())
        )
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
        "asset_a" to Struct.Instance(
            mapOf("code" to baseAssetId.mapAssetId())
        ),
        "asset_b" to Struct.Instance(
            mapOf("code" to targetAssetId.mapAssetId())
        )
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
        "input_asset_a" to Struct.Instance(
            mapOf("code" to baseAssetId.mapAssetId())
        ),
        "input_asset_b" to Struct.Instance(
            mapOf("code" to targetAssetId.mapAssetId())
        ),
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
            "asset_id" to Struct.Instance(
                mapOf("code" to assetId.mapAssetId())
            ),
            "target" to target.toAccountId(),
            "amount" to amount
        )
    )
