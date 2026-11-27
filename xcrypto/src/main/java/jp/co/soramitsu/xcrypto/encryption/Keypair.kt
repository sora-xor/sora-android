package jp.co.soramitsu.xcrypto.encryption

interface Keypair {
    val privateKey: ByteArray
    val publicKey: ByteArray
}

class BaseKeypair(
    override val privateKey: ByteArray,
    override val publicKey: ByteArray
) : Keypair
