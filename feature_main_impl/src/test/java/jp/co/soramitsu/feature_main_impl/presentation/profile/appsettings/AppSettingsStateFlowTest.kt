package jp.co.soramitsu.feature_main_impl.presentation.profile.appsettings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import io.mockk.*
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DarkThemeManager
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.feature_blockexplorer_api.data.*
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

/** Saved preference and capability snapshots drive settings; navigation and writes are inert. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsStateFlowTest {
    @get:Rule val main = MainCoroutineRule()
    @get:Rule val liveData = InstantTaskExecutorRule()
    private val theme = MutableSharedFlow<DarkThemeManager.DarkModeSettings>(replay = 1).apply {
        tryEmit(DarkThemeManager.DarkModeSettings(false, true))
    }
    private val effects = MutableStateFlow(false)
    private val features = MutableStateFlow(ProductionFeatureState(true, true, false, false, true, false, false))
    private val router = mockk<MainRouter>(relaxed = true)
    private val darkManager = mockk<DarkThemeManager>()
    private val featureManager = mockk<ProductionFeatureManager>()
    private val store = ViewModelStore()
    @Before fun setup() {
        mockkObject(FirebaseWrapper); every { FirebaseWrapper.recordException(any()) } just Runs
        every { darkManager.mutableDarkThemeSharedFlow } returns theme
        every { darkManager.reduceEffectsFlow } returns effects
        coEvery { darkManager.setDarkThemeEnabled(any()) } coAnswers { theme.emit(DarkThemeManager.DarkModeSettings(false, firstArg())) }
        coEvery { darkManager.setSystemDrivenUiEnabled(any()) } coAnswers { theme.emit(DarkThemeManager.DarkModeSettings(firstArg(), false)) }
        coEvery { darkManager.setReduceEffects(any()) } answers { effects.value = firstArg() }
        every { featureManager.observeState() } returns features
        coEvery { featureManager.getState() } answers { features.value }
        coEvery { featureManager.setTairaVisible(any()) } answers { features.value = features.value.copy(tairaVisible = firstArg()) }
    }
    @After fun cleanup() { store.clear(); unmockkObject(FirebaseWrapper) }
    private fun vm() = AppSettingsViewModel(router, darkManager, featureManager).also { store.put("vm", it) }
    @Test fun initialStateUsesSavedThemeAndFeatureSnapshot() = runTest {
        val vm = vm(); runCurrent(); assertTrue(vm.state.darkModeChecked); assertFalse(vm.state.systemAppearanceChecked)
        assertTrue(vm.state.testNetworksAvailable); assertFalse(vm.state.testNetworksChecked)
        assertEquals(R.string.settings_header_app, vm.toolbarState.value!!.basic.title)
    }
    @Test fun languageActionUsesLanguagePicker() { vm().onLanguageClick(); verify(exactly = 1) { router.showSelectLanguage() } }
    @Test fun externalThemeChangesUpdateBothAppearanceSwitches() = runTest {
        val vm = vm(); runCurrent(); theme.emit(DarkThemeManager.DarkModeSettings(true, false)); runCurrent()
        assertTrue(vm.state.systemAppearanceChecked); assertFalse(vm.state.darkModeChecked)
    }
    @Test fun systemAppearanceUsesSavedResult() = runTest {
        val vm = vm(); runCurrent(); vm.toggleSystemAppearance(true); runCurrent()
        coVerify(exactly = 1) { darkManager.setSystemDrivenUiEnabled(true) }
        assertTrue(vm.state.systemAppearanceChecked); assertFalse(vm.state.darkModeChecked)
    }
    @Test fun explicitDarkChoiceDisablesSystemAppearance() = runTest {
        theme.emit(DarkThemeManager.DarkModeSettings(true, false)); val vm = vm(); runCurrent(); vm.toggleDarkMode(true); runCurrent()
        assertTrue(vm.state.darkModeChecked); assertFalse(vm.state.systemAppearanceChecked)
    }
    @Test fun failedSystemPreferenceWriteDoesNotFalselyChangeSwitches() = runTest {
        coEvery { darkManager.setSystemDrivenUiEnabled(any()) } throws IllegalStateException("preference write failed")
        val vm = vm(); runCurrent(); val previous = vm.state; vm.toggleSystemAppearance(true); runCurrent(); assertEquals(previous, vm.state)
    }
    @Test fun failedDarkPreferenceWriteDoesNotFalselyChangeSwitches() = runTest {
        coEvery { darkManager.setDarkThemeEnabled(any()) } throws IllegalStateException("preference write failed")
        val vm = vm(); runCurrent(); val previous = vm.state; vm.toggleDarkMode(false); runCurrent(); assertEquals(previous, vm.state)
    }
    @Test fun savedThemeSnapshotWinsOverRequestedValue() = runTest {
        coEvery { darkManager.setDarkThemeEnabled(any()) } coAnswers { theme.emit(DarkThemeManager.DarkModeSettings(true, false)) }
        val vm = vm(); runCurrent(); vm.toggleDarkMode(true); runCurrent()
        assertTrue(vm.state.systemAppearanceChecked); assertFalse(vm.state.darkModeChecked)
    }
    @Test fun cancelledPreferenceWriteDoesNotReportSuccess() = runTest {
        coEvery { darkManager.setDarkThemeEnabled(any()) } throws CancellationException("fixture")
        val vm = vm(); runCurrent(); val previous = vm.state; vm.toggleDarkMode(false); runCurrent(); assertEquals(previous, vm.state)
        verify(exactly = 0) { FirebaseWrapper.recordException(any()) }
    }
    @Test fun reducedEffectsChoiceFollowsSavedFlow() = runTest {
        val vm = vm(); runCurrent(); vm.toggleReduceEffects(true); runCurrent(); assertTrue(vm.state.reduceEffectsChecked)
        effects.value = false; runCurrent(); assertFalse(vm.state.reduceEffectsChecked)
    }
    @Test fun unavailableTestNetworkCannotBeEnabledByHandler() = runTest {
        features.value = features.value.copy(tairaAvailable = false); val vm = vm(); runCurrent(); vm.toggleTestNetworks(true); runCurrent()
        coVerify(exactly = 0) { featureManager.setTairaVisible(any()) }; assertFalse(vm.state.testNetworksChecked)
    }
    @Test fun availableTestNetworkRequiresExplicitPreferenceChange() = runTest {
        val vm = vm(); runCurrent(); vm.toggleTestNetworks(true); runCurrent()
        coVerify(exactly = 1) { featureManager.setTairaVisible(true) }; assertTrue(vm.state.testNetworksChecked)
    }
    @Test fun failedTestNetworkPreferenceLeavesCurrentSnapshot() = runTest {
        coEvery { featureManager.setTairaVisible(any()) } throws IllegalStateException("write failed")
        val vm = vm(); runCurrent(); vm.toggleTestNetworks(true); runCurrent(); assertFalse(vm.state.testNetworksChecked)
    }
    @Test fun emergencyCapabilityRevocationUpdatesOpenSettings() = runTest {
        val vm = vm(); runCurrent(); features.value = features.value.copy(tairaAvailable = false, tairaVisible = false); runCurrent()
        assertFalse(vm.state.testNetworksAvailable); vm.toggleTestNetworks(true); runCurrent()
        coVerify(exactly = 0) { featureManager.setTairaVisible(any()) }
    }
    @Test fun preferenceChangedElsewhereUpdatesOpenSettings() = runTest {
        val vm = vm(); runCurrent(); features.value = features.value.copy(tairaVisible = true); runCurrent(); assertTrue(vm.state.testNetworksChecked)
    }
    @Test fun failedRefreshStillShowsAndObservesPersistedCapabilitySnapshot() = runTest {
        coEvery { featureManager.getState() } throws IllegalStateException("offline")
        val vm = vm(); runCurrent(); assertTrue(vm.state.testNetworksAvailable)
        features.value = features.value.copy(tairaVisible = true); runCurrent(); assertTrue(vm.state.testNetworksChecked)
    }
    @Test fun effectiveNetworkSnapshotWinsOverRequestedVisibility() = runTest {
        coEvery { featureManager.setTairaVisible(any()) } answers { features.value = features.value.copy(tairaAvailable = false, tairaVisible = false) }
        val vm = vm(); runCurrent(); vm.toggleTestNetworks(true); runCurrent()
        assertFalse(vm.state.testNetworksChecked); assertFalse(vm.state.testNetworksAvailable)
    }
    @Test fun clearingViewModelStopsPreferenceObservers() = runTest {
        val vm = vm(); runCurrent(); val previous = vm.state; store.clear()
        effects.value = true; theme.emit(DarkThemeManager.DarkModeSettings(true, false)); features.value = features.value.copy(tairaVisible = true); runCurrent()
        assertEquals(previous, vm.state)
    }
}
