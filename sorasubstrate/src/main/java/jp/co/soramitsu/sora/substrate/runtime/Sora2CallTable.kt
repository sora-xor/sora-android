/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.sora.substrate.runtime

import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.Type
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.Alias
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.CollectionEnum
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.FixedArray
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.Option
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.Tuple
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.Vec
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.generics.Null
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.primitives.BooleanType
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.primitives.Compact
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.primitives.DynamicByteArray
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.primitives.UIntType
import jp.co.soramitsu.xsubstrate.runtime.metadata.callOrNull
import jp.co.soramitsu.xsubstrate.runtime.metadata.module.MetadataFunction
import jp.co.soramitsu.xsubstrate.runtime.metadata.module.Module
import jp.co.soramitsu.xsubstrate.runtime.metadata.moduleOrNull

/**
 * The live runtime describes one of the calls this app signs differently from the reviewed
 * encoding in [Sora2CallTable] ([calls], as `Pallet.call`). Signing and fee quotes are refused:
 * the app would otherwise write whatever pallet index, call index and argument layout the node's
 * metadata names, and the chain would execute those bytes. After a real runtime upgrade that
 * changes one of these calls, the app needs an update with a re-reviewed table.
 */
internal class Sora2CallTableMismatchException(
    val calls: List<String>,
) : Sora2AppUpdateRequiredException(signedExtensions = emptyList())

/**
 * The SORA2 calls this app builds, pinned to their reviewed encoding.
 *
 * xsubstrate resolves a call by pallet and call *name* in the runtime metadata and then writes the
 * pallet index, the call index and the arguments that metadata declares (`GenericCall.encode`).
 * Metadata comes from the node and type aliases from a remote file, and SORA2 has no
 * `CheckMetadataHash`, so without this table a node could make "remove liquidity" encode as a
 * transfer and the signature would be valid on chain.
 *
 * Every entry is the exact encoding shape ([Sora2CallShape]) of one call: pallet index, call index,
 * and each argument's name and full type layout, including struct field names and enum variant
 * names and indices. The shapes were read from mainnet runtime 138 (transaction version 134), whose
 * metadata both public nodes serve byte for byte alike, and the indices and enum layouts were
 * checked against the sora2-network source of that runtime (branch 4.8.13). Mainnet runtimes 130
 * and 133 and the develop and soralution fixtures encode these calls identically.
 *
 * A call that the live runtime does not offer is not a mismatch: the builder cannot resolve it, so
 * nothing is signed (the faucet, for example, exists only on test networks). A call that is offered
 * must match its entry exactly.
 */
internal object Sora2CallTable {

    data class PinnedCall(
        val pallet: String,
        val call: String,
        val shape: String,
    ) {
        val id: String get() = "$pallet.$call"
    }

    /** `AssetId32 { code: [u8; 32] }`; its `PhantomData` field has no bytes. */
    private const val ASSET = "{code:[u8;32]}"

    /** `AccountId32`. */
    private const val ACCOUNT = "bytes[32]"

    /** `Balance`. */
    private const val BALANCE = "u128"

    private const val SWAP_AMOUNT =
        "enum{0=WithDesiredInput:{desired_amount_in:$BALANCE,min_amount_out:$BALANCE}" +
            "|1=WithDesiredOutput:{desired_amount_out:$BALANCE,max_amount_in:$BALANCE}}"

    private const val LIQUIDITY_SOURCES =
        "Vec<enum{0=XYKPool|1=BondingCurvePool|2=MulticollateralBondingCurvePool|3=MockPool" +
            "|4=MockPool2|5=MockPool3|6=MockPool4|7=XSTPool|8=OrderBook}>"

    private const val FILTER_MODE = "enum{0=Disabled|1=ForbidSelected|2=AllowSelected}"

    private const val FARM = "base_asset:$ASSET,pool_asset:$ASSET,reward_asset:$ASSET"

