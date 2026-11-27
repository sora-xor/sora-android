package jp.co.soramitsu.xsubstrate.encrypt.keypair

import jp.co.soramitsu.xcrypto.encryption.Keypair
import jp.co.soramitsu.xsubstrate.encrypt.junction.Junction

internal interface KeypairFactory<K : Keypair> {
    class SoftDerivationNotSupported : Exception()

    fun deriveFromSeed(seed: ByteArray): K

    fun deriveChild(
        parent: K,
        junction: Junction,
    ): K
}
