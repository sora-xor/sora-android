package jp.co.soramitsu.xsubstrate.encrypt.keypair.ethereum

import jp.co.soramitsu.xcrypto.encryption.Keypair
import jp.co.soramitsu.xcrypto.encryption.KeypairWithSeed
import jp.co.soramitsu.xcrypto.encryption.ecdsa.ECDSAUtils
import jp.co.soramitsu.xcrypto.encryption.ecdsa.derivePublicKey
import jp.co.soramitsu.xsubstrate.encrypt.junction.Junction
import jp.co.soramitsu.xsubstrate.encrypt.keypair.generate

object EthereumKeypairFactory {
    fun generate(
        seed: ByteArray,
        junctions: List<Junction>,
    ): Keypair {
        return Bip32KeypairFactory.generate(seed, junctions)
    }

    fun createWithPrivateKey(privateKeyBytes: ByteArray): Keypair {
        return KeypairWithSeed(
            seed = privateKeyBytes,
            privateKey = privateKeyBytes,
            publicKey = ECDSAUtils.derivePublicKey(privateKeyOrSeed = privateKeyBytes),
        )
    }
}
