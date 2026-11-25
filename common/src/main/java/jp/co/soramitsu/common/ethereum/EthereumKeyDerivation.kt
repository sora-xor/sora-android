package jp.co.soramitsu.common.ethereum

import java.math.BigInteger
import java.nio.ByteBuffer
import java.text.Normalizer
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import jp.co.soramitsu.common.account.RecoveryPhrase

/**
 * The Ethereum account of a SORA wallet's recovery phrase: the account MetaMask and every other
 * BIP-44 wallet open for the same words.
 *
 * - BIP-39 seed: PBKDF2-HMAC-SHA512 of the phrase text (NFKD, words joined by single spaces),
 *   salt "mnemonic", 2048 rounds, 64 bytes. This is not the SORA2 (Substrate) mini-secret, which
 *   is derived from the phrase entropy.
 * - BIP-32 secp256k1 path `m/44'/60'/0'/0/0`.
 * - Address: last 20 bytes of keccak256 of the uncompressed public key without its 0x04 prefix.
 *
 * Every byte array of secret material made here is wiped before it is dropped; the caller wipes
 * the private key it receives ([DerivedEthereumKey.destroy]). `BigInteger` copies made by the curve
 * arithmetic cannot be wiped on the JVM; they are short-lived and never stored.
 */
object EthereumKeyDerivation {

    const val DEFAULT_PATH = "m/44'/60'/0'/0/0"

    private const val HARDENED = 0x80000000L
    private const val BIP39_ROUNDS = 2048
    private const val BIP39_SEED_BITS = 512
    private val BIP32_MASTER_KEY = "Bitcoin seed".toByteArray(Charsets.US_ASCII)

    class DerivedEthereumKey internal constructor(
        val privateKey: ByteArray,
        /** 65 bytes, `0x04 ‖ X ‖ Y`. */
        val publicKey: ByteArray,
        val address: ByteArray,
    ) {
        val checksumAddress: String get() = EthAddress.checksum(address)

        fun destroy() {
            privateKey.fill(0)
        }
    }

    /**
     * Derives the account of [mnemonic]. Throws [IllegalArgumentException] when it is not a valid
     * BIP-39 phrase. The words are read by the wallet's own phrase parser, so separators and
     * spacing in the stored text do not matter.
     */
    fun derive(mnemonic: String, path: String = DEFAULT_PATH, passphrase: String = ""): DerivedEthereumKey {
        val phrase = requireNotNull(RecoveryPhrase.parseOrNull(mnemonic)) { "ETHEREUM_PHRASE_INVALID" }
        val seed = bip39Seed(phrase.text, passphrase)
        return try {
            deriveFromSeed(seed, path)
        } finally {
            seed.fill(0)
        }
    }

    /** BIP-39 seed of a phrase text (64 bytes). The caller wipes it. */
    fun bip39Seed(phraseText: String, passphrase: String = ""): ByteArray {
        val password = Normalizer.normalize(phraseText, Normalizer.Form.NFKD).toCharArray()
        val salt = ("mnemonic" + Normalizer.normalize(passphrase, Normalizer.Form.NFKD)).toByteArray(Charsets.UTF_8)
        val spec = PBEKeySpec(password, salt, BIP39_ROUNDS, BIP39_SEED_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).encoded
        } finally {
            password.fill('\u0000')
            salt.fill(0)
            spec.clearPassword()
        }
    }

    fun deriveFromSeed(seed: ByteArray, path: String = DEFAULT_PATH): DerivedEthereumKey {
        require(seed.size in 16..64) { "BIP32_SEED_INVALID" }
        var digest = hmacSha512(BIP32_MASTER_KEY, seed)
        var key = digest.copyOfRange(0, 32)
        var chainCode = digest.copyOfRange(32, 64)
        digest.fill(0)
        try {
            require(Secp256k1.isValidPrivateKey(key)) { "BIP32_MASTER_KEY_INVALID" }
            parsePath(path).forEach { index ->
                val data = if (index >= HARDENED) {
                    ByteArray(1) + key
                } else {
                    Secp256k1.publicKeyCompressed(key)
                }
                val input = data + ByteBuffer.allocate(4).putInt(index.toInt()).array()
                digest = hmacSha512(chainCode, input)
                data.fill(0)
                input.fill(0)
                val tweak = digest.copyOfRange(0, 32)
                val tweakValue = BigInteger(1, tweak)
                tweak.fill(0)
                // BIP-32: such an index is invalid and the next one is meant; it happens with a
                // probability below 2^-127, so the derivation refuses instead of skipping.
                require(tweakValue < Secp256k1.order) { "BIP32_CHILD_KEY_INVALID" }
                val child = tweakValue.add(BigInteger(1, key)).mod(Secp256k1.order)
                require(child.signum() != 0) { "BIP32_CHILD_KEY_INVALID" }
                key.fill(0)
                chainCode.fill(0)
                key = EthHex.toFixed(child, 32)
                chainCode = digest.copyOfRange(32, 64)
                digest.fill(0)
            }
            val publicKey = Secp256k1.publicKeyUncompressed(key)
            return DerivedEthereumKey(
                privateKey = key,
                publicKey = publicKey,
                address = EthAddress.fromPublicKey(publicKey),
            )
        } catch (error: Throwable) {
            key.fill(0)
            throw error
        } finally {
            chainCode.fill(0)
            digest.fill(0)
        }
    }

    /** `m/a'/b/…`: each component below 2^31, `'` (or `h`) marks a hardened index. */
    internal fun parsePath(path: String): List<Long> {
        require(path.startsWith("m/")) { "BIP32_PATH_INVALID" }
        return path.removePrefix("m/").split('/').map { component ->
            val hardened = component.endsWith("'") || component.endsWith("h")
            val digits = if (hardened) component.dropLast(1) else component
            require(digits.isNotEmpty() && digits.all(Char::isDigit) && digits.length <= 10) { "BIP32_PATH_INVALID" }
            val value = digits.toLong()
            require(value < HARDENED) { "BIP32_PATH_INVALID" }
            if (hardened) value + HARDENED else value
        }
    }

    private fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA512").run {
            init(SecretKeySpec(key, "HmacSHA512"))
            doFinal(data)
        }
}
