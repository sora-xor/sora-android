package jp.co.soramitsu.common.data.credentials.repository

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.data.network.substrate.SubstrateNetworkOptionsProvider
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

    override fun isMnemonicValid(mnemonic: String): Single<Boolean> {
        return Single.fromCallable { cryptoAssistant.bip39.isMnemonicValid(mnemonic) }
    }

    override fun generateUserCredentials(): Completable {
        return generateMnemonic()
            .map { generateEntropyAndKeys(it.joinToString(" ")) }
            .ignoreElement()
    }

    private fun generateEntropyAndKeys(mnemonic: String) {
        val entropy = cryptoAssistant.bip39.generateEntropy(mnemonic)
        val seed = cryptoAssistant.bip39.generateSeed(entropy, "")
        val keyPair = cryptoAssistant.keyPairFactory.generate(
            SubstrateNetworkOptionsProvider.encryptionType,
            seed
        )
        credentialsPrefs.saveAddress(keyPair.publicKey.toSoraAddress())
        credentialsPrefs.saveKeys(keyPair)
        credentialsPrefs.saveMnemonic(mnemonic)
        FirebaseCrashlytics.getInstance().log("Keys were created")
    }

    private fun generateMnemonic(): Single<List<String>> =
        Single.fromCallable {
            cryptoAssistant.bip39.generateMnemonic(MnemonicLength.TWELVE).split(" ")
        }

    private fun generateAndSaveClaimData(mnemonic: String): Completable {
        return Completable.fromCallable {
            val projectName = "SORA"
            val purpose = "iroha keypair"

            val entropy =
                Normalizer.normalize(mnemonic, Normalizer.Form.NFKD).toByteArray(charset("UTF-8"))
            val seed =
                cryptoAssistant.generateScryptSeedForEd25519(entropy, projectName, purpose, "")
            val keys = cryptoAssistant.generateEd25519Keys(seed)
            val did = "did:sora:${Hex.toHexString(keys.public.encoded).substring(0, 20)}"
            val irohaAddress = did.didToAccountId()
            val message = irohaAddress + Hex.toHexString(keys.public.encoded)

            val signature = cryptoAssistant.signEd25519(message.toByteArray(charset("UTF-8")), keys)
            credentialsPrefs.saveIrohaAddress(irohaAddress)
            credentialsPrefs.saveIrohaKeys(keys)
            credentialsPrefs.saveSignature(signature)
        }
    }

    override fun restoreUserCredentials(mnemonic: String): Completable {
        return Completable.fromCallable {
            generateEntropyAndKeys(mnemonic)
        }.andThen(generateAndSaveClaimData(mnemonic))
    }

    override fun saveMnemonic(mnemonic: String) {
        credentialsPrefs.saveMnemonic(mnemonic)
    }

    override fun retrieveMnemonic(): Single<String> {
        return Single.fromCallable { credentialsPrefs.retrieveMnemonic() }
    }

    override fun retrieveKeyPair(): Single<Keypair> {
        return Single.fromCallable { credentialsPrefs.retrieveKeys() }
    }

    override fun retrieveIrohaKeyPair(): Single<KeyPair> {
        return Single.fromCallable { credentialsPrefs.retrieveIrohaKeys() }
    }

    override fun getIrohaAddress(): Single<String> {
        return getIrohaAddressInternal().flatMap {
            if (it.isEmpty()) {
                retrieveMnemonic().flatMapCompletable { mnemonic ->
                    restoreUserCredentials(mnemonic)
                }.andThen(getIrohaAddressInternal())
            } else {
                Single.just(it)
            }
        }
    }

    private fun getIrohaAddressInternal(): Single<String> =
        Single.fromCallable { credentialsPrefs.getIrohaAddress() }

    override fun getClaimSignature(): Single<String> {
        return Single.fromCallable { Hex.toHexString(credentialsPrefs.retrieveSignature()) }
    }

    override fun getAddress(): Single<String> {
        return Single.fromCallable {
            var address = credentialsPrefs.getAddress()
            if (address.isEmpty()) {
                address = credentialsPrefs.retrieveKeys()?.publicKey?.toSoraAddress()
                    ?: throw IllegalStateException("Public key not found")
                credentialsPrefs.saveAddress(address)
            }
            address
        }
    }

    override fun getAddressId(): Single<ByteArray> = getAddress().map { it.toAccountId() }

    override fun isAddressOk(address: String): Single<Boolean> =
        Single.fromCallable {
            kotlin.runCatching { address.toAccountId() }.getOrNull() != null &&
                SS58Encoder.extractAddressByte(address) == RuntimeHolder.prefix
        }
}
