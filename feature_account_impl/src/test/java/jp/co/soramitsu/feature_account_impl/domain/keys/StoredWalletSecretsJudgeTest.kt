package jp.co.soramitsu.feature_account_impl.domain.keys

import jp.co.soramitsu.common.account.RecoveryPhrase
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.account.StoredSecretHex
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decision rule itself. Key pairs are real sr25519 key pairs (see [FakeStoredKeyCrypto]) and
 * the public key is computed from the stored private key by the production code; only the native
 * sign-and-verify call is stood in for. The real library runs in WalletUpgradeScenarioTest on a
 * device.
 */
class StoredWalletSecretsJudgeTest {

    private val codec = Sora2AddressCodec()
    private val crypto = FakeStoredKeyCrypto()
    private val judge = StoredWalletSecretsJudge(codec, crypto)

    private val phrase = requireNotNull(RecoveryPhrase.parseOrNull(TWELVE))
    private val keys = crypto.generateKeypair(phrase.miniSeed())
    private val address = requireNotNull(codec.toSoraAddressOrNull(keys.publicKey))
    private val seedHex = StoredSecretHex.encode(phrase.miniSeed())
    private val fullSeedHex = StoredSecretHex.encode(phrase.fullSeed())

    @Test
    fun `key pair, phrase and 32 byte seed that agree`() {
        val verdict = judge.judge(address, keys.copy(), TWELVE, seedHex)
        assertTrue(verdict.canSign)
        assertEquals(BackupMatch.MATCHES, verdict.phrase)
        assertEquals(BackupMatch.MATCHES, verdict.seed)
        assertEquals("MNEMONIC", verdict.secretSource)
        assertEquals(seedHex, verdict.canonicalSeedHex)
        assertTrue(verdict.diagnostics.isEmpty())
    }

    @Test
    fun `a 64 byte cached seed of the same phrase does not stop signing and reads as 32 bytes`() {
        val verdict = judge.judge(address, keys.copy(), TWELVE, fullSeedHex)
        assertTrue(verdict.canSign)
        assertEquals(BackupMatch.MATCHES, verdict.seed)
        assertEquals(seedHex, verdict.canonicalSeedHex)
        assertEquals("MNEMONIC", verdict.secretSource)
        assertTrue("SEED_STORED_AS_64_BYTES" in verdict.diagnostics)
        // Upper case and a 0X prefix name the same bytes.
        val upper = judge.judge(address, keys.copy(), TWELVE, "0X" + fullSeedHex.uppercase())
        assertEquals(BackupMatch.MATCHES, upper.seed)
        assertEquals(seedHex, upper.canonicalSeedHex)
    }

    @Test
    fun `64 bytes with other trailing bytes are not a backup but never stop signing`() {
        val tampered = fullSeedHex.dropLast(2) + if (fullSeedHex.endsWith("00")) "01" else "00"
        val verdict = judge.judge(address, keys.copy(), TWELVE, tampered)
        assertTrue(verdict.canSign)
        assertEquals(BackupMatch.MATCHES, verdict.phrase)
        assertEquals(BackupMatch.DOES_NOT_MATCH, verdict.seed)
        // The phrase is still the backup, and the seed offered is the phrase's own.
        assertEquals(seedHex, verdict.canonicalSeedHex)
        // Without its phrase a 64 byte value cannot be proven at all.
        val alone = judge.judge(address, keys.copy(), "", fullSeedHex)
        assertTrue(alone.canSign)
        assertEquals(BackupMatch.DOES_NOT_MATCH, alone.seed)
        assertNull(alone.canonicalSeedHex)
        assertEquals("LEGACY_SECRET", alone.secretSource)
        assertFalse(alone.hasMatchingBackup)
    }

    @Test
    fun `a phrase stored with commas is the same phrase`() {
        listOf(TWELVE.replace(" ", ","), TWELVE.replace(" ", ", "), "  $TWELVE\n").forEach { stored ->
            val verdict = judge.judge(address, keys.copy(), stored, "")
            assertTrue(stored, verdict.canSign)
            assertEquals(stored, BackupMatch.MATCHES, verdict.phrase)
            assertEquals(stored, "MNEMONIC", verdict.secretSource)
            assertEquals(TWELVE.split(" "), verdict.recoveryPhrase?.words)
            assertTrue("PHRASE_TEXT_NOT_CANONICAL" in verdict.diagnostics)
        }
    }

    @Test
    fun `stored key bytes that differ from a fresh derivation are a diagnostic only`() {
        val otherNonce = Sr25519Keypair(keys.privateKey.copyOf(), keys.publicKey.copyOf(), ByteArray(32) { 7 })
        val verdict = judge.judge(address, otherNonce, TWELVE, seedHex)
        assertTrue(verdict.canSign)
        assertEquals("MNEMONIC", verdict.secretSource)
        assertEquals(setOf("NONCE_BYTES_DIFFER_FROM_DERIVATION"), verdict.diagnostics)
    }

