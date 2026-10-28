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

import java.math.BigInteger
import jp.co.soramitsu.common.domain.NetworkFeeUnavailable
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.ExtrinsicMetadataV14
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.LookupSchema
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.PortableType
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.RegistryType
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.RuntimeMetadataSchemaV14
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.SignedExtensionMetadataV14
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.TypeDefArray
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.TypeDefCompact
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.TypeDefComposite
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.TypeDefCompositeField
import jp.co.soramitsu.xsubstrate.scale.EncodableStruct

/**
 * The live runtime lists a signed extension that carries data this app version cannot encode
 * ([signedExtensions]). A transaction built anyway would be malformed and rejected by every node,
 * so signing and fee quotes are refused until the app is updated. Balances and history are not
 * affected: they need no signed extensions.
 */
internal class Sora2AppUpdateRequiredException(
    val signedExtensions: List<String>,
) : Sora2RuntimeIdentityException(NetworkFeeUnavailable.APP_UPDATE_REQUIRED_CODE)

/**
 * Compares the signed extensions of live V14 metadata with what the vendored extrinsic encoder
 * (xsubstrate 1.2.7, `SignedExtras` and `AdditionalExtras`) writes.
 *
 * The encoder walks the runtime's extension list and silently skips every identifier it has no
 * entry for. That is correct for an extension without data (`CheckWeight`, `CheckNonZeroSender`)
 * and produces wrong bytes for one with data (for example `CheckMetadataHash`, which adds a mode
 * byte to the extrinsic and an optional hash to the signed payload). Whether an extension carries
 * data is read from the metadata itself: its `type` and `additionalSigned` are zero-sized or not.
 */
internal object Sora2SignedExtensionAdmission {

    /** Extensions whose part inside the extrinsic the encoder writes. */
    private val ENCODED_IN_EXTRINSIC = setOf(
        "CheckMortality",
        "CheckNonce",
        "ChargeTransactionPayment",
    )

    /** Extensions whose part of the signed payload (additional signed data) the encoder writes. */
    private val ENCODED_IN_SIGNED_PAYLOAD = setOf(
        "CheckMortality",
        "CheckGenesis",
        "CheckSpecVersion",
        "CheckTxVersion",
    )

    private const val MAXIMUM_TYPE_DEPTH = 16

    /** Identifiers, in runtime order, of extensions with data the encoder would leave out. */
    fun unsupportedExtensions(
        metadata: EncodableStruct<RuntimeMetadataSchemaV14>,
    ): List<String> {
        val types = metadata[RuntimeMetadataSchemaV14.lookup][LookupSchema.types]
            .associate { it[PortableType.id] to it[PortableType.type] }
        return metadata[RuntimeMetadataSchemaV14.extrinsic][ExtrinsicMetadataV14.signedExtensions]
            .filter { extension ->
                val identifier = extension[SignedExtensionMetadataV14.identifier]
                val inExtrinsic = extension[SignedExtensionMetadataV14.type]
                val inSignedPayload = extension[SignedExtensionMetadataV14.additionalSigned]
                (identifier !in ENCODED_IN_EXTRINSIC && !isZeroSized(types, inExtrinsic, 0)) ||
                    (
                        identifier !in ENCODED_IN_SIGNED_PAYLOAD &&
                            !isZeroSized(types, inSignedPayload, 0)
                        )
            }
            .map { it[SignedExtensionMetadataV14.identifier] }
    }

    /**
     * True only when the SCALE encoding of the type is provably empty: a tuple or composite of
     * such types, an array of none or of such a type, or a compact of such a type. Anything else,
     * including a shape this function cannot follow, counts as carrying data.
     */
    private fun isZeroSized(
        types: Map<BigInteger, EncodableStruct<RegistryType>>,
        id: BigInteger,
        depth: Int,
    ): Boolean {
        if (depth > MAXIMUM_TYPE_DEPTH) return false
        val definition = types[id]?.get(RegistryType.def) ?: return false
        return when (definition) {
            is EncodableStruct<*> -> when (definition.schema) {
                is TypeDefComposite -> definition[TypeDefComposite.fields2].all { field ->
                    isZeroSized(types, field[TypeDefCompositeField.type], depth + 1)
                }
                is TypeDefArray -> definition[TypeDefArray.len] == 0u ||
                    isZeroSized(types, definition[TypeDefArray.type], depth + 1)
                is TypeDefCompact ->
                    isZeroSized(types, definition[TypeDefCompact.type], depth + 1)
                // A variant has an index byte, a sequence a length, a bit sequence a length.
                else -> false
            }
            // A tuple is a list of type ids; `()` is the empty one.
            is List<*> -> definition.all { element ->
                element is BigInteger && isZeroSized(types, element, depth + 1)
            }
            // A primitive.
            else -> false
        }
    }
}
