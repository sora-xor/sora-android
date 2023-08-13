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
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.Flavor
import jp.co.soramitsu.common.util.ext.removeHexPrefix
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.shared_utils.encrypt.EncryptionType
import jp.co.soramitsu.shared_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.shared_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.shared_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.shared_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider

@Singleton
class ExtrinsicBuilderFactory @Inject constructor(
    private val calls: SubstrateCalls,
    private val runtimeManager: RuntimeManager,
    private val soraConfigManager: SoraConfigManager,
) {

    suspend fun create(
        from: String
    ): ExtrinsicBuilder {
        return buildExtrinsic(from, generateFakeKeyPair(), runtimeManager.getRuntimeSnapshot())
    }

    suspend fun create(
        from: String,
        keypair: Sr25519Keypair,
    ): ExtrinsicBuilder {
        return buildExtrinsic(from, keypair, runtimeManager.getRuntimeSnapshot())
    }

    private suspend fun buildExtrinsic(
        from: String,
        keypair: Sr25519Keypair,
        runtime: RuntimeSnapshot,
    ): ExtrinsicBuilder {
        val fromAddress = from.toAccountId()
        val runtimeVersion = calls.getRuntimeVersion()
        val finalizedHash = calls.getFinalizedHead()
        val blockHeaderFinalized = calls.getChainHeader(finalizedHash)
        val blockHeaderLast1 = calls.getChainLastHeader()
        val blockHeaderLast2 = calls.getChainHeader(blockHeaderLast1.parentHash)
        val numberFinalized = blockHeaderFinalized.number.removeHexPrefix().toInt(16)
        val numberLast = blockHeaderLast2.number.removeHexPrefix().toInt(16)
        val (number, hash) = if (numberFinalized < numberLast &&
            numberLast - numberFinalized < 5
        ) numberFinalized to finalizedHash else numberLast to blockHeaderLast1.parentHash
        val genesis = genesisBytes()
        val nonce = calls.getNonce(from)
        return ExtrinsicBuilder(
            runtime = runtime,
            keypair = keypair,
            nonce = nonce,
            runtimeVersion = runtimeVersion,
            genesisHash = genesis,
            multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.SR25519),
            accountIdentifier = fromAddress,
            blockHash = hash.removeHexPrefix().fromHex(),
            era = Era.getEraFromBlockPeriod(
                number,
                SubstrateOptionsProvider.mortalEraLength
            )
        )
    }

    private suspend fun genesisBytes(): ByteArray =
        if (BuildUtils.isFlavors(Flavor.DEVELOP, Flavor.TESTING, Flavor.SORALUTION)) {
            val result = calls.getBlockHash()
            result.removeHexPrefix().fromHex()
        } else {
            soraConfigManager.getGenesis().fromHex()
        }

    private fun generateFakeKeyPair() = SubstrateKeypairFactory.generate(
        EncryptionType.SR25519,
        ByteArray(32) { 1 },
        emptyList(),
    ) as Sr25519Keypair
}