    @Test
    fun `a phrase or seed of another wallet limits the backup and never the signing`() {
        val otherPhrase = judge.judge(address, keys.copy(), TWENTY_FOUR, "")
        assertTrue(otherPhrase.canSign)
        assertEquals(BackupMatch.DOES_NOT_MATCH, otherPhrase.phrase)
        assertNull(otherPhrase.recoveryPhrase)
        assertNull(otherPhrase.canonicalSeedHex)
        assertEquals("LEGACY_SECRET", otherPhrase.secretSource)
        assertFalse(otherPhrase.hasMatchingBackup)

        // A wrong phrase next to the right seed: the seed is the backup.
        val wrongPhraseRightSeed = judge.judge(address, keys.copy(), TWENTY_FOUR, seedHex)
        assertTrue(wrongPhraseRightSeed.canSign)
        assertEquals(BackupMatch.MATCHES, wrongPhraseRightSeed.seed)
        assertEquals("RAW_SEED", wrongPhraseRightSeed.secretSource)
        assertEquals(seedHex, wrongPhraseRightSeed.canonicalSeedHex)

        val otherSeed = judge.judge(address, keys.copy(), "", "11".repeat(32))
        assertTrue(otherSeed.canSign)
        assertEquals(BackupMatch.DOES_NOT_MATCH, otherSeed.seed)
        assertEquals("LEGACY_SECRET", otherSeed.secretSource)

        val malformed = judge.judge(address, keys.copy(), "not a phrase", "zz")
        assertTrue(malformed.canSign)
        assertEquals(BackupMatch.DOES_NOT_MATCH, malformed.phrase)
        assertEquals(BackupMatch.DOES_NOT_MATCH, malformed.seed)
        assertFalse(malformed.hasMatchingBackup)
    }

    @Test
    fun `backup material that cannot be read has its own status and never stops signing`() {
        val unreadable = judge.judge(address, keys.copy(), null, null)
        assertTrue(unreadable.canSign)
        assertEquals(BackupMatch.UNREADABLE, unreadable.phrase)
        assertEquals(BackupMatch.UNREADABLE, unreadable.seed)
        assertEquals("LEGACY_SECRET", unreadable.secretSource)
        assertFalse(unreadable.hasMatchingBackup)
        assertNull(unreadable.canonicalSeedHex)
        assertEquals(setOf("PHRASE_UNREADABLE", "SEED_UNREADABLE"), unreadable.diagnostics)

        // An unreadable phrase next to a readable seed that matches: the seed is the backup.
        val seedOnly = judge.judge(address, keys.copy(), null, seedHex)
        assertTrue(seedOnly.canSign)
        assertEquals(BackupMatch.UNREADABLE, seedOnly.phrase)
        assertEquals(BackupMatch.MATCHES, seedOnly.seed)
        assertEquals("RAW_SEED", seedOnly.secretSource)
    }

    @Test
    fun `the signing decision is made from the key pair alone before backup material is judged`() {
        // Step 1 needs no phrase and no seed.
        val signing = judge.judgeSigningKey(address, keys.copy())
        assertTrue(signing.canSign)
        assertEquals(1, crypto.challenges.size)

        // Step 2 carries the decision of step 1 unchanged, whatever the backup material is, and
        // makes no further signature.
        listOf<Pair<String?, String?>>(
            null to null,
            "not a phrase" to "zz",
            TWENTY_FOUR to "11".repeat(32),
            TWELVE to fullSeedHex,
        ).forEach { (mnemonic, seed) ->
            val verdict = judge.judgeBackup(address, signing, keys.copy(), mnemonic, seed)
            assertTrue("$mnemonic / $seed", verdict.canSign)
        }
        assertEquals(1, crypto.challenges.size)

        // And a refusal from step 1 is not undone by a matching phrase and seed.
        val refused = judge.judgeSigningKey(address, null)
        val verdict = judge.judgeBackup(address, refused, null, TWELVE, seedHex)
        assertFalse(verdict.canSign)
        assertEquals("SIGNING_KEY_MISSING", verdict.signingRefusal)
        assertEquals(BackupMatch.MATCHES, verdict.phrase)
        assertEquals(BackupMatch.MATCHES, verdict.seed)
    }

    @Test
    fun `a key pair alone signs`() {
        val verdict = judge.judge(address, keys.copy(), "", "")
        assertTrue(verdict.canSign)
        assertEquals(BackupMatch.ABSENT, verdict.phrase)
        assertEquals(BackupMatch.ABSENT, verdict.seed)
        assertEquals("LEGACY_SECRET", verdict.secretSource)
    }

