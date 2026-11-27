package jp.co.soramitsu.xsubstrate.encrypt.json.coders.content.secretcoder

import jp.co.soramitsu.xcrypto.encryption.Keypair
import jp.co.soramitsu.xcrypto.encryption.sr25519.Sr25519JNI
import jp.co.soramitsu.xsubstrate.encrypt.EncryptionType
import jp.co.soramitsu.xsubstrate.encrypt.MultiChainEncryption
import jp.co.soramitsu.xsubstrate.encrypt.json.coders.content.JsonContentDecoder
import jp.co.soramitsu.xsubstrate.encrypt.json.coders.content.JsonSecretCoder
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair

@Suppress("MagicNumber")
object Sr25519JsonSecretCoder : JsonSecretCoder {
    override fun encode(
        keypair: Keypair,
        seed: ByteArray?,
    ): List<ByteArray> {
        require(keypair is Sr25519Keypair)

        val ed25519BytesSecret = Sr25519JNI.toEd25519Bytes(keypair.privateKey + keypair.nonce)

        return listOf(ed25519BytesSecret, keypair.publicKey)
    }

    override fun decode(data: List<ByteArray>): JsonContentDecoder.SecretDecoder.DecodedSecret {
        require(data.size == 2) { "Unknown secret format. Size: ${data.size}." }

        val (privateKeyCompressed, publicKey) = data

        val privateAndNonce = Sr25519JNI.fromEd25519Bytes(privateKeyCompressed)

        val keypair =
            Sr25519Keypair(
                privateAndNonce.copyOfRange(0, 32),
                publicKey,
                privateAndNonce.copyOfRange(32, 64),
            )

        return JsonContentDecoder.SecretDecoder.DecodedSecret(
            seed = null,
            multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.SR25519),
            keypair = keypair,
        )
    }
}
