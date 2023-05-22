/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.substrate

import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.Flavor
import jp.co.soramitsu.common.util.ext.removeHexPrefix
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.sora.substrate.blockexplorer.SoraConfigManager
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