    val pinned: List<PinnedCall> = listOf(
        PinnedCall(
            "Assets", "transfer",
            "21:21:1(asset_id:$ASSET,to:$ACCOUNT,amount:$BALANCE)",
        ),
        PinnedCall(
            "LiquidityProxy", "swap",
            "26:26:0(dex_id:u32,input_asset_id:$ASSET,output_asset_id:$ASSET," +
                "swap_amount:$SWAP_AMOUNT,selected_source_types:$LIQUIDITY_SOURCES," +
                "filter_mode:$FILTER_MODE)",
        ),
        PinnedCall(
            "PoolXYK", "deposit_liquidity",
            "25:25:0(dex_id:u32,input_asset_a:$ASSET,input_asset_b:$ASSET," +
                "input_a_desired:$BALANCE,input_b_desired:$BALANCE," +
                "input_a_min:$BALANCE,input_b_min:$BALANCE)",
        ),
        PinnedCall(
            "PoolXYK", "withdraw_liquidity",
            "25:25:1(dex_id:u32,output_asset_a:$ASSET,output_asset_b:$ASSET," +
                "marker_asset_desired:$BALANCE,output_a_min:$BALANCE,output_b_min:$BALANCE)",
        ),
        PinnedCall(
            "PoolXYK", "initialize_pool",
            "25:25:2(dex_id:u32,asset_a:$ASSET,asset_b:$ASSET)",
        ),
        PinnedCall(
            "TradingPair", "register",
            "20:20:0(dex_id:u32,base_asset_id:$ASSET,target_asset_id:$ASSET)",
        ),
        PinnedCall("Referrals", "reserve", "7:7:0(balance:$BALANCE)"),
        PinnedCall("Referrals", "unreserve", "7:7:1(balance:$BALANCE)"),
        PinnedCall("Referrals", "set_referrer", "7:7:2(referrer:$ACCOUNT)"),
        PinnedCall(
            "IrohaMigration", "migrate",
            "35:35:0(iroha_address:bytes,iroha_public_key:bytes,iroha_signature:bytes)",
        ),
        PinnedCall(
            "DemeterFarmingPlatform", "deposit",
            "50:50:2($FARM,is_farm:bool,pooled_tokens:$BALANCE)",
        ),
        // The runtime orders this call's last two arguments the other way round.
        PinnedCall(
            "DemeterFarmingPlatform", "withdraw",
            "50:50:4($FARM,pooled_tokens:$BALANCE,is_farm:bool)",
        ),
        PinnedCall(
            "DemeterFarmingPlatform", "get_rewards",
            "50:50:3($FARM,is_farm:bool)",
        ),
        // Test networks only; mainnet has no faucet.
        PinnedCall(
            "Faucet", "transfer",
            "80:80:0(asset_id:$ASSET,target:$ACCOUNT,amount:$BALANCE)",
        ),
        // ExtrinsicBuilder.build wraps several calls in one of these.
        PinnedCall("Utility", "batch", "11:11:0(calls:Vec<Call>)"),
        PinnedCall("Utility", "batch_all", "11:11:2(calls:Vec<Call>)"),
    )

    private val byId: Map<String, PinnedCall> by lazy { pinned.associateBy { it.id } }

    fun isPinned(pallet: String, call: String): Boolean = "$pallet.$call" in byId

    /** `Pallet.call` of every pinned call the runtime offers with a different encoding. */
    fun mismatches(runtime: RuntimeSnapshot): List<String> = pinned.mapNotNull { pin ->
        val module = runtime.metadata.moduleOrNull(pin.pallet) ?: return@mapNotNull null
        val function = module.callOrNull(pin.call) ?: return@mapNotNull null
        pin.id.takeIf { Sora2CallShape.of(module, function) != pin.shape }
    }
}

/**
 * Renders the SCALE encoding of a call as text: everything that decides which bytes the encoder
 * writes and which app value goes where. Aliases are followed, names that do not reach the bytes
 * (type names, documentation) are left out, and every field name and enum variant is kept because
 * the app passes its values by those names. A type this renderer does not know is written as
 * `?ClassName`, which never equals a pinned shape.
 */
internal object Sora2CallShape {

    private const val MAXIMUM_DEPTH = 32

    fun of(module: Module, function: MetadataFunction): String =
        "${module.index}:${function.index.first}:${function.index.second}" +
            function.arguments.joinToString(separator = ",", prefix = "(", postfix = ")") {
                "${it.name}:${render(it.type, 0)}"
            }

    fun render(type: Type<*>?, depth: Int): String {
        if (depth > MAXIMUM_DEPTH) return "?depth"
        val next = depth + 1
        return when (type) {
            null -> "?unresolved"
            is Alias -> render(type.aliasedReference.value, next)
            is Struct -> type.mapping.entries.joinToString(
                separator = ",",
                prefix = "{",
                postfix = "}",
            ) { (name, reference) -> "$name:${render(reference.value, next)}" }
            is DictEnum -> type.elements.entries.sortedBy { it.key }.joinToString(
                separator = "|",
                prefix = "enum{",
                postfix = "}",
            ) { (index, entry) ->
                val payload = entry.value.value
                if (payload is Null) "$index=${entry.name}" else
                    "$index=${entry.name}:${render(payload, next)}"
            }
            is CollectionEnum -> type.elements.withIndex().joinToString(
                separator = "|",
                prefix = "enum{",
                postfix = "}",
            ) { "${it.index}=${it.value}" }
            is Vec -> "Vec<${render(type.innerType, next)}>"
            is Option -> "Option<${render(type.innerType, next)}>"
            is FixedArray -> "[${render(type.innerType, next)};${type.length}]"
            is Tuple -> type.typeReferences.joinToString(
                separator = ",",
                prefix = "(",
                postfix = ")",
            ) { render(it.value, next) }
            is FixedByteArray -> "bytes[${type.length}]"
            is DynamicByteArray -> "bytes"
            is UIntType -> type.name
            is Compact -> "compact"
            is BooleanType -> "bool"
            is Null -> "()"
            is GenericCall -> "Call"
            else -> "?${type::class.java.simpleName}"
        }
    }
}
