package jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate

import jp.co.soramitsu.xcrypto.encryption.KeypairWithSeed
import jp.co.soramitsu.xcrypto.encryption.ed25519.Ed25519

internal object Ed25519SubstrateKeypairFactory : OtherSubstrateKeypairFactory("Ed25519HDKD") {
    override fun deriveFromSeed(seed: ByteArray): KeypairWithSeed {
        return Ed25519.deriveFromSeed(seed)
    }
}
