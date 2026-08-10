package jp.co.soramitsu.feature_blockexplorer_api.data

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Test

class PiCanonicalIntegerLexemeSerializerTest {

    @Test
    fun `strict PI integer serializer preserves arbitrary precision number and string lexemes`() {
        val arbitraryPrecision = "9".repeat(1_024)

        assertThat(decode(arbitraryPrecision)).isEqualTo(arbitraryPrecision)
        assertThat(decode("\"$arbitraryPrecision\"")).isEqualTo(arbitraryPrecision)
        assertThat(decode("0")).isEqualTo("0")
        assertThat(decode("\"0\"")).isEqualTo("0")
        assertThat(decode("null")).isNull()
    }

    @Test
    fun `strict PI integer serializer cache encoding is deterministic and precision safe`() {
        val arbitraryPrecision = "1234567890".repeat(100)

        assertThat(
            JSON.encodeToString(
                NullableCanonicalUnsignedIntegerLexemeSerializer,
                arbitraryPrecision,
            )
        ).isEqualTo("\"$arbitraryPrecision\"")
        assertThat(
            JSON.encodeToString(
                NullableCanonicalUnsignedIntegerLexemeSerializer,
                null,
            )
        ).isEqualTo("null")
    }

    @Test
    fun `strict PI integer serializer rejects booleans exponents signs and leading zero`() {
        listOf(
            "\"01\"",
            "\"-0\"",
            "\"-1\"",
            "\"1.0\"",
            "-0",
            "-1",
            "1.0",
            "1e3",
            "true",
            "{}",
            "[]",
        ).forEach { encoded ->
            val error = runCatching { decode(encoded) }.exceptionOrNull()

            assertThat(error).isInstanceOf(SerializationException::class.java)
            assertThat(error).hasMessageThat().contains(
                NullableCanonicalUnsignedIntegerLexemeSerializer.ERROR_CODE
            )
        }

        listOf("01", "+1").forEach { malformedNumber ->
            val error = runCatching { decode(malformedNumber) }.exceptionOrNull()

            assertThat(error).isInstanceOf(SerializationException::class.java)
        }
    }

    @Test
    fun `strict PI integer serializer rejects noncanonical cache values before encoding`() {
        listOf("", "01", "-1", "1.0", "1e3", " 1").forEach { value ->
            val error = runCatching {
                JSON.encodeToString(
                    NullableCanonicalUnsignedIntegerLexemeSerializer,
                    value,
                )
            }.exceptionOrNull()

            assertThat(error).isInstanceOf(SerializationException::class.java)
            assertThat(error).hasMessageThat().contains(
                NullableCanonicalUnsignedIntegerLexemeSerializer.ERROR_CODE
            )
        }
    }

    @Test
    fun `strict PI integer serializer bounds exact integer work without fixed width conversion`() {
        val maximum = "9".repeat(
            NullableCanonicalUnsignedIntegerLexemeSerializer.MAXIMUM_WIRE_BYTES
        )
        assertThat(decode(maximum)).isEqualTo(maximum)

        val oversized = maximum + "9"
        listOf(oversized, "\"$oversized\"").forEach { encoded ->
            val error = runCatching { decode(encoded) }.exceptionOrNull()

            assertThat(error).isInstanceOf(SerializationException::class.java)
            assertThat(error).hasMessageThat().contains(
                NullableCanonicalUnsignedIntegerLexemeSerializer.ERROR_CODE
            )
        }
    }

    private fun decode(encoded: String): String? = JSON.decodeFromString(
        NullableCanonicalUnsignedIntegerLexemeSerializer,
        encoded,
    )

    private companion object {
        val JSON = Json {
            isLenient = false
            ignoreUnknownKeys = false
        }
    }
}
