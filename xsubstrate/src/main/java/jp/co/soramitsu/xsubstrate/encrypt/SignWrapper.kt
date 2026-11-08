package jp.co.soramitsu.xsubstrate.encrypt

import jp.co.soramitsu.xcrypto.encryption.Keypair
import jp.co.soramitsu.xcrypto.encryption.ecdsa.Ecdsa
import jp.co.soramitsu.xcrypto.encryption.ed25519.Ed25519
import jp.co.soramitsu.xcrypto.encryption.sr25519.Sr25519JNI
import jp.co.soramitsu.xcrypto.hash.blake2.blake2b256
import jp.co.soramitsu.xcrypto.hash.keccak256
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair

object SignWrapper {
    private enum class MessageHashing(val hasher: (ByteArray) -> ByteArray) {
        SUBSTRATE(hasher = { it.blake2b256() }),
        ETHEREUM(hasher = { it.keccak256() }),
    }

    fun sign(
        multiChainEncryption: MultiChainEncryption,
        message: ByteArray,
        keypair: Keypair,
    ): SignatureWrapper {
        return when (multiChainEncryption) {
            is MultiChainEncryption.Ethereum -> {
                val signature = Ecdsa.signer(message, keypair.privateKey, MessageHashing.ETHEREUM.hasher)
                SignatureWrapper.Ecdsa(
                    signature.v,
                    signature.r,
                    signature.s,
                )
            }
            is MultiChainEncryption.Substrate -> {
                when (multiChainEncryption.encryptionType) {
                    EncryptionType.SR25519 -> {
                        require(keypair is Sr25519Keypair) {
                            "Sr25519Keypair is needed to sign with SR25519"
                        }

                        val signature = Sr25519JNI.sign(keypair.publicKey, keypair.privateKey + keypair.nonce, message)
                        SignatureWrapper.Sr25519(signature = signature)
                    }

                    EncryptionType.ED25519 -> {
                        val signature = Ed25519.sign(message, keypair.privateKey)
                        SignatureWrapper.Ed25519(signature = signature)
                    }

                    EncryptionType.ECDSA -> {
                        val signature = Ecdsa.signer(message, keypair.privateKey, MessageHashing.SUBSTRATE.hasher)
                        SignatureWrapper.Ecdsa(
                            signature.v,
                            signature.r,
                            signature.s,
                        )
                    }
                }
            }
        }
    }
}
