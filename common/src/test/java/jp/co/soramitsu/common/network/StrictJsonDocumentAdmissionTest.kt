package jp.co.soramitsu.common.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StrictJsonDocumentAdmissionTest {

    @Test
    fun `strict admission accepts nested JSON and repeated names in separate objects`() {
        requireStrictJsonDocumentWithoutDuplicateKeys(
            """{"data":[{"id":"1"},{"id":"2"}],"errors":null}"""
        )
    }

    @Test
    fun `strict admission rejects duplicate decoded names at every object depth`() {
        listOf(
            """{"data":1,"data":2}""",
            """{"data":{"id":"1","id":"2"}}""",
            """{"a":1,"\u0061":2}""",
        ).forEach { document ->
            assertEquals(
                "BOUNDED_HTTP_JSON_INVALID",
                assertThrows(BoundedHttpTextException::class.java) {
                    requireStrictJsonDocumentWithoutDuplicateKeys(document)
                }.safeCode,
            )
        }
    }

    @Test
    fun `strict admission rejects non RFC syntax and trailing documents`() {
        listOf(
            """{'data':1}""",
            """{"data":NaN}""",
            """{"data":1,}""",
            """{"data":1} {"data":2}""",
            """/*comment*/{"data":1}""",
        ).forEach { document ->
            assertEquals(
                "BOUNDED_HTTP_JSON_INVALID",
                assertThrows(BoundedHttpTextException::class.java) {
                    requireStrictJsonDocumentWithoutDuplicateKeys(document)
                }.safeCode,
            )
        }
    }

    @Test
    fun `strict admission rejects isolated escaped surrogates`() {
        requireStrictJsonDocumentWithoutDuplicateKeys(
            """{"value":"\uD83D\uDE42","\uD83D\uDE42":true}"""
        )

        listOf(
            """{"value":"\uD800"}""",
            """{"value":"\uDC00"}""",
            """{"\uD800":true}""",
            """{"\uDC00":true}""",
        ).forEach { document ->
            assertEquals(
                "BOUNDED_HTTP_JSON_INVALID",
                assertThrows(BoundedHttpTextException::class.java) {
                    requireStrictJsonDocumentWithoutDuplicateKeys(document)
                }.safeCode,
            )
        }
    }

    @Test
    fun `strict admission independently bounds depth and token work`() {
        val depthError = assertThrows(BoundedHttpTextException::class.java) {
            requireStrictJsonDocumentWithoutDuplicateKeys(
                rawJson = "[[[0]]]",
                maximumDepth = 3,
                maximumTokens = 100,
            )
        }
        val tokenError = assertThrows(BoundedHttpTextException::class.java) {
            requireStrictJsonDocumentWithoutDuplicateKeys(
                rawJson = "[0,1,2]",
                maximumDepth = 3,
                maximumTokens = 3,
            )
        }

        assertEquals("BOUNDED_HTTP_JSON_COMPLEXITY", depthError.safeCode)
        assertEquals("BOUNDED_HTTP_JSON_COMPLEXITY", tokenError.safeCode)
    }

    @Test
    fun `Nexus admission accepts only canonical 64 bit integer number tokens`() {
        requireStrictNexusJsonDocument(
            """{"signed":-9223372036854775808,"unsigned":18446744073709551615}"""
        )
        listOf(
            "\uFEFF{\"value\":0}",
            """{"value":1.0}""",
            """{"value":1e0}""",
            """{"value":1E+0}""",
            """{"value":-0}""",
            """{"value":18446744073709551616}""",
            """{"value":-9223372036854775809}""",
        ).forEach { document ->
            assertEquals(
                "BOUNDED_HTTP_JSON_INVALID",
                assertThrows(BoundedHttpTextException::class.java) {
                    requireStrictNexusJsonDocument(document)
                }.safeCode,
            )
        }
    }
}
