package jp.co.soramitsu.common.util.json_decoder

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.ReflectionAccessFilter
import com.google.gson.ReflectionAccessFilter.FilterResult
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.utils.Base64MessageEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.json_decoder.JsonAccountsEncoder.JsonImportException
import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
import jp.co.soramitsu.xcrypto.encryption.sr25519.Sr25519JNI
import jp.co.soramitsu.xsubstrate.encrypt.json.JsonSeedEncoder
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The encrypted JSON export read back by the import, through the production encoder exactly as
 * the app wires it (CommonModule): the one-account export and the batch export restore the same
 * key pairs, byte for byte, and only with the right password.
 *
 * The release build is minified, and R8 removes or renames fields that only Gson reflection
 * reads (the batch export of a release build was `{}`). So the encoder runs here with a Gson
 * that refuses reflection for every class the release rules do not keep whole: writing or
 * reading an export through a model class of this app fails these tests.
 */
@RunWith(AndroidJUnit4::class)
class JsonAccountsExportRoundTripTest {

    private val gson: Gson = GsonBuilder()
        .addReflectionAccessFilter(
            object : ReflectionAccessFilter {
                // app/proguard-rules.pro keeps jp.co.soramitsu.xsubstrate.** whole; the library
                // writes and reads the one-account export through its own model.
                override fun check(rawClass: Class<*>): FilterResult =
                    if (rawClass.name.startsWith("jp.co.soramitsu.xsubstrate.")) {
                        FilterResult.ALLOW
                    } else {
                        FilterResult.BLOCK_ALL
                    }
            }
        )
        .create()
    private val encoder = JsonAccountsEncoder(
        gson,
        CryptoAssistant(
            SecureRandom(),
            Ed25519Sha3(),
            LazySodiumAndroid(SodiumAndroid(), StandardCharsets.UTF_8, Base64MessageEncoder()),
        ),
        JsonSeedEncoder(gson),
    )

    private fun keys(fill: Int): Sr25519Keypair {
        val pair = Sr25519JNI.keypairFromSeed(ByteArray(32) { (it * 5 + fill).toByte() })
        return Sr25519Keypair(pair.copyOfRange(0, 32), pair.copyOfRange(64, 96), pair.copyOfRange(32, 64))
    }

    private fun account(keys: Sr25519Keypair, name: String) = JsonAccountsEncoder.ExportAccount(
        keypair = keys,
        // The sr25519 export is made from the key pair alone: no seed is needed.
        seed = ByteArray(0),
        name = name,
        address = "cnTon6cV8Ze8ATHT1tZdHregCc3wBc8vfg7P5o2TtwdUoRf4m",
    )

    @Test
    fun oneAccountExportRestoresTheSameKeyPair() {
        val keys = keys(1)
        val json = encoder.generate(account(keys, "Main"), PASSWORD, GENESIS)

        assertTrue(encoder.looksLikeExport(json))
        val restored = encoder.decode(json, PASSWORD).single()
        assertArrayEquals(keys.privateKey, restored.keypair.privateKey)
        assertArrayEquals(keys.nonce, restored.keypair.nonce)
        assertArrayEquals(keys.publicKey, restored.keypair.publicKey)
        assertEquals("Main", restored.name)

        assertTrue(runCatching { encoder.decode(json, "another password") }.exceptionOrNull() is JsonImportException.WrongPassword)
    }

    @Test
    fun batchExportRestoresEveryKeyPairInOrder() {
        val first = keys(2)
        val second = keys(3)
        val json = encoder.generate(
            listOf(account(first, "First"), account(second, "Second")),
            PASSWORD,
            GENESIS,
        )

        assertTrue(encoder.looksLikeExport(json))
        // The file itself: every key of the polkadot.js batch format, by name.
        val root = gson.fromJson(json, JsonObject::class.java)
        assertEquals(setOf("accounts", "encoded", "encoding"), root.keySet())
        assertEquals(
            listOf("batch-pkcs8"),
            root.getAsJsonObject("encoding").getAsJsonArray("content").map { it.asString },
        )
        assertEquals(
            listOf("scrypt", "xsalsa20-poly1305"),
            root.getAsJsonObject("encoding").getAsJsonArray("type").map { it.asString },
        )
        assertEquals("3", root.getAsJsonObject("encoding").get("version").asString)
        val listed = root.getAsJsonArray("accounts").map { it.asJsonObject }
        val names = listed.map { account -> account.getAsJsonObject("meta").get("name").asString }
        assertEquals(listOf("First", "Second"), names)
        listed.forEach { account ->
            assertEquals("cnTon6cV8Ze8ATHT1tZdHregCc3wBc8vfg7P5o2TtwdUoRf4m", account.get("address").asString)
            assertEquals(GENESIS, account.getAsJsonObject("meta").get("genesisHash").asString)
            assertTrue(account.getAsJsonObject("meta").get("whenCreated").asLong > 0)
        }

        val restored = encoder.decode(json, PASSWORD)
        assertEquals(listOf("First", "Second"), restored.map { it.name })
        listOf(first, second).zip(restored).forEach { (expected, actual) ->
            assertArrayEquals(expected.privateKey, actual.keypair.privateKey)
            assertArrayEquals(expected.nonce, actual.keypair.nonce)
            assertArrayEquals(expected.publicKey, actual.keypair.publicKey)
        }

        assertTrue(runCatching { encoder.decode(json, "another password") }.exceptionOrNull() is JsonImportException.WrongPassword)
    }

    @Test
    fun alteredExportIsRefused() {
        val json = encoder.generate(account(keys(4), "Main"), PASSWORD, GENESIS)
        // One changed character of the encrypted body fails authentication: reported as a
        // password that does not open the file, and nothing is returned.
        val encoded = gson.fromJson(json, JsonObject::class.java).get("encoded").asString
        val position = encoded.length / 2
        val replacement = if (encoded[position] == 'A') 'B' else 'A'
        val altered = json.replace(
            encoded,
            encoded.substring(0, position) + replacement + encoded.substring(position + 1),
        )
        assertTrue(runCatching { encoder.decode(altered, PASSWORD) }.exceptionOrNull() is JsonImportException)
    }

    private companion object {
        const val PASSWORD = "Backup-Password-1"
        const val GENESIS = "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5"
    }
}
