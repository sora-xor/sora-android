package jp.co.soramitsu.feature_account_impl.domain.keys

import java.security.MessageDigest
import java.security.SecureRandom
import jp.co.soramitsu.common.account.RecoveryPhrase
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.account.StoredSecretHex
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.xcrypto.encryption.sr25519.Sr25519JNI
import jp.co.soramitsu.xsubstrate.encrypt.MultiChainEncryption
import jp.co.soramitsu.xsubstrate.encrypt.SignWrapper
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.SubstrateKeypairFactory
import kotlinx.coroutines.CancellationException

/** The sr25519 operations the judgement needs. Production uses the packaged native library. */
interface StoredKeyCrypto {
    /** The SORA2 key pair of a 32-byte seed (unchanged derivation), or null for another type. */
    fun generateKeypair(seed: ByteArray): Sr25519Keypair?

    /** Signs [challenge] with [keypair] and verifies the signature against [expectedPublicKey]. */
    fun signAndVerify(
        keypair: Sr25519Keypair,
        challenge: ByteArray,
        expectedPublicKey: ByteArray,
    ): Boolean
}

/** The packaged sr25519 library, exactly as the signer uses it. */
object PackagedStoredKeyCrypto : StoredKeyCrypto {
    override fun generateKeypair(seed: ByteArray): Sr25519Keypair? =
        SubstrateKeypairFactory.generate(
            SubstrateOptionsProvider.encryptionType,
            seed,
        ) as? Sr25519Keypair

    override fun signAndVerify(
        keypair: Sr25519Keypair,
        challenge: ByteArray,
        expectedPublicKey: ByteArray,
    ): Boolean {
        val signature = SignWrapper.sign(
            MultiChainEncryption.Substrate(SubstrateOptionsProvider.encryptionType),
            challenge,
            keypair,
        ).signature
        return try {
            Sr25519JNI.verify(signature, challenge, expectedPublicKey)
        } finally {
            signature.fill(0)
        }
    }
}

/** How one kind of stored backup material relates to the account it is stored for. */
enum class BackupMatch {
    /** It re-derives the account's address, so it is a valid backup of this account. */
    MATCHES,

    /** Stored, but unreadable, malformed or deriving another address. Never shown as a backup. */
    DOES_NOT_MATCH,

    /** Nothing is stored. */
    ABSENT,
}

/**
 * What the secrets stored for one account prove. Computed from the stored values only; nothing
 * is written, and the stored values are never changed to make them agree.
 */
class StoredSecretsVerdict(
    /** Null when the stored signing key can sign for the account; otherwise why it cannot. */
    val signingRefusal: String?,
    val phrase: BackupMatch,
    val seed: BackupMatch,
    /** The parsed stored phrase when it [BackupMatch.MATCHES]. */
    val recoveryPhrase: RecoveryPhrase?,
    /**
     * The 32-byte seed (hex) that re-derives the account, taken from the matching stored seed
     * (cropped when it was stored as 64 bytes) or derived from the matching phrase. Null when
     * neither matches.
     */
    val canonicalSeedHex: String?,
    /** Observations that never change the decision; recorded for support. Never key bytes. */
    val diagnostics: Set<String>,
) {
    /** The stored signing key alone decides this. */
    val canSign: Boolean get() = signingRefusal == null

    /** True when a stored phrase or seed is a valid backup of this account. */
    val hasMatchingBackup: Boolean
        get() = phrase == BackupMatch.MATCHES || seed == BackupMatch.MATCHES

    /**
     * The label recorded for an account that can sign: which stored material is a proven backup.
     * `MNEMONIC` (12 or 24 words; Nexus accounts are derived from it), `MNEMONIC_UNSUPPORTED`
     * (15, 18 or 21 words), `RAW_SEED`, or `LEGACY_SECRET` when only the key pair is usable.
     */
    val secretSource: String
        get() = when {
            phrase == BackupMatch.MATCHES ->
                if (recoveryPhrase?.wordCount == 12 || recoveryPhrase?.wordCount == 24) {
                    SOURCE_MNEMONIC
                } else {
                    SOURCE_MNEMONIC_UNSUPPORTED
                }
            seed == BackupMatch.MATCHES -> SOURCE_RAW_SEED
            else -> SOURCE_LEGACY_SECRET
        }

    companion object {
        const val SOURCE_MNEMONIC = "MNEMONIC"
        const val SOURCE_MNEMONIC_UNSUPPORTED = "MNEMONIC_UNSUPPORTED"
        const val SOURCE_RAW_SEED = "RAW_SEED"
        const val SOURCE_LEGACY_SECRET = "LEGACY_SECRET"
    }
}

