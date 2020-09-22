package jp.co.soramitsu.common.util

import com.fasterxml.jackson.databind.ObjectMapper
import io.reactivex.Single
import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
import jp.co.soramitsu.crypto.ed25519.EdDSAPrivateKey
import jp.co.soramitsu.sora.sdk.crypto.json.JSONEd25519Sha3SignatureSuite
import jp.co.soramitsu.sora.sdk.did.model.dto.DDO
import jp.co.soramitsu.sora.sdk.did.model.dto.Options
import jp.co.soramitsu.sora.sdk.did.model.type.SignatureTypeEnum
import org.spongycastle.crypto.generators.SCrypt
import org.spongycastle.util.encoders.Hex
import java.security.KeyPair
import java.security.SecureRandom
import java.util.Date

class CryptoAssistant(
    private val secureRandom: SecureRandom,
    private val objectMapper: ObjectMapper,
    private val signatureSuite: JSONEd25519Sha3SignatureSuite,
    private val ed25519Sha3: Ed25519Sha3
) {

    companion object {
        private const val CHARSET = "UTF-8"
    }

    fun getSecureRandom(length: Int): Single<ByteArray> {
        return Single.fromCallable {
            val e = ByteArray(length)
            secureRandom.nextBytes(e)
            e
        }
    }

    fun signDDO(keyPair: KeyPair, ddo: DDO): Single<DDO> {
        return getSecureRandom(8)
            .flatMap { nonce -> signDDO(nonce, ddo, keyPair) }
    }

    private fun signDDO(nonce: ByteArray, ddo: DDO, keyPair: KeyPair): Single<DDO> {
        return Single.fromCallable {
            val options = Options.builder()
                .type(SignatureTypeEnum.Ed25519Sha3Signature)
                .nonce(Hex.toHexString(nonce))
                .creator(ddo.id.withFragment("keys-1"))
                .created(Date())
                .build()
            val singedDdo = signatureSuite.sign(ddo, keyPair.private as EdDSAPrivateKey, options)
            objectMapper.readValue(singedDdo.toString(), DDO::class.java)
        }
    }

    fun generateScryptSeed(entropy: ByteArray, project: String, purpose: String, password: String): Single<ByteArray> {
        return Single.fromCallable {
            val salt = StringBuilder()
                .append(project)
                .append("|")
                .append(purpose)
                .append("|")
                .append(password)
                .toString()

            SCrypt.generate(entropy, salt.toByteArray(charset(CHARSET)), 16384, 8, 1, 32)
        }
    }

    fun generateKeys(seed: ByteArray): Single<KeyPair> {
        return Single.just(ed25519Sha3.generateKeypair(seed))
    }

    fun getProofKeyFromDdo(ddo: DDO): ByteArray {
        var publicKeyByte = ByteArray(0)
        for (publicKey in ddo.publicKey) {
            if (publicKey.id == ddo.proof.options.creator) {
                publicKeyByte = publicKey.publicKey
            }
        }
        return publicKeyByte
    }

    fun getKeypairFromBytes(privateKeyBytes: ByteArray, publicKeyBytes: ByteArray): KeyPair {
        return Ed25519Sha3.keyPairFromBytes(privateKeyBytes, publicKeyBytes)
    }
}