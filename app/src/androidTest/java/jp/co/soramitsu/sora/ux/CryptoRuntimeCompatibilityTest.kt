package jp.co.soramitsu.sora.ux

import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.nexus.IrohaKeyDerivation
import jp.co.soramitsu.common.nexus.NexusDerivationProfiles
import jp.co.soramitsu.sora.splash.domain.MigrationSr25519Crypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the packaged crypto libraries on Android using public derivation vectors. */
@RunWith(AndroidJUnit4::class)
class CryptoRuntimeCompatibilityTest {
    @Test
    fun retainedSora2Sr25519VectorSignsAndVerifies() {
        // common/src/test/resources/wallet-derivation-v1.json: bip39-12-abandon.
        val seed = hexBytes("4ed8d4b17698ddeaa1f1559f152f87b5d472f725ca86d341bd0276f1b61197e2")
        val expectedPublicKey = hexBytes("66933bd1f37070ef87bd1198af3dacceb095237f803f3d32b173e6b425ed7972")
        val crypto = MigrationSr25519Crypto()
        try {
            val keypair = crypto.generateKeypair(seed)
            assertNotNull("Packaged sr25519 implementation must load and derive a keypair", keypair)
            keypair!!
            try {
                assertArrayEquals(expectedPublicKey, keypair.publicKey)
                val challenge = "sora-wallet-android-release-crypto-check".toByteArray()
                assertTrue(crypto.signAndVerify(keypair, challenge, expectedPublicKey))
                // A second valid sr25519 public key tests rejection without using malformed curve data.
                val wrongPublicKey = hexBytes("4a50a9606f3b0c47e0582f9a2dce9da3ec59819c6e15468f6f03f07e7fcfed23")
                assertFalse(crypto.signAndVerify(keypair, challenge, wrongPublicKey))
                wrongPublicKey.fill(0)
                challenge.fill(0)
            } finally {
                keypair.privateKey.fill(0)
                keypair.nonce.fill(0)
            }
        } finally {
            seed.fill(0)
            expectedPublicKey.fill(0)
        }
    }

    @Test
    fun nexusDerivationUsesPackagedBouncyCastleOnAndroid() {
        // Same public vector as common/src/test/resources/wallet-derivation-v1.json.
        val account = IrohaKeyDerivation.derive(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
            NexusDerivationProfiles.taira,
        )
        try {
            assertArrayEquals(
                hexBytes("ebe2fe329305f8e4c19cc4b159bc6ddd413cf5831422bdb9b3b93ee5053bd4d3"),
                account.publicKey,
            )
        } finally {
            account.destroy()
        }
    }

    private fun hexBytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
