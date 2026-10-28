@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.settings

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.material.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.backup.CloudBackupProvider
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_main_impl.presentation.profile.debugmenu.*
import jp.co.soramitsu.feature_wallet_impl.presentation.cardshub.CommonHubCard
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.xbackup.BackupService
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticAndHubBehaviorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun diagnosticStateContainsDeviceBuildAndRuntimeValues() = runBlocking {
        val runtime = mockk<RuntimeManager>(relaxed = true)
        val provider = CloudBackupProvider(false) { error("Unavailable cloud backup must not initialize") }
        val vm = withContext(Dispatchers.Main) { DebugMenuViewModel(runtime, provider) }
        assertTrue(vm.state.settings.any { it.name == "App ID:" && it.value.isNotEmpty() })
        assertTrue(vm.state.settings.any { it.name == "Arch:" && it.value.contains("arm64") })
        compose.setContent { SoraAppTheme { DebugMenuScreen(vm.state) } }
        compose.onNodeWithText("App ID:").assertIsDisplayed()
        compose.onNodeWithText("Build Flavor:").performScrollTo().assertIsDisplayed()
        withContext(Dispatchers.Main) { vm.onResetRuntimeClick() }
        coVerify(exactly = 1) { runtime.resetRuntimeVersion() }
    }

    @Test fun unavailableCloudAccountChangeExplainsTheMissingCapability() = runBlocking {
        val vm = withContext(Dispatchers.Main) { DebugMenuViewModel(mockk(relaxed = true), CloudBackupProvider(false) { error("Must not initialize") }) }
        withContext(Dispatchers.Main) { vm.onChangeGoogleAccount(mockk()) }
        assertEquals(R.string.wallet_cloud_backup_unavailable, vm.errorFromResourceLiveData.value?.second)
    }

    @Test fun accountChangeLogsOutBeforeAuthorizationAndReportsFailureWithoutCrashing() = runBlocking {
        val service = mockk<BackupService>(relaxed = true)
        val launcher = mockk<ActivityResultLauncher<Intent>>()
        val vm = withContext(Dispatchers.Main) { DebugMenuViewModel(mockk(relaxed = true), CloudBackupProvider(true) { service }) }
        withContext(Dispatchers.Main) { vm.onChangeGoogleAccount(launcher) }
        coVerifyOrder { service.logout(); service.authorize(launcher) }
        coEvery { service.logout() } throws IllegalStateException("cloud unavailable")
        withContext(Dispatchers.Main) { vm.onChangeGoogleAccount(launcher) }
        assertEquals(R.string.wallet_cloud_backup_failed, vm.errorFromResourceLiveData.value?.second)
        withContext(Dispatchers.Main) { vm.onSuccessfulGoogleSignIn() }
        assertNotNull(vm.googleSignInEvent.value)
    }

    @Test fun hubCollapseHidesDetailsAndExpansionKeepsItsOwnNavigationAction() {
        val collapsed = mutableStateOf(false)
        var opened = 0
        compose.setContent { SoraAppTheme {
            CommonHubCard(R.string.pooled_assets, "$12.50", collapsed.value, { opened++ }, { collapsed.value = !collapsed.value }) { Text("Pool position fixture") }
        } }
        compose.onNodeWithText("Pool position fixture").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.common_expand)).performClick()
        compose.runOnIdle { assertEquals(1, opened) }
        compose.onNodeWithText(compose.activity.getString(R.string.pooled_assets)).performClick()
        compose.onNodeWithText("Pool position fixture").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.common_expand)).assertDoesNotExist()
        compose.onNodeWithText("$12.50").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.pooled_assets)).performClick()
        compose.onNodeWithText("Pool position fixture").assertIsDisplayed()
    }
}
