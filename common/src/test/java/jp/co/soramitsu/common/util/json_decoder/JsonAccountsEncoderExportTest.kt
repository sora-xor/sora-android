package jp.co.soramitsu.common.util.json_decoder

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.ReflectionAccessFilter
import com.google.gson.ReflectionAccessFilter.FilterResult
import java.io.File
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.xcrypto.encryption.Keypair
import jp.co.soramitsu.xsubstrate.encrypt.json.JsonSeedEncoder
import jp.co.soramitsu.xsubstrate.encrypt.json.coders.type.TypeCoderFactory
import jp.co.soramitsu.xsubstrate.encrypt.json.coders.type.decode
import jp.co.soramitsu.xsubstrate.encrypt.json.coders.type.encode
import org.bouncycastle.util.encoders.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito

/**
 * The batch export ("Export JSON" for several accounts) must not depend on Gson reflection over
 * classes of this app. The release build is minified: R8 removes or renames fields that only
 * reflection reads, and the batch file of the release build came out as `{}`.
 *
 * These tests write the file with a Gson that refuses reflection for every class the release
 * build does not keep by rule, so they fail as soon as a reflective model comes back. The real
 * encryption and the full round trip run on a device (`JsonAccountsExportRoundTripTest`).
 */
class JsonAccountsEncoderExportTest {

    private val gson: Gson = GsonBuilder()
        .addReflectionAccessFilter(ReleaseBuildReflection)
        .create()

    /** The one-account exports the library encoder would write, in the order asked for. */
    private val accountExports = mutableListOf<String>()

    private val jsonSeedEncoder = Mockito.mock(JsonSeedEncoder::class.java).also { mock ->
        Mockito.`when`(
            mock.generate(
                anyOrNull(),
                anyOrNull(),
                anyString(),
                anyString(),
                anyOrNull(),
                anyString(),
                anyString(),
            )
        ).thenAnswer { call ->
            accountExport(name = call.getArgument(3), address = call.getArgument(6))
                .also(accountExports::add)
        }
    }

    /**
     * Seals with the library's own scrypt and xsalsa20-poly1305 coder (pure JVM), so the outer
     * layer written here is one the production reader really opens.
     */
    private val cryptoAssistant = Mockito.mock(CryptoAssistant::class.java).also { mock ->
        Mockito.`when`(mock.generateKeyForJson(anyOrNull())).thenReturn(
            CryptoAssistant.ScryptNaclKeyResult(
                N = ByteArray(0),
                r = ByteArray(0),
                p = ByteArray(0),
                result = ByteArray(32),
                salt = ByteArray(0),
            )
        )
        Mockito.`when`(mock.encryptNaCl(anyString(), anyOrNull())).thenAnswer { call ->
            requireNotNull(TypeCoderFactory.getEncoder(ENCRYPTION)).encode(
                call.getArgument<String>(0).encodeToByteArray(),
                PASSWORD.encodeToByteArray(),
            )
        }
    }

    private val encoder = JsonAccountsEncoder(gson, cryptoAssistant, jsonSeedEncoder)

    private fun account(name: String, address: String) = JsonAccountsEncoder.ExportAccount(
        keypair = Mockito.mock(Keypair::class.java),
        seed = ByteArray(0),
        name = name,
        address = address,
    )

    @Test
    fun `the batch export is written without reflection and holds every key the import reads`() {
        val json = encoder.generate(
            listOf(account("First", FIRST_ADDRESS), account("Second", SECOND_ADDRESS)),
            PASSWORD,
            GENESIS,
        )

        // The file a minified build once wrote.
        assertFalse(json == "{}")
        assertTrue(encoder.looksLikeExport(json))
        val root = gson.fromJson(json, JsonObject::class.java)
        assertEquals(setOf("accounts", "encoded", "encoding"), root.keySet())

        // The format polkadot.js and this app's import read: batch-pkcs8, scrypt and
        // xsalsa20-poly1305, version 3.
        val encoding = root.getAsJsonObject("encoding")
        assertEquals(setOf("content", "type", "version"), encoding.keySet())
        assertEquals(listOf("batch-pkcs8"), encoding.getAsJsonArray("content").map { it.asString })
        assertEquals(ENCRYPTION, encoding.getAsJsonArray("type").map { it.asString })
        assertEquals("3", encoding.get("version").asString)

        // The accounts in the clear: address, name and chain of each, in the order chosen.
        val accounts = root.getAsJsonArray("accounts").map { it.asJsonObject }
        assertEquals(listOf(FIRST_ADDRESS, SECOND_ADDRESS), accounts.map { it.get("address").asString })
        accounts.forEach { account ->
            assertEquals(setOf("address", "meta"), account.keySet())
            assertEquals(
                setOf("name", "genesisHash", "whenCreated"),
                account.getAsJsonObject("meta").keySet(),
            )
            assertEquals(GENESIS, account.getAsJsonObject("meta").get("genesisHash").asString)
            assertTrue(account.getAsJsonObject("meta").get("whenCreated").asLong > 0)
        }
        assertEquals(
            listOf("First", "Second"),
            accounts.map { it.getAsJsonObject("meta").get("name").asString },
        )

        // The sealed part opens with the password and holds each account's own export, whole.
        val plain = requireNotNull(TypeCoderFactory.getDecoder(ENCRYPTION))
            .decode(Base64.decode(root.get("encoded").asString), PASSWORD.encodeToByteArray())
        assertNotNull(plain)
        val sealed = gson.fromJson(requireNotNull(plain).decodeToString(), JsonArray::class.java)
        assertEquals(2, accountExports.size)
        assertEquals(
            accountExports.map { gson.fromJson(it, JsonObject::class.java) },
            sealed.map { it.asJsonObject },
        )
    }

