/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.credentials.repository

import com.google.firebase.crashlytics.FirebaseCrashlytics
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeHolder
import jp.co.soramitsu.common.data.network.substrate.toSoraAddress
import jp.co.soramitsu.common.domain.credentials.CredentialsDatasource
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.ext.didToAccountId
import jp.co.soramitsu.fearless_utils.bip39.MnemonicLength
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
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
        return cryptoAssistant.bip39.isMnemonicValid(mnemonic)
    }

    override suspend fun generateUserCredentials() {
        val mnemonic = generateMnemonic()
        generateEntropyAndKeys(mnemonic.joinToString(" "))
    }

    private fun generateEntropyAndKeys(mnemonic: String) {
        val entropy = cryptoAssistant.bip39.generateEntropy(mnemonic)
        val seed = cryptoAssistant.bip39.generateSeed(entropy, "")
        val keyPair = cryptoAssistant.keyPairFactory.generate(
            OptionsProvider.encryptionType,
            seed
        )

        credentialsPrefs.saveAddress(keyPair.publicKey.toSoraAddress())
        credentialsPrefs.saveKeys(keyPair)
        credentialsPrefs.saveMnemonic(mnemonic)
        FirebaseCrashlytics.getInstance().log("Keys were created")
    }

    private fun generateMnemonic(): List<String> =
        cryptoAssistant.bip39.generateMnemonic(MnemonicLength.TWELVE).split(" ")

    private fun generateAndSaveClaimData(mnemonic: String) {
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
        generateEntropyAndKeys(mnemonic)
        generateAndSaveClaimData(mnemonic)
    }

    override fun saveMnemonic(mnemonic: String) {
        credentialsPrefs.saveMnemonic(mnemonic)
    }

    override suspend fun retrieveMnemonic(): String {
        return credentialsPrefs.retrieveMnemonic()
    }

    override suspend fun retrieveKeyPair(): Keypair {
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

    override fun getAddress(): String {
        var address = credentialsPrefs.getAddress()
        if (address.isEmpty()) {
            address = credentialsPrefs.retrieveKeys()?.publicKey?.toSoraAddress()
                ?: throw IllegalStateException("Public key not found")
            credentialsPrefs.saveAddress(address)
            FirebaseCrashlytics.getInstance().log("Address recreated ${address.isNotEmpty()}")
        }
        return address
    }

    override fun getAccountId(): ByteArray = getAddress().toAccountId()

    override suspend fun isAddressOk(address: String): Boolean =
        runCatching { address.toAccountId() }.getOrNull() != null &&
            SS58Encoder.extractAddressByte(address) == RuntimeHolder.prefix
}
