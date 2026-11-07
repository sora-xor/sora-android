package jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate

import jp.co.soramitsu.xcrypto.encryption.Keypair
import jp.co.soramitsu.xcrypto.encryption.sr25519.Sr25519JNI
import jp.co.soramitsu.xsubstrate.encrypt.junction.Junction
import jp.co.soramitsu.xsubstrate.encrypt.junction.JunctionType
import jp.co.soramitsu.xsubstrate.encrypt.keypair.KeypairFactory

class Sr25519Keypair(
    override val privateKey: ByteArray,
    override val publicKey: ByteArray,
    val nonce: ByteArray,
) : Keypair

@Suppress("MagicNumber")
internal object Sr25519SubstrateKeypairFactory : KeypairFactory<Sr25519Keypair> {
    override fun deriveFromSeed(seed: ByteArray): Sr25519Keypair {
        val keypairBytes = Sr25519JNI.keypairFromSeed(seed)

        return decodeSr25519Keypair(keypairBytes)
    }

    override fun deriveChild(
        parent: Sr25519Keypair,
        junction: Junction,
    ): Sr25519Keypair {
        return when (junction.type) {
            JunctionType.SOFT -> deriveSr25519SoftKeypair(junction.chaincode, parent)
            JunctionType.HARD -> deriveSr25519HardKeypair(junction.chaincode, parent)
        }
    }

    private fun deriveSr25519SoftKeypair(
        chaincode: ByteArray,
        previousKeypair: Sr25519Keypair,
    ): Sr25519Keypair {
        val keypair = previousKeypair.privateKey + previousKeypair.nonce + previousKeypair.publicKey
        val newKeypairbytes = Sr25519JNI.deriveKeypairSoft(keypair, chaincode)

        return decodeSr25519Keypair(newKeypairbytes)
    }

    private fun deriveSr25519HardKeypair(
        chaincode: ByteArray,
        previousKeypair: Sr25519Keypair,
    ): Sr25519Keypair {
        val keypair = previousKeypair.privateKey + previousKeypair.nonce + previousKeypair.publicKey
        val newKeypairbytes = Sr25519JNI.deriveKeypairHard(keypair, chaincode)

        return decodeSr25519Keypair(newKeypairbytes)
    }

    private fun decodeSr25519Keypair(bytes: ByteArray): Sr25519Keypair {
        val privateKey = bytes.copyOfRange(0, 32)
        val nonce = bytes.copyOfRange(32, 64)
        val publicKey = bytes.copyOfRange(64, bytes.size)
        return Sr25519Keypair(
            privateKey,
            publicKey,
            nonce,
        )
    }
}
