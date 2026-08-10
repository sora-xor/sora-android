package jp.co.soramitsu.common.io

import android.content.Context
import android.system.Os
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileManagerAtomicRecoveryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearExactTestEntriesBeforeRun() {
        removeExactTestEntries()
    }

    @After
    fun removeExactTestEntries() {
        TEST_ENTRY_NAMES.forEach(::deleteAtomicEntry)
        Files.deleteIfExists(File(context.cacheDir, SYMLINK_TARGET).toPath())
    }

    @Test
    fun interruptedAtomicFileWriteRecoversTheLastCompleteValueAfterReopen() {
        val fileName = "${ENTRY_PREFIX}interrupted"
        val manager = FileManagerImpl(context)
        manager.writeInternalCacheFileAtomically(fileName, "old-complete")

        val atomicFile = AtomicFile(File(context.cacheDir, fileName))
        val interruptedOutput = atomicFile.startWrite()
        interruptedOutput.write("new-partial".toByteArray(Charsets.UTF_8))
        interruptedOutput.flush()
        interruptedOutput.fd.sync()
        // Model process death: close the OS descriptor without calling finishWrite or failWrite.
        interruptedOutput.close()

        assertEquals(
            "old-complete",
            FileManagerImpl(context).readInternalCacheFileAtomically(fileName, 64),
        )
    }

    @Test
    fun failedAtomicFileWriteNeverPublishesItsPartialValue() {
        val fileName = "${ENTRY_PREFIX}failed"
        val manager = FileManagerImpl(context)
        manager.writeInternalCacheFileAtomically(fileName, "old-complete")

        val atomicFile = AtomicFile(File(context.cacheDir, fileName))
        val failedOutput = atomicFile.startWrite()
        failedOutput.write("new-partial".toByteArray(Charsets.UTF_8))
        atomicFile.failWrite(failedOutput)

        assertEquals(
            "old-complete",
            FileManagerImpl(context).readInternalCacheFileAtomically(fileName, 64),
        )
    }

    @Test
    fun completedAtomicFileWritePublishesOnlyTheNewCompleteValue() {
        val fileName = "${ENTRY_PREFIX}completed"
        val manager = FileManagerImpl(context)
        manager.writeInternalCacheFileAtomically(fileName, "old-complete")
        manager.writeInternalCacheFileAtomically(fileName, "new-complete")

        assertEquals(
            "new-complete",
            FileManagerImpl(context).readInternalCacheFileAtomically(fileName, 64),
        )
    }

    @Test
    fun atomicWriterRejectsBaseBackupAndNewSymlinksWithoutFollowingThem() {
        val target = File(context.cacheDir, SYMLINK_TARGET)
        target.writeText("untouched", Charsets.UTF_8)

        listOf("", ".bak", ".new").forEachIndexed { index, suffix ->
            val fileName = "${ENTRY_PREFIX}symlink-$index"
            val link = File(context.cacheDir, "$fileName$suffix")
            Os.symlink(target.absolutePath, link.absolutePath)

            val error = assertThrows(IOException::class.java) {
                FileManagerImpl(context).writeInternalCacheFileAtomically(
                    fileName,
                    "must-not-be-written",
                )
            }
            assertTrue(error.message.orEmpty().startsWith("INTERNAL_CACHE_FILE_NOT_REGULAR:"))
            assertEquals("untouched", target.readText(Charsets.UTF_8))
        }
    }

    private fun deleteAtomicEntry(fileName: String) {
        listOf(fileName, "$fileName.bak", "$fileName.new").forEach { exactName ->
            Files.deleteIfExists(File(context.cacheDir, exactName).toPath())
        }
    }

    private companion object {
        const val ENTRY_PREFIX = "sora-runtime-atomic-recovery-test-"
        const val SYMLINK_TARGET = "${ENTRY_PREFIX}target"
        val TEST_ENTRY_NAMES = listOf(
            "${ENTRY_PREFIX}interrupted",
            "${ENTRY_PREFIX}failed",
            "${ENTRY_PREFIX}completed",
            "${ENTRY_PREFIX}symlink-0",
            "${ENTRY_PREFIX}symlink-1",
            "${ENTRY_PREFIX}symlink-2",
        )
    }
}
