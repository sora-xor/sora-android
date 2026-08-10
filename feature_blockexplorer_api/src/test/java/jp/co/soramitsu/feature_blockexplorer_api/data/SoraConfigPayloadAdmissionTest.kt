package jp.co.soramitsu.feature_blockexplorer_api.data

import jp.co.soramitsu.common.network.BoundedHttpTextException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class SoraConfigPayloadAdmissionTest {

    @Test
    fun `config admission counts exact UTF8 bytes`() {
        assertEquals(
            "a🙂",
            requireSoraConfigPayloadWithinLimit("a🙂", maximumBytes = 5),
        )
        assertEquals(
            "SORA_CONFIG_RESPONSE_TOO_LARGE",
            assertThrows(IllegalArgumentException::class.java) {
                requireSoraConfigPayloadWithinLimit("a🙂", maximumBytes = 4)
            }.message,
        )
    }

    @Test
    fun `config admission rejects unpaired UTF16 surrogates`() {
        assertEquals(
            "SORA_CONFIG_UTF8_INVALID",
            assertThrows(IllegalArgumentException::class.java) {
                requireSoraConfigPayloadWithinLimit("\uD800", maximumBytes = 8)
            }.message,
        )
    }

    @Test
    fun `config admission rejects duplicate decoded JSON names`() {
        listOf(
            """{"genesis":"first","genesis":"second"}""",
            """{"node":{"address":"first","address":"second"}}""",
            """{"a":1,"\u0061":2}""",
        ).forEach { payload ->
            assertEquals(
                "BOUNDED_HTTP_JSON_INVALID",
                assertThrows(BoundedHttpTextException::class.java) {
                    requireUnambiguousSoraConfigPayload(
                        content = payload,
                        maximumBytes = 1_024,
                    )
                }.safeCode,
            )
        }
    }

    @Test
    fun `config cache fallback is limited to transient transport failures`() {
        requireSoraConfigCacheFallbackEligible(
            BoundedHttpTextException("BOUNDED_HTTP_IO")
        )

        val ambiguous = BoundedHttpTextException("BOUNDED_HTTP_JSON_INVALID")
        assertSame(
            ambiguous,
            assertThrows(BoundedHttpTextException::class.java) {
                requireSoraConfigCacheFallbackEligible(ambiguous)
            },
        )
    }
}
