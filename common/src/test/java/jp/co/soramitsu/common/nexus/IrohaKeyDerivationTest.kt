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
    fun `address validation is bound to selected network`() {
        val account = IrohaKeyDerivation.derive(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
            NexusNetworks.taira,
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
        val taira = IrohaKeyDerivation.derive(mnemonic, NexusNetworks.taira)
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
