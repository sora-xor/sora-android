package jp.co.soramitsu.common.nexus

import java.nio.ByteBuffer
import java.text.Normalizer
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import jp.co.soramitsu.xcrypto.seed.MnemonicCreator
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters

/**
 * BIP-39 + SLIP-0010 Ed25519 derivation used for SORA Nexus accounts.
 *
 * Existing SORA2 key derivation is intentionally not routed through this class.
 */
object IrohaKeyDerivation {
    private const val HARDENED_OFFSET = 0x80000000L
    private const val BIP39_ROUNDS = 2048
    private const val BIP39_SEED_BITS = 512
    private val masterKey = "ed25519 seed".toByteArray(Charsets.UTF_8)

    data class DerivedAccount(
        val network: WalletNetworkId,
        val derivationPath: String,
        val privateKeySeed: ByteArray,
        val chainCode: ByteArray,
        val publicKey: ByteArray,
        val address: String,
    ) {
        init {
            require(privateKeySeed.size == 32)
            require(chainCode.size == 32)
            require(publicKey.size == 32)
        }

        fun destroy() {
            privateKeySeed.fill(0)
            chainCode.fill(0)
        }
    }

    fun derive(
        mnemonic: String,
        network: NexusNetwork,
        passphrase: String = "",
    ): DerivedAccount {
        val words = mnemonic.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        require(words.size == 12 || words.size == 24) {
            "Nexus master phrase must contain 12 or 24 words"
        }

        val normalizedMnemonic = Normalizer.normalize(words.joinToString(" "), Normalizer.Form.NFKD)
        require(
            runCatching {
                MnemonicCreator.fromWords(normalizedMnemonic)
            }.isSuccess
        ) {
            "Nexus master phrase has an invalid BIP-39 checksum"
        }
        val normalizedPassphrase = Normalizer.normalize(passphrase, Normalizer.Form.NFKD)
        val password = normalizedMnemonic.toCharArray()
        val salt = "mnemonic$normalizedPassphrase".toByteArray(Charsets.UTF_8)
        val spec = PBEKeySpec(password, salt, BIP39_ROUNDS, BIP39_SEED_BITS)
        val seed = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            .generateSecret(spec)
            .encoded
        return try {
            val derived = derivePrivateKey(seed, network.derivationPath)
            val publicKey = Ed25519PrivateKeyParameters(derived.first, 0).generatePublicKey().encoded
            DerivedAccount(
                network = network.id,
                derivationPath = network.derivationPath,
                privateKeySeed = derived.first,
                chainCode = derived.second,
                publicKey = publicKey,
                address = IrohaAddressCodec.encode(publicKey, network.chainDiscriminant),
            )
        } finally {
            seed.fill(0)
            password.fill('\u0000')
            salt.fill(0)
            spec.clearPassword()
        }
    }

    internal fun derivePrivateKey(seed: ByteArray, path: String): Pair<ByteArray, ByteArray> {
        require(seed.isNotEmpty())
        var digest = hmac(masterKey, seed)
        var privateKey = digest.copyOfRange(0, 32)
        var chainCode = digest.copyOfRange(32, 64)
        digest.fill(0)

        parsePath(path).forEach { index ->
            val data = ByteArray(37)
            data[0] = 0
            privateKey.copyInto(data, 1)
            ByteBuffer.allocate(4)
                .putInt((index + HARDENED_OFFSET).toInt())
                .array()
                .copyInto(data, 33)
            digest = hmac(chainCode, data)
            privateKey.fill(0)
            chainCode.fill(0)
            data.fill(0)
            privateKey = digest.copyOfRange(0, 32)
            chainCode = digest.copyOfRange(32, 64)
            digest.fill(0)
        }

        return privateKey to chainCode
    }

    private fun parsePath(path: String): List<Long> {
        require(path.startsWith("m/"))
        return path.removePrefix("m/").split('/').map { component ->
            require(component.endsWith("'")) { "Ed25519 derivation requires hardened components" }
            component.dropLast(1).toLong().also {
                require(it in 0..0x7fffffff)
            }
        }
    }

    private fun hmac(key: ByteArray, value: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA512").run {
            init(SecretKeySpec(key, "HmacSHA512"))
            doFinal(value)
        }
}
