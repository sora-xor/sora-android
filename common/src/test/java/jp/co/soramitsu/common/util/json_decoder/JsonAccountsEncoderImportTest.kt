package jp.co.soramitsu.common.util.json_decoder

import com.google.gson.Gson
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.json_decoder.JsonAccountsEncoder.JsonImportException
import jp.co.soramitsu.xsubstrate.encrypt.json.JsonSeedEncoder
import jp.co.soramitsu.xsubstrate.encrypt.json.coders.type.TypeCoderFactory
import jp.co.soramitsu.xsubstrate.encrypt.json.coders.type.encode
import org.bouncycastle.util.encoders.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

/**
 * What the JSON import refuses before any key is derived or decrypted. Reading a real export
 * back needs the native sr25519 library and runs on a device
 * (WalletUpgradeScenarioTest.everyBackupFormatRestoresTheSameAccount).
 */
class JsonAccountsEncoderImportTest {

    private val gson = Gson()
    private val encoder = JsonAccountsEncoder(
        gson,
        Mockito.mock(CryptoAssistant::class.java),
        JsonSeedEncoder(gson),
    )

    @Test
    fun `text that is not an encrypted sr25519 export is refused`() {
        listOf(
            "",
            "not json",
            "[]",
            "{}",
            // Another key type.
            export(content = listOf("pkcs8", "ed25519")),
            // Another encryption.
            export(type = listOf("none")),
            // Too large to be an account export.
            "{\"padding\":\"" + "a".repeat(600_000) + "\"}",
        ).forEach { text ->
            assertFalse(text.take(40), encoder.looksLikeExport(text))
            assertThrows(JsonImportException.Invalid::class.java) { encoder.decode(text, "password") }
        }
        assertTrue(encoder.looksLikeExport(export()))
        assertTrue(encoder.looksLikeExport(export(content = listOf("batch-pkcs8"))))
    }

    @Test
    fun `a file that asks for another scrypt cost is refused before any key is derived`() {
        // N = 2^30 would need a gigabyte of memory; only the cost this app writes is accepted.
        val header = ByteArray(32) + littleEndian(1 shl 30) + littleEndian(1) + littleEndian(8)
        val encoded = Base64.toBase64String(header + ByteArray(24 + 16 + 64))
        listOf(listOf("pkcs8", "sr25519"), listOf("batch-pkcs8")).forEach { content ->
            assertThrows(JsonImportException.Invalid::class.java) {
                encoder.decode(export(content = content, encoded = encoded), "password")
            }
        }
        // Too short to hold the scrypt header and a sealed box.
        assertThrows(JsonImportException.Invalid::class.java) {
            encoder.decode(export(encoded = Base64.toBase64String(ByteArray(40))), "password")
        }
    }

    @Test
    fun `a batch export opens only with its password and must hold accounts`() {
        val sealed = requireNotNull(TypeCoderFactory.getEncoder(listOf("scrypt", "xsalsa20-poly1305")))
            .encode("[]".encodeToByteArray(), "right password".encodeToByteArray())
        val batch = export(content = listOf("batch-pkcs8"), encoded = Base64.toBase64String(sealed))

        assertThrows(JsonImportException.WrongPassword::class.java) {
            encoder.decode(batch, "wrong password")
        }
        // The right password opens it; a batch without accounts is not a backup.
        assertThrows(JsonImportException.Invalid::class.java) {
            encoder.decode(batch, "right password")
        }
    }

    private fun export(
        content: List<String> = listOf("pkcs8", "sr25519"),
        type: List<String> = listOf("scrypt", "xsalsa20-poly1305"),
        encoded: String = Base64.toBase64String(
            ByteArray(32) + littleEndian(32768) + littleEndian(1) + littleEndian(8) +
                ByteArray(24 + 16 + 64)
        ),
    ): String = gson.toJson(
        mapOf(
            "address" to "cnTon6cV8Ze8ATHT1tZdHregCc3wBc8vfg7P5o2TtwdUoRf4m",
            "encoded" to encoded,
            "encoding" to mapOf("content" to content, "type" to type, "version" to "3"),
            "meta" to mapOf("name" to "Account", "genesisHash" to "", "whenCreated" to 0),
        )
    )

    private fun littleEndian(value: Int): ByteArray =
        ByteArray(4) { index -> (value ushr (8 * index)).toByte() }
}
