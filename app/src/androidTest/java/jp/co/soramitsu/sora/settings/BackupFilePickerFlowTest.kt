package jp.co.soramitsu.sora.settings

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import jp.co.soramitsu.common.R
import jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingActivity
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Exercises the real content resolver, file provider, result callback and retained import state.
 * Files contain synthetic export shapes only; tests never continue into import or create a wallet.
 */
@RunWith(AndroidJUnit4::class)
class BackupFilePickerFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var scenario: ActivityScenario<OnboardingActivity>? = null
    private var monitor: Instrumentation.ActivityMonitor? = null
    private val fixtures = mutableListOf<File>()
    private val exportShape = """{"encoding":{"content":["pkcs8","sr25519"],"type":["scrypt","xsalsa20-poly1305"]}}"""
    @After fun close() {
        monitor?.let(instrumentation::removeMonitor)
        scenario?.close()
        fixtures.forEach(File::delete)
    }
    private fun launch() {
        scenario = ActivityScenario.launch(Intent(context, OnboardingActivity::class.java)
            .putExtra("jp.co.soramitsu.feature_multiaccount_impl.extra.ENTRY", "IMPORT_CHOOSE_SOURCE"))
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("RecoveryBackupFile").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("RecoveryBackupFile").performClick()
        compose.onNodeWithTag("RecoveryChooseFile").assertExists()
    }
    private fun choose(text: String?, cancel: Boolean = false, removeBeforeRead: Boolean = false): File? {
        val file = text?.let { File.createTempFile("fixture-backup-", ".json", context.cacheDir).apply { writeText(it); fixtures += this } }
        val result = file?.let { Intent().setData(FileProvider.getUriForFile(context, context.packageName + ".soraFileProvider", it)) }
        if (removeBeforeRead) requireNotNull(file).delete()
        monitor = instrumentation.addMonitor(IntentFilter(Intent.ACTION_GET_CONTENT).apply { addCategory(Intent.CATEGORY_OPENABLE); addDataType("*/*") },
            Instrumentation.ActivityResult(if (cancel) Activity.RESULT_CANCELED else Activity.RESULT_OK, result), true)
        compose.onNodeWithTag("RecoveryChooseFile").performClick()
        compose.waitUntil(10_000) { requireNotNull(monitor).hits > 0 }
        return file
    }
    private fun awaitFilename(name: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(name, substring = true).fetchSemanticsNodes().isNotEmpty() }
        scenario!!.onActivity { assertEquals(name, it.viewModel.recoveryState.value!!.chosenFileName) }
    }
    private fun assertUnreadable() {
        val message = context.getString(R.string.recovery_json_file_unreadable)
        compose.waitUntil(10_000) { compose.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }
        scenario!!.onActivity { assertFalse(it.viewModel.recoveryState.value!!.isButtonEnabled); assertEquals(null, it.viewModel.recoveryState.value!!.chosenFileName) }
    }
    @Test fun validFileDisplaysOnlyItsNameAndRetainsTheSelectionAcrossRecreation() {
        launch(); val file = requireNotNull(choose(exportShape)); awaitFilename(file.name)
        compose.onNodeWithText(exportShape).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.common_continue)).assertIsNotEnabled()
        compose.onNodeWithTag("RecoveryFilePassword").performTextReplacement("synthetic password")
        compose.onNodeWithText(context.getString(R.string.common_continue)).assertIsEnabled()
        scenario!!.recreate(); awaitFilename(file.name)
        compose.onNodeWithTag("RecoveryFilePassword").assertTextContains("synthetic password")
    }
    @Test fun fileAtTheByteLimitIsAcceptedWithoutPlacingItsContentsInTheEditor() {
        launch(); val file = requireNotNull(choose(exportShape.padEnd(128 * 1024, ' '))); awaitFilename(file.name)
        scenario!!.onActivity { assertEquals("", it.viewModel.recoveryState.value!!.recoveryInputState.value.text) }
    }
    @Test fun fileOneByteBeyondTheLimitIsRejected() { launch(); choose(exportShape.padEnd(128 * 1024 + 1, ' ')); assertUnreadable() }
    @Test fun aDocumentRemovedBeforeItsStreamOpensIsRejected() { launch(); choose(exportShape, removeBeforeRead = true); assertUnreadable() }
    @Test fun malformedFileIsRejectedByTheRealBackupShapeValidator() { launch(); choose("{not an encrypted export}"); assertUnreadable() }
    @Test fun cancellingThePickerLeavesTheImportFormEmptyAndDisabled() {
        launch(); choose(null, cancel = true); compose.waitForIdle()
        scenario!!.onActivity { assertEquals(null, it.viewModel.recoveryState.value!!.chosenFileName); assertFalse(it.viewModel.recoveryState.value!!.isButtonEnabled) }
        compose.onNodeWithTag("RecoveryFilePassword").assertExists()
        compose.onNodeWithText(context.getString(R.string.recovery_json_file_unreadable)).assertDoesNotExist()
    }
}
