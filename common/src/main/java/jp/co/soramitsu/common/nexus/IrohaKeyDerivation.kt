package jp.co.soramitsu.common.nexus

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.text.Normalizer
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import jp.co.soramitsu.common.account.RecoveryPhrase
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

    /**
     * The text of the recovery phrase that is fed to PBKDF2 as the BIP-39 password. A Nexus
     * identity is derived from this text, so two texts with the same words but different
     * separators are two different identities.
     */
    enum class PhraseText {
        /**
         * The parsed words joined by single spaces (library parser: NFKD, separators `[\s,]+`).
         * Every NEW derivation uses this, so the same words always derive the same identity
         * however the phrase was typed or stored.
         */
        CANONICAL,

        /**
         * The stored text as builds before this rule fed it: split on whitespace only and
         * joined by single spaces, so a comma typed after a word stays part of the password.
         * Used only to re-derive a Nexus account that already exists and was derived this way
         * ([deriveExisting]); it is never used for a new account.
         */
        AS_FIRST_DERIVED,
    }

    /** Derives a NEW Nexus account from the canonical phrase text. */
    fun derive(
        mnemonic: String,
        network: NexusNetwork,
        passphrase: String = "",
    ): DerivedAccount = derive(mnemonic, network.derivationProfile, passphrase)

    fun derive(
        mnemonic: String,
        profile: NexusDerivationProfile,
        passphrase: String = "",
        phraseText: PhraseText = PhraseText.CANONICAL,
    ): DerivedAccount {
        // Parsed once with the library parser, the same reading the SORA2 derivation uses.
        val phrase = requireNotNull(RecoveryPhrase.parseOrNull(mnemonic)) {
            "Nexus master phrase has an invalid BIP-39 checksum"
        }
        require(phrase.wordCount == 12 || phrase.wordCount == 24) {
            "Nexus master phrase must contain 12 or 24 words"
        }
        val text = when (phraseText) {
            PhraseText.CANONICAL -> phrase.text
            PhraseText.AS_FIRST_DERIVED -> {
                val words = mnemonic.trim().split(Regex("\\s+")).filter(String::isNotBlank)
                require(words.size == 12 || words.size == 24) {
                    "Nexus master phrase must contain 12 or 24 words"
                }
                words.joinToString(" ")
            }
        }

        val normalizedMnemonic = Normalizer.normalize(text, Normalizer.Form.NFKD)
        val normalizedPassphrase = Normalizer.normalize(passphrase, Normalizer.Form.NFKD)
        val password = normalizedMnemonic.toCharArray()
        val salt = "mnemonic$normalizedPassphrase".toByteArray(Charsets.UTF_8)
        val spec = PBEKeySpec(password, salt, BIP39_ROUNDS, BIP39_SEED_BITS)
        val seed = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            .generateSecret(spec)
            .encoded
        return try {
            val derived = derivePrivateKey(seed, profile.derivationPath)
            val publicKey = Ed25519PrivateKeyParameters(derived.first, 0).generatePublicKey().encoded
            DerivedAccount(
                network = profile.id,
                derivationPath = profile.derivationPath,
                privateKeySeed = derived.first,
                chainCode = derived.second,
                publicKey = publicKey,
                address = IrohaAddressCodec.encode(publicKey, profile.chainDiscriminant),
            )
        } finally {
            seed.fill(0)
            password.fill('\u0000')
            salt.fill(0)
            spec.clearPassword()
        }
    }

    /**
     * Re-derives a Nexus account that already exists, for signing. An existing account keeps
     * the identity it was first derived with: the canonical text is tried first and, when it
     * does not derive the stored account, the text form of earlier builds. Returns the account
     * whose address and public key are exactly the stored ones, or null when the phrase derives
     * neither (the caller must then refuse to sign; nothing is re-derived or replaced).
     */
    fun deriveExisting(
        mnemonic: String,
        profile: NexusDerivationProfile,
        existingAddress: String,
        existingPublicKey: ByteArray,
        passphrase: String = "",
    ): DerivedAccount? {
        PhraseText.entries.forEach { phraseText ->
            val derived = try {
                derive(mnemonic, profile, passphrase, phraseText)
            } catch (_: IllegalArgumentException) {
                return@forEach
            }
            if (
                derived.address == existingAddress &&
                MessageDigest.isEqual(derived.publicKey, existingPublicKey)
            ) {
                return derived
            }
            derived.destroy()
        }
        return null
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