/**
 * One rule for every place that judges an account's stored secrets (upgrade verification, adding
 * and restoring accounts, importing a phrase over stored keys, removal, backup screens).
 *
 * **Signing is decided by the stored signing key alone.** The account can sign when
 * 1. a complete key pair is stored (32-byte private scalar, nonce and public key),
 * 2. the stored public key is the public key in the account's SORA2 address (SS58 prefix 69), and
 * 3. a signature made with the stored private key over a random challenge verifies against that
 *    public key. A Schnorr signature only verifies under the public key that belongs to the
 *    private scalar that made it, so this proves the stored public key is the one derived from
 *    the stored private key, and that the key signs.
 *
 * What could sign in the released apps therefore signs here. The phrase and the seed are
 * judged separately, only as backup material: each [BackupMatch.MATCHES] when it re-derives the
 * account's address. A cached seed of 64 bytes (3.6.0.0 to 3.8.2.0), a phrase stored with
 * commas (2.3.x), stored key or nonce bytes that differ from a fresh derivation, or a phrase or
 * seed that belongs to another wallet never stop the account from signing; they only limit what
 * is offered as a backup and are listed in [StoredSecretsVerdict.diagnostics].
 */
class StoredWalletSecretsJudge(
    private val sora2AddressCodec: Sora2AddressCodec,
    private val crypto: StoredKeyCrypto,
) {

    /**
     * @param keyPair the stored key pair, or null when none is stored.
     * @param mnemonic the stored phrase text ("" when absent), or null when it cannot be read.
     * @param storedSeed the stored seed hex ("" when absent), or null when it cannot be read.
     */
    fun judge(
        address: String,
        keyPair: Sr25519Keypair?,
        mnemonic: String?,
        storedSeed: String?,
    ): StoredSecretsVerdict {
        val diagnostics = linkedSetOf<String>()
        val accountPublicKey = sora2AddressCodec.soraPublicKeyOrNull(address)
        val signingRefusal = signingRefusal(address, accountPublicKey, keyPair)

        val phrase = mnemonic?.let(RecoveryPhrase::parseOrNull)
        var phraseKeys: Sr25519Keypair? = null
        var seedKeys: Sr25519Keypair? = null
        var canonicalSeed: ByteArray? = null
        val seedBytes = storedSeed?.takeIf(String::isNotBlank)?.let(StoredSecretHex::decodeOrNull)
        try {
            val phraseMatch = when {
                mnemonic == null -> BackupMatch.DOES_NOT_MATCH.also { diagnostics += "PHRASE_UNREADABLE" }
                mnemonic.isBlank() -> BackupMatch.ABSENT
                phrase == null -> BackupMatch.DOES_NOT_MATCH.also { diagnostics += "PHRASE_NOT_BIP39" }
                else -> {
                    phraseKeys = keysOf(phrase.miniSeed(), wipeSeed = true)
                    if (derivesAccount(phraseKeys, accountPublicKey)) {
                        if (mnemonic != phrase.text) diagnostics += "PHRASE_TEXT_NOT_CANONICAL"
                        BackupMatch.MATCHES
                    } else {
                        BackupMatch.DOES_NOT_MATCH.also { diagnostics += "PHRASE_OTHER_WALLET" }
                    }
                }
            }
            val seedMatch = when {
                storedSeed == null -> BackupMatch.DOES_NOT_MATCH.also { diagnostics += "SEED_UNREADABLE" }
                storedSeed.isBlank() -> BackupMatch.ABSENT
                seedBytes == null -> BackupMatch.DOES_NOT_MATCH.also { diagnostics += "SEED_NOT_HEX" }
                seedBytes.size == RecoveryPhrase.MINI_SEED_BYTES -> {
                    seedKeys = keysOf(seedBytes, wipeSeed = false)
                    if (derivesAccount(seedKeys, accountPublicKey)) {
                        canonicalSeed = seedBytes.copyOf()
                        BackupMatch.MATCHES
                    } else {
                        BackupMatch.DOES_NOT_MATCH.also { diagnostics += "SEED_OTHER_WALLET" }
                    }
                }
                seedBytes.size == RecoveryPhrase.FULL_SEED_BYTES -> {
                    // The uncropped derivation cached by 3.6.0.0 to 3.8.2.0. It is this
                    // account's seed only as the complete 64-byte output of its own phrase.
                    val cropped = if (phraseMatch == BackupMatch.MATCHES && phrase != null) {
                        StoredSecretHex.canonicalSeedOf(seedBytes, phrase)
                    } else {
                        null
                    }
                    if (cropped != null) {
                        canonicalSeed = cropped
                        diagnostics += "SEED_STORED_AS_64_BYTES"
                        BackupMatch.MATCHES
                    } else {
                        BackupMatch.DOES_NOT_MATCH.also { diagnostics += "SEED_64_BYTES_UNPROVEN" }
                    }
                }
                else -> BackupMatch.DOES_NOT_MATCH.also { diagnostics += "SEED_LENGTH_UNSUPPORTED" }
            }
            if (canonicalSeed == null && phraseMatch == BackupMatch.MATCHES && phrase != null) {
                canonicalSeed = phrase.miniSeed()
            }
            // Diagnostic only: the stored key signs, but its bytes are not what a fresh
            // derivation of the matching backup produces today.
            val derived = phraseKeys?.takeIf { phraseMatch == BackupMatch.MATCHES }
                ?: seedKeys?.takeIf { seedMatch == BackupMatch.MATCHES }
            if (signingRefusal == null && keyPair != null && derived != null) {
                if (!MessageDigest.isEqual(keyPair.privateKey, derived.privateKey)) {
                    diagnostics += "PRIVATE_KEY_BYTES_DIFFER_FROM_DERIVATION"
                }
                if (!MessageDigest.isEqual(keyPair.nonce, derived.nonce)) {
                    diagnostics += "NONCE_BYTES_DIFFER_FROM_DERIVATION"
                }
            }
            return StoredSecretsVerdict(
                signingRefusal = signingRefusal,
                phrase = phraseMatch,
                seed = seedMatch,
                recoveryPhrase = phrase.takeIf { phraseMatch == BackupMatch.MATCHES },
                canonicalSeedHex = canonicalSeed?.let(StoredSecretHex::encode),
                diagnostics = diagnostics,
            )
        } finally {
            phraseKeys?.wipe()
            seedKeys?.wipe()
            seedBytes?.fill(0)
            canonicalSeed?.fill(0)
        }
    }

    /** Null when the stored key pair is proven to sign for [address]. */
    private fun signingRefusal(
        address: String,
        accountPublicKey: ByteArray?,
        keyPair: Sr25519Keypair?,
    ): String? {
        if (accountPublicKey == null) return "SORA_ADDRESS_INVALID"
        if (keyPair == null) return "SIGNING_KEY_MISSING"
        if (
            keyPair.privateKey.size != KEY_BYTES ||
            keyPair.nonce.size != KEY_BYTES ||
            keyPair.publicKey.size != KEY_BYTES
        ) {
            return "SIGNING_KEY_INCOMPLETE"
        }
        if (
            !MessageDigest.isEqual(keyPair.publicKey, accountPublicKey) ||
            sora2AddressCodec.toSoraAddressOrNull(keyPair.publicKey) != address
        ) {
            return "SORA_ADDRESS_MISMATCH"
        }
        // A malformed scalar or public key makes the native library throw (it never aborts the
        // process; see Sr25519NativeContractTest), which is reported as a failed proof below.
        val challenge = randomChallenge()
        return try {
            val verified = try {
                crypto.signAndVerify(
                    keypair = keyPair,
                    challenge = challenge,
                    expectedPublicKey = accountPublicKey,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: OutOfMemoryError) {
                throw error
            } catch (_: Throwable) {
                return "SORA_SIGNING_VECTOR_FAILED"
            }
            if (verified) null else "SORA_SIGNING_VECTOR_MISMATCH"
        } finally {
            challenge.fill(0)
        }
    }

    private fun keysOf(seed: ByteArray, wipeSeed: Boolean): Sr25519Keypair? = try {
        crypto.generateKeypair(seed)
    } catch (error: CancellationException) {
        throw error
    } catch (error: OutOfMemoryError) {
        throw error
    } catch (_: Throwable) {
        null
    } finally {
        if (wipeSeed) seed.fill(0)
    }

    private fun derivesAccount(keys: Sr25519Keypair?, accountPublicKey: ByteArray?): Boolean =
        keys != null && accountPublicKey != null &&
            MessageDigest.isEqual(keys.publicKey, accountPublicKey)

    private fun Sr25519Keypair.wipe() {
        privateKey.fill(0)
        nonce.fill(0)
    }

    private fun randomChallenge(): ByteArray {
        val random = ByteArray(CHALLENGE_RANDOM_BYTES).also(SecureRandom()::nextBytes)
        // The fixed prefix keeps the signed bytes from ever being a transaction payload; the
        // signature is wiped after verification and never leaves this process.
        return CHALLENGE_PREFIX.encodeToByteArray() + random
    }

    companion object {
        const val CHALLENGE_PREFIX = "sora-wallet-stored-key-proof-v1:"
        const val CHALLENGE_RANDOM_BYTES = 32
        private const val KEY_BYTES = 32
    }
}
