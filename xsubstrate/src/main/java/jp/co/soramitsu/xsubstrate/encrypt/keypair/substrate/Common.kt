package jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate

import jp.co.soramitsu.xcrypto.encryption.KeypairWithSeed
import jp.co.soramitsu.xcrypto.hash.blake2.blake2b256
import jp.co.soramitsu.xsubstrate.encrypt.junction.Junction
import jp.co.soramitsu.xsubstrate.encrypt.junction.JunctionType
import jp.co.soramitsu.xsubstrate.encrypt.keypair.KeypairFactory
import jp.co.soramitsu.xsubstrate.scale.datatype.string
import jp.co.soramitsu.xsubstrate.scale.datatype.toByteArray

abstract class OtherSubstrateKeypairFactory(
    private val hardDerivationPrefix: String,
) : KeypairFactory<KeypairWithSeed> {
    override fun deriveChild(
        parent: KeypairWithSeed,
        junction: Junction,
    ): KeypairWithSeed {
        if (junction.type == JunctionType.HARD) {
            val prefix = string.toByteArray(hardDerivationPrefix)

            val newSeed = (prefix + parent.seed + junction.chaincode).blake2b256()

            return deriveFromSeed(newSeed)
        } else {
            throw KeypairFactory.SoftDerivationNotSupported()
        }
    }
}
