package jp.co.soramitsu.common.util

import jp.co.soramitsu.xcrypto.hash.xxhash.xxHash128
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class XcryptoHashCompatibilityTest {
    @Test
    fun `patched xxhash dependency retains Substrate storage prefixes`() {
        // Public Substrate Twox128 vectors; changing these breaks existing storage lookups.
        assertArrayEquals(hex("26aa394eea5630e07c48ae0c9558cef7"), "System".toByteArray().xxHash128())
        assertArrayEquals(hex("b99d880ec681799c0cf30e8886371da9"), "Account".toByteArray().xxHash128())
    }

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
