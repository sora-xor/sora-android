package jp.co.soramitsu.xcrypto.encryption

class KeypairWithSeed(
    val seed: ByteArray,
    override val privateKey: ByteArray,
    override val publicKey: ByteArray
) : Keypair
