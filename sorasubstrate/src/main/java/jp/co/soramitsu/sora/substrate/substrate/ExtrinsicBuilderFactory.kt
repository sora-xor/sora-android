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
