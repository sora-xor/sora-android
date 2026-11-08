package jp.co.soramitsu.xsubstrate.encrypt.keypair.ethereum

import jp.co.soramitsu.xcrypto.encryption.Keypair

class Bip32ExtendedKeyPair(
    override val privateKey: ByteArray,
    override val publicKey: ByteArray,
    val chaincode: ByteArray,
) : Keypair
