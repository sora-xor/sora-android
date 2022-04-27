/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.credentials.repository

import jp.co.soramitsu.common.account.IrohaData
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeHolder
import jp.co.soramitsu.common.data.network.substrate.toSoraAddress
import jp.co.soramitsu.common.domain.credentials.CredentialsDatasource
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.deriveSeed32
import jp.co.soramitsu.common.util.ext.didToAccountId
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.Mnemonic
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import org.spongycastle.util.encoders.Hex
import java.text.Normalizer
import javax.inject.Inject

class CredentialsRepositoryImpl @Inject constructor(
    private val credentialsPrefs: CredentialsDatasource,
    private val cryptoAssistant: CryptoAssistant
) : CredentialsRepository {

    private val irohaCash = mutableMapOf<String, IrohaData>()

    override suspend fun isMnemonicValid(mnemonic: String): Boolean {
        return runCatching { MnemonicCreator.fromWords(mnemonic) }.isSuccess
    }

    override suspend fun generateUserCredentials(accountName: String): SoraAccount {
        val mnemonic = generateMnemonic()
        val address = generateEntropyAndKeys(mnemonic)
        return SoraAccount(address, accountName)
    }

    private suspend fun generateEntropyAndKeys(mnemonic: Mnemonic): String {
        val derivationResult = SubstrateSeedFactory.deriveSeed32(mnemonic.words, null)
        val keyPair = SubstrateKeypairFactory.generate(
            OptionsProvider.encryptionType,
            derivationResult.seed,
        )
        require(keyPair is Sr25519Keypair)

        val substrateAddress = keyPair.publicKey.toSoraAddress()
        credentialsPrefs.saveKeys(keyPair, substrateAddress)
        credentialsPrefs.saveMnemonic(mnemonic.words, substrateAddress)
        FirebaseWrapper.log("Keys were created")
        return substrateAddress
    }

    private fun generateMnemonic(): Mnemonic =
        MnemonicCreator.randomMnemonic(Mnemonic.Length.TWELVE)

    override suspend fun restoreUserCredentials(
        mnemonic: String,
        accountName: String
    ): SoraAccount {
        val address = generateEntropyAndKeys(MnemonicCreator.fromWords(mnemonic))
        return SoraAccount(address, accountName)
    }

    override suspend fun saveMnemonic(mnemonic: String, soraAccount: SoraAccount) {
        credentialsPrefs.saveMnemonic(mnemonic, soraAccount.substrateAddress)
    }

    override suspend fun retrieveMnemonic(soraAccount: SoraAccount): String {
        return credentialsPrefs.retrieveMnemonic(soraAccount.substrateAddress)
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
            val projectName = "SORA"
            val purpose = "iroha keypair"

            val entropy =
                Normalizer.normalize(mnemonic, Normalizer.Form.NFKD).toByteArray(charset("UTF-8"))
            val seed = cryptoAssistant.generateScryptSeedForEd25519(entropy, projectName, purpose, "")
            val keys = cryptoAssistant.generateEd25519Keys(seed)
            val did = "did:sora:${Hex.toHexString(keys.public.encoded).substring(0, 20)}"
            val irohaAddress = did.didToAccountId()
            val message = irohaAddress + Hex.toHexString(keys.public.encoded)
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

    override suspend fun getAddressForMigration(): String {
        var address = credentialsPrefs.getAddress()
        if (address.isEmpty()) {
            address =
                credentialsPrefs.retrieveKeys("")?.publicKey?.toSoraAddress().orEmpty()
            FirebaseWrapper.log("Address recreated ${address.isNotEmpty()}")
        }
        return address
    }

    override suspend fun isAddressOk(address: String): Boolean =
        runCatching { address.toAccountId() }.getOrNull() != null &&
            SS58Encoder.extractAddressByte(address) == RuntimeHolder.prefix
}
