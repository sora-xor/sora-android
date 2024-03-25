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

package jp.co.soramitsu.feature_account_impl.data.repository

import java.text.Normalizer
import jp.co.soramitsu.androidfoundation.format.removeHexPrefix
import jp.co.soramitsu.common.account.IrohaData
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.ext.didToAccountId
import jp.co.soramitsu.common.util.json_decoder.JsonAccountsEncoder
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.deriveSeed32
import jp.co.soramitsu.xcrypto.seed.Mnemonic
import jp.co.soramitsu.xcrypto.seed.MnemonicCreator
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xcrypto.util.toHexString
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.xsubstrate.encrypt.seed.substrate.SubstrateSeedFactory

class CredentialsRepositoryImpl constructor(
    private val credentialsPrefs: CredentialsDatasource,
    private val cryptoAssistant: CryptoAssistant,
    private val runtimeManager: RuntimeManager,
    private val jsonSeedEncoder: JsonAccountsEncoder,
    private val soraConfigManager: SoraConfigManager,
) : CredentialsRepository {

    private val irohaCash = mutableMapOf<String, IrohaData>()

    override suspend fun isMnemonicValid(mnemonic: String): Boolean {
        return runCatching { MnemonicCreator.fromWords(mnemonic) }.isSuccess
    }

    override suspend fun isRawSeedValid(rawSeed: String): Boolean {
        return runCatching { rawSeed.fromHex().size == 32 }.getOrDefault(false)
    }

    override suspend fun generateUserCredentials(accountName: String): SoraAccount {
        val mnemonic = generateMnemonic()
        val address = generateEntropyAndKeysFromMnemonic(mnemonic)
        return SoraAccount(address, accountName)
    }

    private suspend fun generateEntropyAndKeysFromMnemonic(mnemonic: Mnemonic): String {
        val derivationResult = SubstrateSeedFactory.deriveSeed32(mnemonic.words, null)
        val keyPair = SubstrateKeypairFactory.generate(
            SubstrateOptionsProvider.encryptionType,
            derivationResult.seed,
        )
        require(keyPair is Sr25519Keypair)

        val substrateAddress = runtimeManager.toSoraAddress(keyPair.publicKey)

        if (credentialsPrefs.retrieveKeys(substrateAddress)?.publicKey.contentEquals(keyPair.publicKey)) {
            throw SoraException.businessError(ResponseCode.ACCOUNT_ALREADY_IMPORTED)
        }

        credentialsPrefs.saveKeys(keyPair, substrateAddress)
        credentialsPrefs.saveMnemonic(mnemonic.words, substrateAddress)
        FirebaseWrapper.log("Keys were created")
        return substrateAddress
    }

    private suspend fun generateEntropyAndKeysFromRawSeed(rawSeed: String): String {
        val keyPair = SubstrateKeypairFactory.generate(
            SubstrateOptionsProvider.encryptionType,
            rawSeed.fromHex(),
        )
        require(keyPair is Sr25519Keypair)

        val substrateAddress = runtimeManager.toSoraAddress(keyPair.publicKey)

        if (credentialsPrefs.retrieveKeys(substrateAddress)?.publicKey.contentEquals(keyPair.publicKey)) {
            throw SoraException.businessError(ResponseCode.ACCOUNT_ALREADY_IMPORTED)
        }

        credentialsPrefs.saveKeys(keyPair, substrateAddress)
        credentialsPrefs.saveSeed(rawSeed, substrateAddress)
        FirebaseWrapper.log("Keys were created")
        return substrateAddress
    }

    private fun generateMnemonic(): Mnemonic =
        MnemonicCreator.randomMnemonic(Mnemonic.Length.TWELVE)

    override suspend fun restoreUserCredentialsFromMnemonic(
        mnemonic: String,
        accountName: String
    ): SoraAccount {
        val address = generateEntropyAndKeysFromMnemonic(MnemonicCreator.fromWords(mnemonic))
        return SoraAccount(address, accountName)
    }

    override suspend fun restoreUserCredentialsFromRawSeed(
        rawSeed: String,
        accountName: String
    ): SoraAccount {
        val address = generateEntropyAndKeysFromRawSeed(rawSeed.removeHexPrefix())
        return SoraAccount(address, accountName)
    }

    override suspend fun saveMnemonic(mnemonic: String, soraAccount: SoraAccount) {
        credentialsPrefs.saveMnemonic(mnemonic, soraAccount.substrateAddress)
    }

    override suspend fun retrieveMnemonic(soraAccount: SoraAccount): String {
        return credentialsPrefs.retrieveMnemonic(soraAccount.substrateAddress)
    }

    override suspend fun retrieveSeed(soraAccount: SoraAccount): String {
        var seed = credentialsPrefs.retrieveSeed(soraAccount.substrateAddress)

        if (seed.isEmpty()) {
            seed = convertPassphraseToSeed(credentialsPrefs.retrieveMnemonic(soraAccount.substrateAddress))
            credentialsPrefs.saveSeed(seed, soraAccount.substrateAddress)
        }

        return seed
    }

    override suspend fun retrieveKeyPair(soraAccount: SoraAccount): Sr25519Keypair {
        return credentialsPrefs.retrieveKeys(soraAccount.substrateAddress)
            ?: throw IllegalStateException("Keypair not found")
    }

    override suspend fun saveKeyPair(key: Sr25519Keypair, soraAccount: SoraAccount) {
        credentialsPrefs.saveKeys(key, soraAccount.substrateAddress)
    }

    override suspend fun getIrohaData(soraAccount: SoraAccount): IrohaData {
        if (irohaCash.containsKey(soraAccount.substrateAddress)) {
            return requireNotNull(irohaCash[soraAccount.substrateAddress]) { "Iroha cash failure" }
        } else {
            val mnemonic = retrieveMnemonic(soraAccount)
            val purpose = "iroha keypair"

            val entropy =
                Normalizer.normalize(mnemonic, Normalizer.Form.NFKD).toByteArray(charset("UTF-8"))
            val seed =
                cryptoAssistant.generateScryptSeedForEd25519(entropy, Const.SORA, purpose, "")
            val keys = cryptoAssistant.generateEd25519Keys(seed)
            val did = "did:sora:${keys.public.encoded.toHexString().substring(0, 20)}"
            val irohaAddress = did.didToAccountId()
            val message = irohaAddress + keys.public.encoded.toHexString()
            val signature = cryptoAssistant.signEd25519(message.toByteArray(charset("UTF-8")), keys)
            val data = IrohaData(
                address = irohaAddress,
                claimSignature = signature.toHexString(),
                publicKey = keys.public.encoded.toHexString()
            )
            FirebaseWrapper.log("iroha ${data.address.isNotEmpty()} ${data.claimSignature.isNotEmpty()} ${data.publicKey.isNotEmpty()}}")
            irohaCash[soraAccount.substrateAddress] = data
            return data
        }
    }

    // TODO: modify for multiaccount variant
    override suspend fun getAddressForMigration(): String {
        var address = credentialsPrefs.getAddress()
        if (address.isEmpty()) {
            address =
                runtimeManager.toSoraAddressOrNull(credentialsPrefs.retrieveKeys("")?.publicKey)
                    .orEmpty()
            FirebaseWrapper.log("Address recreated ${address.isNotEmpty()}")
        }
        return address
    }

    override suspend fun generateJson(accounts: List<SoraAccount>, password: String): String {
        if (accounts.size == 1) {
            accounts.first().let {
                val seed = credentialsPrefs.retrieveSeed(it.substrateAddress)
                val keys = credentialsPrefs.retrieveKeys(it.substrateAddress) as Sr25519Keypair

                val exportAccountData = JsonAccountsEncoder.ExportAccount(
                    keypair = keys,
                    seed = seed.fromHex(),
                    it.accountName,
                    it.substrateAddress
                )

                return jsonSeedEncoder.generate(
                    account = exportAccountData,
                    password = password,
                    genesisHash = soraConfigManager.getGenesis()
                )
            }
        } else {
            val accountsList = accounts.map {
                val seed = credentialsPrefs.retrieveSeed(it.substrateAddress)
                val keys = credentialsPrefs.retrieveKeys(it.substrateAddress) as Sr25519Keypair

                JsonAccountsEncoder.ExportAccount(
                    keypair = keys,
                    seed = seed.toByteArray(),
                    it.accountName,
                    it.substrateAddress
                )
            }

            return jsonSeedEncoder.generate(accountsList, password, soraConfigManager.getGenesis())
        }
    }

    override fun convertPassphraseToSeed(mnemonic: String): String {
        val derivationResult = SubstrateSeedFactory.deriveSeed32(MnemonicCreator.fromWords(mnemonic).words, null)
        return derivationResult.seed.toHexString()
    }
}
