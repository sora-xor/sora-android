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

import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.androidfoundation.format.removeHexPrefix
import jp.co.soramitsu.sora.substrate.runtime.QualifiedSora2MutationRuntime
import jp.co.soramitsu.sora.substrate.runtime.Sora2BoundedRuntimeRpcClient
import jp.co.soramitsu.sora.substrate.runtime.Sora2MutationRuntimeContext
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xsubstrate.encrypt.EncryptionType
import jp.co.soramitsu.xsubstrate.encrypt.MultiChainEncryption
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.generics.Era
import jp.co.soramitsu.xsubstrate.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId

@Singleton
class ExtrinsicBuilderFactory @Inject constructor(
    private val runtimeRpcClient: Sora2BoundedRuntimeRpcClient,
) {

    /**
     * Couples a signing builder to the exact immutable runtime context used to create it. The
     * internal type and constructor keep feature modules from supplying an unrelated context when
     * [ExtrinsicManager] creates its non-forgeable prepared envelope.
     */
    internal class ContextBoundExtrinsicBuilder internal constructor(
        val builder: ExtrinsicBuilder,
        val runtimeContext: Sora2MutationRuntimeContext,
    )

    internal suspend fun createForFee(
        from: String,
        runtimeContext: Sora2MutationRuntimeContext,
    ): ContextBoundExtrinsicBuilder = ContextBoundExtrinsicBuilder(
        builder = buildExtrinsic(
            from = from,
            keypair = generateFakeKeyPair(),
            runtimeContext = runtimeContext,
        ),
        runtimeContext = runtimeContext,
    )

    /**
     * Generic signing must make its single context fetch explicit and receive that same context
     * back with the builder. This prevents a caller from signing with a hidden second fetch and
     * later attaching a different identity to the prepared bytes.
     */
    internal suspend fun createForSigning(
        from: String,
        keypair: Sr25519Keypair,
        runtimeContext: Sora2MutationRuntimeContext,
    ): ContextBoundExtrinsicBuilder = ContextBoundExtrinsicBuilder(
        builder = buildExtrinsic(from, keypair, runtimeContext),
        runtimeContext = runtimeContext,
    )

    /**
     * The only builder entry-point family that accepts a previously qualified context. It is
     * internal so feature code must go through ExtrinsicManager's Polkamarkt fee/preparation
     * boundaries and cannot replace the context between runtime qualification and signing.
     */
    internal suspend fun createForPolkamarkt(
        from: String,
        runtime: QualifiedSora2MutationRuntime,
    ): ContextBoundExtrinsicBuilder = ContextBoundExtrinsicBuilder(
        builder = buildExtrinsic(from, generateFakeKeyPair(), runtime.context),
        runtimeContext = runtime.context,
    )

    internal suspend fun createForPolkamarkt(
        from: String,
        keypair: Sr25519Keypair,
        runtime: QualifiedSora2MutationRuntime,
    ): ContextBoundExtrinsicBuilder = ContextBoundExtrinsicBuilder(
        builder = buildExtrinsic(from, keypair, runtime.context),
        runtimeContext = runtime.context,
    )

    private suspend fun buildExtrinsic(
        from: String,
        keypair: Sr25519Keypair,
        runtimeContext: Sora2MutationRuntimeContext,
    ): ExtrinsicBuilder {
        val fromAddress = from.toAccountId()
        val number = runtimeContext.finalizedBlockNumber
        check(number in 0L..Int.MAX_VALUE.toLong()) {
            "SORA2_FINALIZED_BLOCK_NUMBER_INVALID"
        }
        val nonce = runtimeRpcClient.getAccountNextIndex(from)
        return ExtrinsicBuilder(
            runtime = runtimeContext.snapshot,
            keypair = keypair,
            nonce = nonce,
            runtimeVersion = runtimeContext.runtimeVersion,
            genesisHash = runtimeContext.genesisHash.removeHexPrefix().fromHex(),
            multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.SR25519),
            accountIdentifier = fromAddress,
            blockHash = runtimeContext.finalizedHash.removeHexPrefix().fromHex(),
            era = Era.getEraFromBlockPeriod(
                number.toInt(),
                SubstrateOptionsProvider.mortalEraLength
            )
        )
    }

    private fun generateFakeKeyPair() = SubstrateKeypairFactory.generate(
        EncryptionType.SR25519,
        ByteArray(32) { 1 },
        emptyList(),
    ) as Sr25519Keypair
}
