package jp.co.soramitsu.xcrypto.encryption.ed25519

import jp.co.soramitsu.xcrypto.encryption.KeypairWithSeed
import org.bouncycastle.math.ec.rfc8032.Ed25519 as BcEd25519

/** RFC 8032 Ed25519. Private keys retain the existing 32-byte seed representation. */
object Ed25519 {
    fun deriveFromSeed(seed: ByteArray): KeypairWithSeed {
        require(seed.size == BcEd25519.SECRET_KEY_SIZE) { "Ed25519 seed must be 32 bytes" }
        val publicKey = ByteArray(BcEd25519.PUBLIC_KEY_SIZE)
        BcEd25519.generatePublicKey(seed, 0, publicKey, 0)
        return KeypairWithSeed(seed.copyOf(), seed.copyOf(), publicKey)
    }

    fun sign(message: ByteArray, privateKey: ByteArray): ByteArray {
        require(privateKey.size == BcEd25519.SECRET_KEY_SIZE) { "Ed25519 private key must be 32 bytes" }
        val signature = ByteArray(BcEd25519.SIGNATURE_SIZE)
        // Derive the public key internally; callers cannot inject a mismatched signing key.
        BcEd25519.sign(privateKey, 0, message, 0, message.size, signature, 0)
        return signature
    }

    fun verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        if (signature.size != BcEd25519.SIGNATURE_SIZE || publicKey.size != BcEd25519.PUBLIC_KEY_SIZE) {
            return false
        }
        // Reject noncanonical/small-order public points in addition to BC's S < L check.
        if (!BcEd25519.validatePublicKeyFull(publicKey, 0)) return false
        return BcEd25519.verify(signature, 0, publicKey, 0, message, 0, message.size)
    }
}
