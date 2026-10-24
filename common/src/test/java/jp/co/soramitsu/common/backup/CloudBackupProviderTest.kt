package jp.co.soramitsu.common.backup

import jp.co.soramitsu.xbackup.BackupService
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class CloudBackupProviderTest {
    @Test fun `unavailable backup never evaluates the Google factory`() {
        val provider = CloudBackupProvider(false) { error("Google SDK must remain uninitialized") }
        assertFalse(provider.isAvailable)
        repeat(2) { assertNull(provider.serviceOrNull()) }
    }
    @Test fun `configured backup is lazy and shares one service`() {
        var constructions = 0
        val service = mock(BackupService::class.java)
        val provider = CloudBackupProvider(true) { constructions++; service }
        assertEquals(0, constructions)
        assertSame(service, provider.serviceOrNull())
        assertSame(service, provider.serviceOrNull())
        assertEquals(1, constructions)
    }
}
