/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.network.substrate

import jp.co.soramitsu.common.data.network.substrate.Method
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.data.network.substrate.Pallete
import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeHolder
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.compactInt
import jp.co.soramitsu.fearless_utils.scale.dataType.byteArraySized
import jp.co.soramitsu.fearless_utils.scale.schema
import jp.co.soramitsu.fearless_utils.scale.sizedByteArray
import jp.co.soramitsu.fearless_utils.scale.uint128
import jp.co.soramitsu.fearless_utils.scale.uint32
import jp.co.soramitsu.fearless_utils.scale.vector
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_wallet_api.domain.model.WithDesired
import java.math.BigInteger

fun String.blake2b256String() = this.fromHex().blake2b256().toHexString(true)

object AccountData : Schema<AccountData>() {
    val free by uint128()
    val reserved by uint128()
    val miscFrozen by uint128()
    val feeFrozen by uint128()
}

object PooledAssetId : Schema<PooledAssetId>() {
    val assetId by vector(byteArraySized(32))
}

object ReservesResponse : Schema<ReservesResponse>() {
    val first by uint128()
    val second by uint128()
}

object PoolPropertiesResponse : Schema<PoolPropertiesResponse>() {
    val first by sizedByteArray(32)
    val second by sizedByteArray(32)
}

object TotalIssuance : Schema<TotalIssuance>() {
    val totalIssuance by uint128()
}

object PoolProviders : Schema<PoolProviders>() {
    val poolProviders by uint128()
}

object AccountInfo : Schema<AccountInfo>() {
    val nonce by uint32()
    val consumers by uint32()
    val providers by uint32()
    val data by schema(AccountData)
}

object StakingLedger : Schema<StakingLedger>() {
    val stash by sizedByteArray(32)
    val total by compactInt()
    val active by compactInt()
    val unlocking by vector(UnlockChunk)
    val claimedRewards by vector(jp.co.soramitsu.fearless_utils.scale.dataType.uint32)
}

object UnlockChunk : Schema<UnlockChunk>() {
    val value by compactInt()
    val era by compactInt()
}

object ActiveEraInfo : Schema<ActiveEraInfo>() {
    val index by uint32()
}

object ControllerAccountId : Schema<ControllerAccountId>() {
    val identifier by sizedByteArray(32)
}

fun ExtrinsicBuilder.swap(
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
            "dex_id" to OptionsProvider.dexId.toBigInteger(),
            "input_asset_id" to if (RuntimeHolder.getMetadataVersion() < 14) inputAssetId.fromHex() else Struct.Instance(mapOf("code" to inputAssetId.fromHex().toList().map { it.toInt().toBigInteger() })),
            "output_asset_id" to if (RuntimeHolder.getMetadataVersion() < 14) outputAssetId.fromHex() else Struct.Instance(mapOf("code" to outputAssetId.fromHex().toList().map { it.toInt().toBigInteger() })),
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
            "selected_source_types" to markets,
            "filter_mode" to if (RuntimeHolder.getMetadataVersion() < 14) filter else DictEnum.Entry(
                name = filter,
                value = null
            ),
        )
    )

fun ExtrinsicBuilder.transfer(assetId: String, to: String, amount: BigInteger) =
    this.call(
        Pallete.ASSETS.palletName,
        Method.TRANSFER.methodName,
        mapOf(
            "asset_id" to if (RuntimeHolder.getMetadataVersion() < 14) assetId.fromHex() else Struct.Instance(mapOf("code" to assetId.fromHex().toList().map { it.toInt().toBigInteger() })),
            "to" to to.toAccountId(),
            "amount" to amount
        )
    )

fun ExtrinsicBuilder.migrate(irohaAddress: String, irohaPublicKey: String, signature: String) =
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
            "dex_id" to OptionsProvider.dexId.toBigInteger(),
            "output_asset_a" to if (RuntimeHolder.getMetadataVersion() < 14) outputAssetIdA.fromHex() else Struct.Instance(mapOf("code" to outputAssetIdA.fromHex().toList().map { it.toInt().toBigInteger() })),
            "output_asset_b" to if (RuntimeHolder.getMetadataVersion() < 14) outputAssetIdB.fromHex() else Struct.Instance(mapOf("code" to outputAssetIdB.fromHex().toList().map { it.toInt().toBigInteger() })),
            "marker_asset_desired" to markerAssetDesired,
            "output_a_min" to outputAMin,
            "output_b_min" to outputBMin
        )
    )

fun ExtrinsicBuilder.register(
    baseAssetId: String,
    targetAssetId: String
) = this.call(
    Pallete.TRADING_PAIR.palletName,
    Method.REGISTER.methodName,
    mapOf(
        "dex_id" to OptionsProvider.dexId.toBigInteger(),
        "base_asset_id" to if (RuntimeHolder.getMetadataVersion() < 14) baseAssetId.fromHex() else Struct.Instance(
            mapOf("code" to baseAssetId.fromHex().toList().map { it.toInt().toBigInteger() })
        ),
        "target_asset_id" to if (RuntimeHolder.getMetadataVersion() < 14) targetAssetId.fromHex() else Struct.Instance(
            mapOf("code" to targetAssetId.fromHex().toList().map { it.toInt().toBigInteger() })
        )
    )
)

fun ExtrinsicBuilder.initializePool(
    baseAssetId: String,
    targetAssetId: String
) = this.call(
    Pallete.POOL_XYK.palletName,
    Method.INITIALIZE_POOL.methodName,
    mapOf(
        "dex_id" to OptionsProvider.dexId.toBigInteger(),
        "asset_a" to if (RuntimeHolder.getMetadataVersion() < 14) baseAssetId.fromHex() else Struct.Instance(
            mapOf("code" to baseAssetId.fromHex().toList().map { it.toInt().toBigInteger() })
        ),
        "asset_b" to if (RuntimeHolder.getMetadataVersion() < 14) targetAssetId.fromHex() else Struct.Instance(
            mapOf("code" to targetAssetId.fromHex().toList().map { it.toInt().toBigInteger() })
        )
    )
)

fun ExtrinsicBuilder.depositLiquidity(
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
        "dex_id" to OptionsProvider.dexId.toBigInteger(),
        "input_asset_a" to if (RuntimeHolder.getMetadataVersion() < 14) baseAssetId.fromHex() else Struct.Instance(
            mapOf("code" to baseAssetId.fromHex().toList().map { it.toInt().toBigInteger() })
        ),
        "input_asset_b" to if (RuntimeHolder.getMetadataVersion() < 14) targetAssetId.fromHex() else Struct.Instance(
            mapOf("code" to targetAssetId.fromHex().toList().map { it.toInt().toBigInteger() })
        ),
        "input_a_desired" to baseAssetAmount,
        "input_b_desired" to targetAssetAmount,
        "input_a_min" to amountFromMin,
        "input_b_min" to amountToMin
    )
)
