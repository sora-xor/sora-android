package jp.co.soramitsu.xsubstrate.encrypt.json.coders.content.secretcoder

import jp.co.soramitsu.xcrypto.encryption.BaseKeypair
import jp.co.soramitsu.xcrypto.encryption.Keypair
import jp.co.soramitsu.xcrypto.encryption.ecdsa.ECDSAUtils
import jp.co.soramitsu.xcrypto.encryption.ecdsa.derivePublicKey
import jp.co.soramitsu.xsubstrate.encrypt.MultiChainEncryption
import jp.co.soramitsu.xsubstrate.encrypt.json.coders.content.JsonContentDecoder
import jp.co.soramitsu.xsubstrate.encrypt.json.coders.content.JsonSecretCoder

object EthereumJsonSecretCoder : JsonSecretCoder {
    override fun encode(
        keypair: Keypair,
        seed: ByteArray?,
    ): List<ByteArray> {
        return listOf(keypair.privateKey, keypair.publicKey)
    }

    override fun decode(data: List<ByteArray>): JsonContentDecoder.SecretDecoder.DecodedSecret {
        require(data.size == 2) { "Unknown secret structure (size: ${data.size}" }

        val privateKey = data[0]

        return JsonContentDecoder.SecretDecoder.DecodedSecret(
            seed = null,
            multiChainEncryption = MultiChainEncryption.Ethereum,
            keypair =
            BaseKeypair(
                privateKey = privateKey,
                publicKey = ECDSAUtils.derivePublicKey(privateKey),
            ),
        )
    }
}