    @Test
    fun `an export that could not be read back is never handed out`() {
        // What a build that lost the library's model would write for one account.
        Mockito.`when`(
            jsonSeedEncoder.generate(
                anyOrNull(),
                anyOrNull(),
                anyString(),
                anyString(),
                anyOrNull(),
                anyString(),
                anyString(),
            )
        ).thenReturn("{}")

        assertThrows(IllegalStateException::class.java) {
            encoder.generate(account("First", FIRST_ADDRESS), PASSWORD, GENESIS)
        }
        assertThrows(IllegalStateException::class.java) {
            encoder.generate(
                listOf(account("First", FIRST_ADDRESS), account("Second", SECOND_ADDRESS)),
                PASSWORD,
                GENESIS,
            )
        }
    }

    @Test
    fun `the release build keeps the library model the one-account export is written with`() {
        // The one-account export, and each account inside a batch, is written by the library
        // (JsonSeedEncoder) through Gson reflection over its own model. That is safe only
        // while the release rules keep that package whole. This is the rule the filter above
        // stands for.
        val rules = generateSequence(File("").absoluteFile, File::getParentFile)
            .map { File(it, "app/proguard-rules.pro") }
            .first(File::isFile)
            .readLines()
            .map(String::trim)
        assertTrue(
            "app/proguard-rules.pro must keep jp.co.soramitsu.xsubstrate.** { *; }",
            rules.any { it == "-keep class jp.co.soramitsu.xsubstrate.** { *; }" },
        )
    }

    /**
     * What the release build allows Gson to reflect on: only the library package that
     * `app/proguard-rules.pro` keeps whole. Reflection over anything else (every class of this
     * app) is refused, as it is unsafe after minification.
     */
    private object ReleaseBuildReflection : ReflectionAccessFilter {
        override fun check(rawClass: Class<*>): FilterResult =
            if (rawClass.name.startsWith("jp.co.soramitsu.xsubstrate.")) {
                FilterResult.ALLOW
            } else {
                FilterResult.BLOCK_ALL
            }
    }

    private fun accountExport(name: String, address: String): String = gson.toJson(
        JsonObject().apply {
            addProperty("address", address)
            addProperty(
                "encoded",
                Base64.toBase64String(
                    ByteArray(32) + littleEndian(32768) + littleEndian(1) + littleEndian(8) +
                        ByteArray(24 + 16 + 64)
                ),
            )
            add(
                "encoding",
                JsonObject().apply {
                    add("content", JsonArray().apply { listOf("pkcs8", "sr25519").forEach { add(it) } })
                    add("type", JsonArray().apply { ENCRYPTION.forEach { add(it) } })
                    addProperty("version", "3")
                },
            )
            add(
                "meta",
                JsonObject().apply {
                    addProperty("name", name)
                    addProperty("genesisHash", GENESIS)
                    addProperty("whenCreated", 1L)
                },
            )
        }
    )

    private fun littleEndian(value: Int): ByteArray =
        ByteArray(4) { index -> (value ushr (8 * index)).toByte() }

    /** Mockito's `any()` for a Kotlin parameter that is not nullable. */
    @Suppress("UNCHECKED_CAST")
    private fun <T> anyOrNull(): T = any<T>() as T

    private companion object {
        const val PASSWORD = "Backup-Password-1"
        const val GENESIS = "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5"
        const val FIRST_ADDRESS = "cnTon6cV8Ze8ATHT1tZdHregCc3wBc8vfg7P5o2TtwdUoRf4m"
        const val SECOND_ADDRESS = "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm"
        val ENCRYPTION = listOf("scrypt", "xsalsa20-poly1305")
    }
}
