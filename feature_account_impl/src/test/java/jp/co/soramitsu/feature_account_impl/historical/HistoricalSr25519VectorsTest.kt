package jp.co.soramitsu.feature_account_impl.historical

import jp.co.soramitsu.common.account.RecoveryPhrase
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.account.StoredSecretHex
import jp.co.soramitsu.feature_account_impl.domain.keys.BackupMatch
import jp.co.soramitsu.feature_account_impl.domain.keys.FakeStoredKeyCrypto
import jp.co.soramitsu.feature_account_impl.domain.keys.Sr25519PublicKeyDerivation
import jp.co.soramitsu.feature_account_impl.domain.keys.StoredWalletSecretsJudge
import jp.co.soramitsu.feature_account_impl.domain.keys.realSr25519Keypair
import jp.co.soramitsu.feature_account_impl.domain.keys.toHex
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The key vectors of the released native library (schnorrkel 0.9.1 as `sr25519-java` calls it,
 * see scripts/historical-sr25519-vectors) against today's code, on the JVM. The same vectors run
 * against the packaged native library on a device (HistoricalSr25519NativeParityTest and
 * WalletUpgradeScenarioTest).
 */
class HistoricalSr25519VectorsTest {

    private val codec = Sora2AddressCodec()
    private val hex = HistoricalSr25519Vectors::hex

    @Test
    fun `the vector set covers every phrase length and a raw seed`() {
        assertEquals(
            setOf(12, 15, 18, 21, 24),
            HistoricalSr25519Vectors.ALL.mapNotNull { it.phrase }.map { it.split(" ").size }.toSet(),
        )
        assertTrue(HistoricalSr25519Vectors.ALL.any { it.phrase == null })
    }

    @Test
    fun `the public key computed from each stored private key is the historical public key`() {
        HistoricalSr25519Vectors.ALL.forEach { vector ->
            assertEquals(
                vector.id,
                vector.publicKey,
                Sr25519PublicKeyDerivation.publicKeyOrNull(hex(vector.privateKey))?.toHex(),
            )
        }
    }

    @Test
    fun `each historical public key is the SORA address recorded for it`() {
        HistoricalSr25519Vectors.ALL.forEach { vector ->
            assertEquals(vector.id, vector.soraAddress, codec.toSoraAddressOrNull(hex(vector.publicKey)))
            assertEquals(
                vector.id,
                vector.publicKey,
                codec.soraPublicKeyOrNull(vector.soraAddress)?.toHex(),
            )
        }
    }

    @Test
    fun `each phrase gives the historical entropy seed and full 64 byte derivation`() {
        HistoricalSr25519Vectors.ALL.filter { it.phrase != null }.forEach { vector ->
            val phrase = requireNotNull(RecoveryPhrase.parseOrNull(requireNotNull(vector.phrase)))
            assertEquals(vector.id, vector.seed, phrase.miniSeed().toHex())
            assertEquals(vector.id, vector.fullSeed, phrase.fullSeed().toHex())
            // The 64-byte cache of 3.6.0.0 to 3.8.2.0 and the 32-byte seed name the same seed.
            assertEquals(
                vector.id,
                vector.seed,
                StoredSecretHex.canonicalSeedOf(hex(requireNotNull(vector.fullSeed)), phrase)?.toHex(),
            )
        }
    }

    @Test
    fun `the Kotlin key expansion used by the JVM tests reproduces the historical key bytes`() {
        HistoricalSr25519Vectors.ALL.forEach { vector ->
            val keys = realSr25519Keypair(hex(vector.seed))
            assertEquals(vector.id, vector.privateKey, keys.privateKey.toHex())
            assertEquals(vector.id, vector.nonce, keys.nonce.toHex())
            assertEquals(vector.id, vector.publicKey, keys.publicKey.toHex())
        }
    }

    @Test
    fun `stored historical key bytes are judged able to sign with their phrase and seed as backups`() {
        val judge = StoredWalletSecretsJudge(codec, FakeStoredKeyCrypto())
        HistoricalSr25519Vectors.ALL.forEach { vector ->
            val stored = Sr25519Keypair(hex(vector.privateKey), hex(vector.publicKey), hex(vector.nonce))
            val verdict = judge.judge(
                address = vector.soraAddress,
                keyPair = stored,
                mnemonic = vector.phrase.orEmpty(),
                // Stored as the released apps stored it: the 64-byte cache for a phrase wallet.
                storedSeed = vector.fullSeed ?: vector.seed,
            )
            assertTrue(vector.id, verdict.canSign)
            assertEquals(
                vector.id,
                if (vector.phrase == null) BackupMatch.ABSENT else BackupMatch.MATCHES,
                verdict.phrase,
            )
            assertEquals(vector.id, BackupMatch.MATCHES, verdict.seed)
            assertEquals(vector.id, vector.seed, verdict.canonicalSeedHex)
            assertFalse(vector.id, "PRIVATE_KEY_BYTES_DIFFER_FROM_DERIVATION" in verdict.diagnostics)
            assertFalse(vector.id, "NONCE_BYTES_DIFFER_FROM_DERIVATION" in verdict.diagnostics)
        }
    }

    @Test
    fun `a private key that is not a canonical non zero scalar has no public key`() {
        val order = "edd3f55c1a631258d69cf7a2def9de1400000000000000000000000000000010"
        assertNull(Sr25519PublicKeyDerivation.publicKeyOrNull(ByteArray(32)))
        assertNull(Sr25519PublicKeyDerivation.publicKeyOrNull(hex(order)))
        assertNull(Sr25519PublicKeyDerivation.publicKeyOrNull(ByteArray(32) { 0xff.toByte() }))
        assertNull(Sr25519PublicKeyDerivation.publicKeyOrNull(ByteArray(31) { 1 }))
        assertNull(Sr25519PublicKeyDerivation.publicKeyOrNull(ByteArray(64) { 1 }))
        assertFalse(Sr25519PublicKeyDerivation.isCanonicalScalar(hex(order)))
        // One below the group order is the largest valid scalar.
        val largest = "ecd3f55c1a631258d69cf7a2def9de1400000000000000000000000000000010"
        assertTrue(Sr25519PublicKeyDerivation.isCanonicalScalar(hex(largest)))
    }

    @Test
    fun `small multiples of the base point have their Ristretto encodings`() {
        // RFC 9496, appendix A.1: the encodings of B, 2B and 3B.
        val expected = listOf(
            "e2f2ae0a6abc4e71a884a961c500515f58e30b6aa582dd8db6a65945e08d2d76",
            "6a493210f7499cd17fecb510ae0cea23a110e8d5b901f8acadd3095c73a3b919",
            "94741f5d5d52755ece4f23f044ee27d5d1ea1e2bd196b462166b16152a9d0259",
        )
        expected.forEachIndexed { index, encoding ->
            val scalar = ByteArray(32).also { it[0] = (index + 1).toByte() }
            assertEquals(encoding, Sr25519PublicKeyDerivation.publicKeyOrNull(scalar)?.toHex())
        }
    }
}
