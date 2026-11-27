package jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate

import jp.co.soramitsu.xcrypto.encryption.Keypair
import jp.co.soramitsu.xsubstrate.encrypt.EncryptionType
import jp.co.soramitsu.xsubstrate.encrypt.junction.Junction
import jp.co.soramitsu.xsubstrate.encrypt.keypair.generate

object SubstrateKeypairFactory {
    fun generate(
        encryptionType: EncryptionType,
        seed: ByteArray,
        junctions: List<Junction> = emptyList(),
    ): Keypair =
        when (encryptionType) {
            EncryptionType.SR25519 -> Sr25519SubstrateKeypairFactory.generate(seed, junctions)
            EncryptionType.ED25519 -> Ed25519SubstrateKeypairFactory.generate(seed, junctions)
            EncryptionType.ECDSA -> ECDSASubstrateKeypairFactory.generate(seed, junctions)
        }
}