    @Test
    fun `fifteen words are a backup of the SORA2 account but not a Nexus phrase`() {
        val fifteen = requireNotNull(RecoveryPhrase.parseOrNull(FIFTEEN))
        val fifteenKeys = crypto.generateKeypair(fifteen.miniSeed())
        val fifteenAddress = requireNotNull(codec.toSoraAddressOrNull(fifteenKeys.publicKey))
        val verdict = judge.judge(fifteenAddress, fifteenKeys, FIFTEEN.replace(" ", ","), "")
        assertTrue(verdict.canSign)
        assertEquals("MNEMONIC_UNSUPPORTED", verdict.secretSource)
    }

    @Test
    fun `only the stored signing key makes an account read-only`() {
        // No key.
        assertEquals("SIGNING_KEY_MISSING", judge.judge(address, null, TWELVE, seedHex).signingRefusal)
        // No nonce (a process killed between two preference edits).
        val noNonce = Sr25519Keypair(keys.privateKey.copyOf(), keys.publicKey.copyOf(), ByteArray(0))
        assertEquals("SIGNING_KEY_INCOMPLETE", judge.judge(address, noNonce, TWELVE, seedHex).signingRefusal)
        // The stored key is another wallet's: it does not derive this address.
        val other = crypto.generateKeypair(ByteArray(32) { 9 })
        assertEquals("SORA_ADDRESS_MISMATCH", judge.judge(address, other, TWELVE, seedHex).signingRefusal)
        // The right public key with a private key that is not its own: the public key computed
        // from the stored private key is another one. The native library is never called.
        val wrongPrivate = Sr25519Keypair(other.privateKey.copyOf(), keys.publicKey.copyOf(), keys.nonce.copyOf())
        crypto.challenges.clear()
        assertEquals(
            "SIGNING_KEY_NOT_OF_ADDRESS",
            judge.judge(address, wrongPrivate, TWELVE, seedHex).signingRefusal,
        )
        // A private key that is not a canonical scalar (zero, or not below the group order).
        listOf(ByteArray(32), ByteArray(32) { 0xff.toByte() }).forEach { scalar ->
            val malformed = Sr25519Keypair(scalar, keys.publicKey.copyOf(), keys.nonce.copyOf())
            assertEquals(
                "SIGNING_KEY_SCALAR_INVALID",
                judge.judge(address, malformed, TWELVE, seedHex).signingRefusal,
            )
        }
        // A key of the wrong length.
        val longPrivate = Sr25519Keypair(ByteArray(64) { 1 }, keys.publicKey.copyOf(), keys.nonce.copyOf())
        assertEquals("SIGNING_KEY_INCOMPLETE", judge.judge(address, longPrivate, TWELVE, seedHex).signingRefusal)
        assertTrue("nothing malformed reaches the native library", crypto.challenges.isEmpty())
        // A well-formed key pair of this address whose signature does not verify cannot sign.
        crypto.signatureVerifies = false
        assertEquals(
            "SORA_SIGNING_VECTOR_MISMATCH",
            judge.judge(address, keys.copy(), TWELVE, seedHex).signingRefusal,
        )
        crypto.signatureVerifies = true
        // The native library throws.
        crypto.throwOnSign = true
        assertEquals("SORA_SIGNING_VECTOR_FAILED", judge.judge(address, keys.copy(), TWELVE, seedHex).signingRefusal)
        crypto.throwOnSign = false
        // Not a SORA2 address.
        assertEquals("SORA_ADDRESS_INVALID", judge.judge("not-an-address", keys.copy(), TWELVE, seedHex).signingRefusal)
        // The matching phrase is still reported as the backup of a read-only account.
        val readOnly = judge.judge(address, null, TWELVE, "")
        assertFalse(readOnly.canSign)
        assertEquals(BackupMatch.MATCHES, readOnly.phrase)
    }

    @Test
    fun `every proof signs a fresh random challenge with a fixed prefix`() {
        repeat(3) { judge.judge(address, keys.copy(), "", "") }
        assertEquals(3, crypto.challenges.size)
        assertEquals(3, crypto.challenges.map { StoredSecretHex.encode(it) }.toSet().size)
        crypto.challenges.forEach { challenge ->
            val prefix = StoredWalletSecretsJudge.CHALLENGE_PREFIX.encodeToByteArray()
            assertEquals(prefix.size + StoredWalletSecretsJudge.CHALLENGE_RANDOM_BYTES, challenge.size)
            assertTrue(challenge.copyOfRange(0, prefix.size).contentEquals(prefix))
        }
    }

    private fun Sr25519Keypair.copy() =
        Sr25519Keypair(privateKey.copyOf(), publicKey.copyOf(), nonce.copyOf())

    private companion object {
        const val TWELVE =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        const val FIFTEEN =
            "adapt explain ecology dinner glare old unfair empty expect road off suggest dash " +
                "dolphin case"
        const val TWENTY_FOUR =
            "abandon amount liar amount expire adjust cage candy arch gather drum bullet absurd " +
                "math era live bid rhythm alien crouch range attend journey unaware"
    }
}
