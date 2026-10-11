package jp.co.soramitsu.common.nexus

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader
import jp.co.soramitsu.xsubstrate.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.xsubstrate.encrypt.seed.substrate.SubstrateSeedFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IrohaKeyDerivationTest {

    @Test
    fun `twelve word master phrase derives independent mainnet and testnet accounts`() {
        assertGoldenVector("bip39-12-abandon")
    }

    @Test
    fun `twenty four word vector is stable on both platforms`() {
        assertGoldenVector("bip39-24-abandon")
    }

    @Test
    fun `shared twelve and twenty four word vectors preserve Sora2 seed inputs`() {
        val vectors = loadVectors().filter { it.has("sora2") }
        assertEquals(
            setOf("bip39-12-abandon", "fearless-default-24"),
            vectors.map { it["name"].asString }.toSet(),
        )

        vectors.forEach { vector ->
            val expected = vector.getAsJsonObject("sora2")
            val directBip39Seed = deriveDirectBip39Seed32(vector["mnemonic"].asString)
            try {
                assertEquals(
                    expected["directBip39Seed32Hex"].asString,
                    directBip39Seed.toHex(),
                )
            } finally {
                directBip39Seed.fill(0)
            }
            val seed = deriveRetainedSora2MiniSeed(vector["mnemonic"].asString)
            try {
                assertEquals(expected["legacySora2MiniSeedHex"].asString, seed.toHex())
            } finally {
                seed.fill(0)
            }
        }
    }

    @Test
    fun `an existing Nexus account derived from a comma text keeps its identity`() {
        val spaces =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val stored = spaces.replace(" ", ", ")
        // What builds before the canonical rule derived for this stored text: PBKDF2 over the
        // text with its commas (the audited derivation, copied in earlierBuildAddress below).
        val earlier = earlierBuildDerivation(stored, NexusDerivationProfiles.taira)
        val canonical = IrohaKeyDerivation.derive(stored, NexusDerivationProfiles.taira)
        try {
            assertNotEquals(earlier.address, canonical.address)

            // The existing account is re-derived exactly as it was first derived.
            val existing = requireNotNull(
                IrohaKeyDerivation.deriveExisting(
                    mnemonic = stored,
                    profile = NexusDerivationProfiles.taira,
                    existingAddress = earlier.address,
                    existingPublicKey = earlier.publicKey,
                )
            )
            try {
                assertEquals(earlier.address, existing.address)
                assertEquals(earlier.publicKey.toHex(), existing.publicKey.toHex())
                assertEquals(earlier.privateKeySeed.toHex(), existing.privateKeySeed.toHex())
                assertEquals(NexusDerivationProfiles.taira.derivationPath, existing.derivationPath)
            } finally {
                existing.destroy()
            }

            // An account first derived from the canonical words is re-derived from them.
            val fresh = requireNotNull(
                IrohaKeyDerivation.deriveExisting(
                    mnemonic = stored,
                    profile = NexusDerivationProfiles.taira,
                    existingAddress = canonical.address,
                    existingPublicKey = canonical.publicKey,
                )
            )
            try {
                assertEquals(canonical.privateKeySeed.toHex(), fresh.privateKeySeed.toHex())
            } finally {
                fresh.destroy()
            }

            // A stored account that the phrase does not derive is never replaced by another.
            assertEquals(
                null,
                IrohaKeyDerivation.deriveExisting(
                    mnemonic = stored,
                    profile = NexusDerivationProfiles.taira,
                    existingAddress = canonical.address,
                    existingPublicKey = earlier.publicKey,
                ),
            )
        } finally {
            earlier.destroy()
            canonical.destroy()
        }
    }

    @Test
    fun `a phrase stored with single spaces derives one identity in both text forms`() {
        val spaces =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val earlier = earlierBuildDerivation(spaces, NexusDerivationProfiles.taira)
        val canonical = IrohaKeyDerivation.derive(spaces, NexusDerivationProfiles.taira)
        try {
            assertEquals(earlier.address, canonical.address)
            assertEquals(earlier.privateKeySeed.toHex(), canonical.privateKeySeed.toHex())
        } finally {
            earlier.destroy()
            canonical.destroy()
        }
    }

    /**
     * The Nexus derivation of the audited build (commit 42013abd, IrohaKeyDerivation.derive),
     * kept here as the reference for accounts that build already derived: the stored text is
     * split on whitespace only, so punctuation stays in the PBKDF2 password.
     */
    private fun earlierBuildDerivation(
        mnemonic: String,
        profile: NexusDerivationProfile,
    ): IrohaKeyDerivation.DerivedAccount {
        val words = mnemonic.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        require(words.size == 12 || words.size == 24)
        val normalizedMnemonic =
            java.text.Normalizer.normalize(words.joinToString(" "), java.text.Normalizer.Form.NFKD)
        val spec = javax.crypto.spec.PBEKeySpec(
            normalizedMnemonic.toCharArray(),
            "mnemonic".toByteArray(Charsets.UTF_8),
            2048,
            512,
        )
        val seed = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            .generateSecret(spec)
            .encoded
        val derived = IrohaKeyDerivation.derivePrivateKey(seed, profile.derivationPath)
        val publicKey = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(derived.first, 0)
            .generatePublicKey().encoded
        return IrohaKeyDerivation.DerivedAccount(
            network = profile.id,
            derivationPath = profile.derivationPath,
            privateKeySeed = derived.first,
            chainCode = derived.second,
            publicKey = publicKey,
            address = IrohaAddressCodec.encode(publicKey, profile.chainDiscriminant),
        )
    }

    @Test
    fun `a new derivation from a phrase stored with commas uses the same words with spaces`() {
        val spaces =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val expected = IrohaKeyDerivation.derive(spaces, NexusDerivationProfiles.taira)
        try {
            listOf(
                spaces.replace(" ", ","),
                spaces.replace(" ", ", "),
                spaces.replace(" ", "  "),
                " $spaces\n",
            ).forEach { stored ->
                val derived = IrohaKeyDerivation.derive(stored, NexusDerivationProfiles.taira)
                try {
                    assertEquals(stored, expected.address, derived.address)
                    assertEquals(stored, expected.publicKey.toHex(), derived.publicKey.toHex())
                } finally {
                    derived.destroy()
                }
            }
        } finally {
            expected.destroy()
        }
    }

    @Test
    fun `address validation is bound to selected network`() {
        val account = IrohaKeyDerivation.derive(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
            NexusDerivationProfiles.taira,
        )

        try {
            assertTrue(IrohaAddressCodec.isValid(account.address, 369))
            assertFalse(IrohaAddressCodec.isValid(account.address, 753))
            assertThrows(IrohaAddressCodec.AddressException::class.java) {
                IrohaAddressCodec.parse(account.address.dropLast(1) + "1", 369)
            }
        } finally {
            account.destroy()
        }
    }

    @Test
    fun `address validation rejects unbounded input before base conversion`() {
        val oversized = "sora" + "1".repeat(509)

        assertThrows(IrohaAddressCodec.AddressException::class.java) {
            IrohaAddressCodec.parse(
                oversized,
                NexusNetworks.minamoto.chainDiscriminant,
            )
        }
    }

    @Test
    fun `only supported master phrase lengths are accepted`() {
        assertThrows(IllegalArgumentException::class.java) {
            IrohaKeyDerivation.derive("one two three", NexusNetworks.minamoto)
        }
    }

    @Test
    fun `supported word count with invalid bip39 checksum is rejected`() {
        val invalidChecksum = List(12) { "abandon" }.joinToString(" ")

        assertThrows(IllegalArgumentException::class.java) {
            IrohaKeyDerivation.derive(
                invalidChecksum,
                NexusNetworks.minamoto,
            )
        }
    }

    private fun assertGoldenVector(name: String) {
        val vector = loadVectors().single { it["name"].asString == name }
        val mnemonic = vector["mnemonic"].asString
        val minamoto = IrohaKeyDerivation.derive(mnemonic, NexusNetworks.minamoto)
        val taira = IrohaKeyDerivation.derive(mnemonic, NexusDerivationProfiles.taira)
        try {
            assertDerivedAccount(minamoto, vector.getAsJsonObject("minamoto"))
            assertDerivedAccount(taira, vector.getAsJsonObject("taira"))
            assertNotEquals(minamoto.publicKey.toList(), taira.publicKey.toList())
        } finally {
            minamoto.destroy()
            taira.destroy()
        }
    }

    private fun assertDerivedAccount(
        actual: IrohaKeyDerivation.DerivedAccount,
        expected: JsonObject,
    ) {
        assertEquals(expected["privateKeyHex"].asString, actual.privateKeySeed.toHex())
        assertEquals(expected["publicKeyHex"].asString, actual.publicKey.toHex())
        assertEquals(expected["address"].asString, actual.address)
    }

    private fun loadVectors(): List<JsonObject> {
        val stream = javaClass.classLoader?.getResourceAsStream("wallet-derivation-v1.json")
            ?: error("wallet-derivation-v1.json is missing")
        return stream.use {
            JsonParser.parseReader(InputStreamReader(it))
                .asJsonObject
                .getAsJsonArray("vectors")
                .map { vector -> vector.asJsonObject }
        }
    }

    private fun deriveRetainedSora2MiniSeed(mnemonic: String): ByteArray {
        val bip39Seed = SubstrateSeedFactory.deriveSeed(mnemonic, null).seed
        return try {
            check(bip39Seed.size >= SORA2_SEED_BYTES)
            bip39Seed.copyOfRange(0, SORA2_SEED_BYTES)
        } finally {
            bip39Seed.fill(0)
        }
    }

    private fun deriveDirectBip39Seed32(mnemonic: String): ByteArray {
        val bip39Seed = EthereumSeedFactory.deriveSeed(mnemonic, null).seed
        return try {
            check(bip39Seed.size >= SORA2_SEED_BYTES)
            bip39Seed.copyOfRange(0, SORA2_SEED_BYTES)
        } finally {
            bip39Seed.fill(0)
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    private companion object {
        const val SORA2_SEED_BYTES = 32
    }
}
