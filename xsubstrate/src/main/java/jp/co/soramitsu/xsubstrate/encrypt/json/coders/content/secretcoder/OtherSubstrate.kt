package jp.co.soramitsu.xsubstrate.encrypt.json.coders.content.secretcoder

import jp.co.soramitsu.xcrypto.encryption.Keypair
import jp.co.soramitsu.xsubstrate.encrypt.EncryptionType
import jp.co.soramitsu.xsubstrate.encrypt.MultiChainEncryption
import jp.co.soramitsu.xsubstrate.encrypt.json.coders.content.JsonContentDecoder
import jp.co.soramitsu.xsubstrate.encrypt.json.coders.content.JsonSecretCoder
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.SubstrateKeypairFactory

internal object EcdsaJsonSecretCoder : OtherSubstrateJsonSecretCoder(EncryptionType.ECDSA)

internal object Ed25519JsonSecretCoder : OtherSubstrateJsonSecretCoder(EncryptionType.ED25519)

private const val SEED_PADDING_SIZE = 32

internal abstract class OtherSubstrateJsonSecretCoder(
    private val encryptionType: EncryptionType,
) : JsonSecretCoder {
    override fun encode(
        keypair: Keypair,
        seed: ByteArray?,
    ): List<ByteArray> {
        requireNotNull(seed) { "Seed cannot be null" }

        return listOf(seed, keypair.publicKey)
    }

    override fun decode(data: List<ByteArray>): JsonContentDecoder.SecretDecoder.DecodedSecret {
        require(data.size == 2) { "Unknown secret structure (size: ${data.size}" }

        val seed = data[0].copyOfRange(0, SEED_PADDING_SIZE) // crop to 32 bytes

        return JsonContentDecoder.SecretDecoder.DecodedSecret(
            seed = seed,
            multiChainEncryption = MultiChainEncryption.Substrate(encryptionType),
            keypair = SubstrateKeypairFactory.generate(encryptionType, seed),
        )
    }
}
