package jp.co.soramitsu.xcrypto.seed

import java.text.Normalizer
import java.text.Normalizer.normalize
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.KeyParameter

object SeedCreator {

    private const val SEED_PREFIX = "mnemonic"
    private const val FULL_SEED_LENGTH = 64
    private const val ITERATION_COUNT = 2048

    fun deriveSeed(entropy: ByteArray, passphrase: String? = null): ByteArray {
        val generator = PKCS5S2ParametersGenerator(SHA512Digest())
        generator.init(
            entropy,
            normalize("$SEED_PREFIX${passphrase.orEmpty()}", Normalizer.Form.NFKD).toByteArray(),
            ITERATION_COUNT
        )
        val key = generator.generateDerivedMacParameters(FULL_SEED_LENGTH * Byte.SIZE_BITS) as KeyParameter
        return key.key.copyOfRange(0, FULL_SEED_LENGTH)
    }
}
