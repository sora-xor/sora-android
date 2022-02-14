/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.credentials.repository

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
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import org.spongycastle.util.encoders.Hex
import java.security.KeyPair
import java.text.Normalizer
import javax.inject.Inject

class CredentialsRepositoryImpl @Inject constructor(
    private val credentialsPrefs: CredentialsDatasource,
    private val cryptoAssistant: CryptoAssistant
) : CredentialsRepository {

    override suspend fun isMnemonicValid(mnemonic: String): Boolean {
        return runCatching { MnemonicCreator.fromWords(mnemonic) }.isSuccess
    }

    override suspend fun generateUserCredentials() {
        val mnemonic = generateMnemonic()
        generateEntropyAndKeys(mnemonic)
    }

    private suspend fun generateEntropyAndKeys(mnemonic: Mnemonic) {
        val derivationResult = SubstrateSeedFactory.deriveSeed32(mnemonic.words, null)
        val keyPair = SubstrateKeypairFactory.generate(
            OptionsProvider.encryptionType,
            derivationResult.seed,
        )
        require(keyPair is Sr25519Keypair)

        credentialsPrefs.saveAddress(keyPair.publicKey.toSoraAddress())
        credentialsPrefs.saveKeys(keyPair)
        credentialsPrefs.saveMnemonic(mnemonic.words)
        FirebaseWrapper.log("Keys were created")
    }

    private fun generateMnemonic(): Mnemonic = MnemonicCreator.randomMnemonic(Mnemonic.Length.TWELVE)

    private suspend fun generateAndSaveClaimData(mnemonic: String) {
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
        credentialsPrefs.saveIrohaAddress(irohaAddress)
        credentialsPrefs.saveIrohaKeys(keys)
        credentialsPrefs.saveSignature(signature)
    }

    override suspend fun restoreUserCredentials(mnemonic: String) {
        generateEntropyAndKeys(MnemonicCreator.fromWords(mnemonic))
        generateAndSaveClaimData(mnemonic)
    }

    override suspend fun saveMnemonic(mnemonic: String) {
        credentialsPrefs.saveMnemonic(mnemonic)
    }

    override suspend fun retrieveMnemonic(): String {
        return credentialsPrefs.retrieveMnemonic()
    }

    override suspend fun retrieveKeyPair(): Sr25519Keypair {
        return credentialsPrefs.retrieveKeys() ?: throw IllegalStateException("Keypair not found")
    }

    override suspend fun retrieveIrohaKeyPair(): KeyPair {
        return credentialsPrefs.retrieveIrohaKeys()
            ?: throw IllegalStateException("Iroha Keypair not found")
    }

    override suspend fun getIrohaAddress(): String {
        return credentialsPrefs.getIrohaAddress().let {
            if (it.isEmpty()) {
                val mn = retrieveMnemonic()
                restoreUserCredentials(mn)
                credentialsPrefs.getIrohaAddress()
            } else {
                it
            }
        }
    }

    override suspend fun getClaimSignature(): String {
        return Hex.toHexString(credentialsPrefs.retrieveSignature())
    }

    override suspend fun getAddress(): String {
        var address = credentialsPrefs.getAddress()
        if (address.isEmpty()) {
            address = credentialsPrefs.retrieveKeys()?.publicKey?.toSoraAddress()
                ?: throw IllegalStateException("Public key not found")
            credentialsPrefs.saveAddress(address)
            FirebaseWrapper.log("Address recreated ${address.isNotEmpty()}")
        }
        return address
    }

    override suspend fun getAccountId(): ByteArray = getAddress().toAccountId()

    override suspend fun isAddressOk(address: String): Boolean =
        runCatching { address.toAccountId() }.getOrNull() != null &&
            SS58Encoder.extractAddressByte(address) == RuntimeHolder.prefix
}
