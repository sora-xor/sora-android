package jp.co.soramitsu.xsubstrate.encrypt.keypair

import jp.co.soramitsu.xcrypto.encryption.Keypair
import jp.co.soramitsu.xsubstrate.encrypt.junction.Junction

internal fun <K : Keypair> KeypairFactory<K>.generate(
    seed: ByteArray,
    junctions: List<Junction>,
): K {
    val parentKeypair = deriveFromSeed(seed)

    return junctions.fold(parentKeypair) { currentKeypair, junction ->
        deriveChild(currentKeypair, junction)
    }
}
