package jp.co.soramitsu.feature_account_impl.keys

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.SecureRandom
import jp.co.soramitsu.feature_account_impl.domain.keys.Sr25519PublicKeyDerivation
import jp.co.soramitsu.feature_account_impl.historical.HistoricalSr25519Vectors
import jp.co.soramitsu.sora.substrate.substrate.deriveSeed32
import jp.co.soramitsu.xcrypto.encryption.sr25519.Sr25519JNI
import jp.co.soramitsu.xcrypto.seed.MnemonicCreator
import jp.co.soramitsu.xcrypto.util.toHexString
import jp.co.soramitsu.xsubstrate.encrypt.EncryptionType
import jp.co.soramitsu.xsubstrate.encrypt.MultiChainEncryption
import jp.co.soramitsu.xsubstrate.encrypt.SignWrapper
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.xsubstrate.encrypt.seed.substrate.SubstrateSeedFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The packaged native sr25519 library against the key vectors of the library the released apps
 * shipped (schnorrkel 0.9.1 called as `sr25519-java` calls it; see
 * scripts/historical-sr25519-vectors). What an old release stored for a phrase is exactly what
 * today's library derives, signs with and exports, byte for byte.
 */
@RunWith(AndroidJUnit4::class)
class HistoricalSr25519NativeParityTest {

    private val hex = HistoricalSr25519Vectors::hex
    private val message = HistoricalSr25519Vectors.SIGNED_MESSAGE.encodeToByteArray()

    @Test
    fun packagedLibraryDerivesTheHistoricalSeeds() {
        HistoricalSr25519Vectors.ALL.filter { it.phrase != null }.forEach { vector ->
            val mnemonic = MnemonicCreator.fromWords(requireNotNull(vector.phrase))
            assertEquals(vector.id, vector.entropy, mnemonic.entropy.toHexString())
            assertEquals(
                vector.id,
                vector.seed,
                SubstrateSeedFactory.deriveSeed32(mnemonic.words, null).seed.toHexString(),
            )
            // The uncropped derivation that 3.6.0.0 to 3.8.2.0 cached.
            assertEquals(
                vector.id,
                vector.fullSeed,
                SubstrateSeedFactory.deriveSeed(mnemonic.words, null).seed.toHexString(),
            )
        }
    }

    @Test
    fun packagedLibraryDerivesTheHistoricalKeyBytes() {
        HistoricalSr25519Vectors.ALL.forEach { vector ->
            val keys = SubstrateKeypairFactory.generate(EncryptionType.SR25519, hex(vector.seed))
                as Sr25519Keypair
            assertEquals(vector.id, vector.privateKey, keys.privateKey.toHexString())
            assertEquals(vector.id, vector.nonce, keys.nonce.toHexString())
            assertEquals(vector.id, vector.publicKey, keys.publicKey.toHexString())
        }
    }

    @Test
    fun signaturesMadeByTheHistoricalLibraryVerifyToday() {
        HistoricalSr25519Vectors.ALL.forEach { vector ->
            assertTrue(
                vector.id,
                Sr25519JNI.verify(hex(vector.signature), message, hex(vector.publicKey)),
            )
        }
    }

    @Test
    fun storedHistoricalKeyBytesSignThroughTheProductionSigner() {
        HistoricalSr25519Vectors.ALL.forEach { vector ->
            val stored = Sr25519Keypair(hex(vector.privateKey), hex(vector.publicKey), hex(vector.nonce))
            val signature = SignWrapper.sign(
                MultiChainEncryption.Substrate(EncryptionType.SR25519),
                message,
                stored,
            ).signature
            assertTrue(vector.id, Sr25519JNI.verify(signature, message, hex(vector.publicKey)))
        }
    }

    @Test
    fun theSecretInsideTheJsonExportIsTheHistoricalOne() {
        HistoricalSr25519Vectors.ALL.forEach { vector ->
            val secret = hex(vector.privateKey) + hex(vector.nonce)
            assertEquals(
                vector.id,
                vector.ed25519ExpandedSecret,
                Sr25519JNI.toEd25519Bytes(secret).toHexString(),
            )
            assertArrayEquals(
                vector.id,
                secret,
                Sr25519JNI.fromEd25519Bytes(hex(vector.ed25519ExpandedSecret)),
            )
        }
    }

    @Test
    fun publicKeyComputedFromThePrivateScalarEqualsTheNativeLibrary() {
        val random = SecureRandom()
        repeat(RANDOM_KEYS) {
            val seed = ByteArray(32).also(random::nextBytes)
            val pair = Sr25519JNI.keypairFromSeed(seed)
            assertEquals(
                seed.toHexString(),
                pair.copyOfRange(64, 96).toHexString(),
                Sr25519PublicKeyDerivation.publicKeyOrNull(pair.copyOfRange(0, 32))?.toHexString(),
            )
        }
    }

    private companion object {
        const val RANDOM_KEYS = 300
    }
}
