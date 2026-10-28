package jp.co.soramitsu.sora

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class WalletLogTreesTest {
    private data class Entry(val priority: Int, val tag: String?, val message: String)
    private val entries = mutableListOf<Entry>()
    private val installed = mutableListOf<Timber.Tree>()
    private val codes = listOf("NEXUS_PENDING_RECOVERY_SCHEDULING_FAILED", "SORA2_PENDING_RECOVERY_SCHEDULING_FAILED")

    @Before fun capturePlatformLogs() {
        mockkStatic(Log::class)
        every { Log.println(any(), any(), any()) } answers {
            entries.add(Entry(firstArg(), secondArg(), thirdArg()))
            0
        }
    }

    @After fun removeOnlyTestTrees() {
        installed.forEach(Timber::uproot)
        unmockkStatic(Log::class)
    }

    private fun install(verbose: Boolean) {
        installed.add(installWalletLogTree(verbose))
    }

    @Test fun releaseInstallationWritesBothExactRecoveryCodesToPlatformLog() {
        install(verbose = false)
        codes.forEach { Timber.tag("WalletRecoveryStartup").w(it) }
        assertEquals(codes.map { Entry(Log.WARN, "WalletRecoveryStartup", it) }, entries)
    }

    @Test fun releaseInstallationRejectsOtherPrioritiesTagsMessagesAndExceptionData() {
        install(verbose = false)
        for (code in codes) {
            for (priority in listOf(Log.VERBOSE, Log.DEBUG, Log.INFO, Log.ERROR, Log.ASSERT)) {
                Timber.tag("WalletRecoveryStartup").log(priority, code)
            }
            for (tag in listOf("Other", "WalletRecoveryStartupExtra", "")) Timber.tag(tag).w(code)
            Timber.w(code)
            Timber.tag("WalletRecoveryStartup").w("$code sensitive suffix")
            Timber.tag("WalletRecoveryStartup").w(IllegalStateException("sensitive fixture"), code)
        }
        Timber.tag("WalletRecoveryStartup").w("unknown diagnostic")
        Timber.tag("WalletRecoveryStartup").w("sensitive address and recovery data")
        assertTrue("Release logging must not forward arbitrary messages or exception data", entries.isEmpty())
    }

    @Test fun rejectedReleaseMessageDoesNotSuppressLaterAllowedDiagnostic() {
        install(verbose = false)
        Timber.tag("WalletRecoveryStartup").w(IllegalStateException("private"), codes.first())
        Timber.tag("WalletRecoveryStartup").w(codes.last())
        assertEquals(listOf(Entry(Log.WARN, "WalletRecoveryStartup", codes.last())), entries)
    }

    @Test fun verboseInstallationKeepsExistingDebugTagAndExplicitTags() {
        install(verbose = true)
        Timber.i("debug fixture")
        Timber.tag("Explicit").d("explicit fixture")
        assertEquals(2, entries.size)
        assertEquals(Log.INFO, entries.first().priority)
        assertTrue(entries.first().tag.orEmpty().startsWith("SORA_LOG: "))
        assertEquals("debug fixture", entries.first().message)
        assertEquals(Entry(Log.DEBUG, "Explicit", "explicit fixture"), entries.last())
    }
}
