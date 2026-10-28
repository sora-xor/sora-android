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

package jp.co.soramitsu.sora.substrate.substrate

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import java.io.ByteArrayOutputStream
import jp.co.soramitsu.xcrypto.hash.blake2.blake2b256
import jp.co.soramitsu.xcrypto.seed.Mnemonic
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xcrypto.util.toHexString
import jp.co.soramitsu.xsubstrate.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.xsubstrate.encrypt.seed.SeedFactory
import jp.co.soramitsu.xsubstrate.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.xsubstrate.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.xsubstrate.extensions.fromUnsignedBytes
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.generics.GenericEvent
import jp.co.soramitsu.xsubstrate.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.xsubstrate.runtime.metadata.module
import jp.co.soramitsu.xsubstrate.runtime.metadata.module.Module
import jp.co.soramitsu.xsubstrate.runtime.metadata.module.StorageEntry
import jp.co.soramitsu.xsubstrate.runtime.metadata.moduleOrNull
import jp.co.soramitsu.xsubstrate.runtime.metadata.storageKey
import jp.co.soramitsu.xsubstrate.scale.EncodableStruct
import jp.co.soramitsu.xsubstrate.scale.Schema
import jp.co.soramitsu.xsubstrate.scale.datatype.DataType
import jp.co.soramitsu.xsubstrate.scale.datatype.uint32
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.xsubstrate.wsrpc.mappers.nonNull
import jp.co.soramitsu.xsubstrate.wsrpc.mappers.pojo

val BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH: String
    get() = "//44//60//0/0/0"

fun BIP32JunctionDecoder.default() = decode(DEFAULT_DERIVATION_PATH)

fun StorageEntry.defaultInHex() = default.toHexString(withPrefix = true)

fun <T> DataType<T>.fromHex(hex: String): T {
    val codecReader = ScaleCodecReader(hex.fromHex())

    return read(codecReader)
}

fun <T> DataType<T>.toHex(value: T): String {
    return toByteArray(value).toHexString(withPrefix = true)
}

fun <T> DataType<T>.toByteArray(value: T): ByteArray {
    val stream = ByteArrayOutputStream()
    val writer = ScaleCodecWriter(stream)

    write(writer, value)

    return stream.toByteArray()
}

typealias StructBuilderWithContext<S> = S.(EncodableStruct<S>) -> Unit

operator fun <S : Schema<S>> S.invoke(block: StructBuilderWithContext<S>? = null): EncodableStruct<S> {
    val struct = EncodableStruct(this)

    block?.invoke(this, struct)

    return struct
}

fun <S : Schema<S>> EncodableStruct<S>.hash(): String {
    return schema.toByteArray(this).blake2b256().toHexString(withPrefix = true)
}

fun String.extrinsicHash(): String {
    return fromHex().blake2b256().toHexString(withPrefix = true)
        .canonicalExtrinsicHash()
}

/**
 * Canonical transaction identity used by durable pending journals and reconciliation.
 *
 * Whitespace, truncated values, overlong values, embedded prefixes, and the all-zero sentinel are
 * rejected instead of being normalized into a different transaction.
 */
fun String.canonicalExtrinsicHash(): String {
    require(this == trim()) { "INVALID_EXTRINSIC_HASH" }
    val payload = when {
        startsWith("0x") || startsWith("0X") -> substring(2)
        else -> this
    }
    require(
        payload.length == 64 &&
            payload.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } &&
            payload.any { it != '0' }
    ) {
        "INVALID_EXTRINSIC_HASH"
    }
    return "0x${payload.lowercase()}"
}

fun String.toHexAccountId(): String = toAccountId().toHexString()

fun preBinder() = pojo<String>().nonNull()

val GenericEvent.Instance.index
    get() = event.index

fun Module.constant(name: String) = constantOrNull(name) ?: throw NoSuchElementException()

fun Module.constantOrNull(name: String) = constants[name]

fun RuntimeMetadata.staking() = module(Modules.STAKING)

fun RuntimeMetadata.system() = module(Modules.SYSTEM)

fun RuntimeMetadata.balances() = module(Modules.BALANCES)

fun RuntimeMetadata.crowdloan() = module(Modules.CROWDLOAN)

fun RuntimeMetadata.babe() = module(Modules.BABE)

fun RuntimeMetadata.slots() = module(Modules.SLOTS)

fun RuntimeMetadata.session() = module(Modules.SESSION)

fun <T> StorageEntry.storageKeys(
    runtime: RuntimeSnapshot,
    singleMapArguments: Collection<T>
): Map<String, T> {
    return singleMapArguments.associateBy { storageKey(runtime, it) }
}

inline fun <K, T> StorageEntry.storageKeys(
    runtime: RuntimeSnapshot,
    singleMapArguments: Collection<T>,
    argumentTransform: (T) -> K
): Map<String, K> {
    return singleMapArguments.associateBy(
        keySelector = { storageKey(runtime, it) },
        valueTransform = { argumentTransform(it) }
    )
}

fun RuntimeMetadata.hasModule(name: String) = moduleOrNull(name) != null

private const val HEX_SYMBOLS_PER_BYTE = 2
private const val UINT_32_BYTES = 4

fun String.u32ArgumentFromStorageKey() =
    uint32.fromHex(takeLast(HEX_SYMBOLS_PER_BYTE * UINT_32_BYTES)).toLong().toBigInteger()

fun ByteArray.decodeToInt() = fromUnsignedBytes().toInt()

fun SeedFactory.createSeed32(length: Mnemonic.Length, password: String?) =
    cropSeedTo32Bytes(createSeed(length, password))

// fun SeedFactory.deriveSeed32(mnemonicWords: String, password: String?) = cropSeedTo32Bytes(deriveSeed(mnemonicWords, password))

fun SubstrateSeedFactory.deriveSeed32(mnemonicWords: String, password: String?) =
    cropSeedTo32Bytes(deriveSeed(mnemonicWords, password))

fun EthereumSeedFactory.deriveSeed32(mnemonicWords: String, password: String?) =
    deriveSeed(mnemonicWords, password)

private fun cropSeedTo32Bytes(seedResult: SeedFactory.Result): SeedFactory.Result {
    return SeedFactory.Result(seed = seedResult.seed.copyOfRange(0, 32), seedResult.mnemonic)
}

object Modules {
    const val STAKING = "Staking"
    const val BALANCES = "Balances"
    const val SYSTEM = "System"
    const val CROWDLOAN = "Crowdloan"
    const val BABE = "Babe"
    const val SLOTS = "Slots"
    const val SESSION = "Session"
}
