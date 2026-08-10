package jp.co.soramitsu.common.io

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileManagerAtomicEntryPolicyTest {

    @Test
    fun `atomic cache write accepts only absent or regular non symlink entries`() {
        assertTrue(
            isSafeAtomicCacheEntryForWrite(
                existsNoFollow = false,
                isRegularFileNoFollow = false,
                isSymbolicLinkNoFollow = false,
            )
        )
        assertTrue(
            isSafeAtomicCacheEntryForWrite(
                existsNoFollow = true,
                isRegularFileNoFollow = true,
                isSymbolicLinkNoFollow = false,
            )
        )
        assertFalse(
            isSafeAtomicCacheEntryForWrite(
                existsNoFollow = true,
                isRegularFileNoFollow = true,
                isSymbolicLinkNoFollow = true,
            )
        )
        assertFalse(
            isSafeAtomicCacheEntryForWrite(
                existsNoFollow = true,
                isRegularFileNoFollow = false,
                isSymbolicLinkNoFollow = false,
            )
        )
    }
}
