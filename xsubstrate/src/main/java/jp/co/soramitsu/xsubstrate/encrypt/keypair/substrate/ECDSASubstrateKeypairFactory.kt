package jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate

import jp.co.soramitsu.xcrypto.encryption.KeypairWithSeed
import jp.co.soramitsu.xcrypto.encryption.ecdsa.ECDSAUtils
import jp.co.soramitsu.xcrypto.encryption.ecdsa.derivePublicKey

internal object ECDSASubstrateKeypairFactory : OtherSubstrateKeypairFactory("Secp256k1HDKD") {
    override fun deriveFromSeed(seed: ByteArray): KeypairWithSeed {
        return KeypairWithSeed(
            seed = seed,
            privateKey = seed,
            publicKey = ECDSAUtils.derivePublicKey(privateKeyOrSeed = seed),
        )
    }
}
