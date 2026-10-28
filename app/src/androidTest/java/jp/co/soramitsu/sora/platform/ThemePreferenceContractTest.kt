package jp.co.soramitsu.sora.platform

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.domain.DarkThemeManager
import jp.co.soramitsu.common.presentation.compose.theme.neon.NeonEffectsPreference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

/** Real Android theme resolution, with presentation settings isolated from wallet preferences. */
@RunWith(AndroidJUnit4::class)
class ThemePreferenceContractTest {
    private suspend fun fixture(night: Boolean, body: suspend (DarkThemeManager, MutableMap<String, Boolean>, Context) -> Unit) {
        val originalMode = AppCompatDelegate.getDefaultNightMode()
        val originalEffects = NeonEffectsPreference.reduceEffects.value
        val app = ApplicationProvider.getApplicationContext<Context>()
        val settings = mutableMapOf<String, Boolean>()
        val preferenceName = "theme-test-${UUID.randomUUID()}"
        val config = Configuration(app.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val context = object : ContextWrapper(app.createConfigurationContext(config)) {
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences(preferenceName, mode)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val dispatchers = mockk<CoroutineManager>()
        every { dispatchers.main } returns Dispatchers.Main
        every { dispatchers.applicationScope } returns scope
        val prefs = mockk<SoraPreferences>()
        coEvery { prefs.putBoolean(any(), any()) } coAnswers { settings[firstArg()] = secondArg(); Unit }
        coEvery { prefs.getBoolean(any(), any()) } coAnswers { settings[firstArg()] ?: secondArg<Boolean>() }
        try {
            withContext(Dispatchers.Main) { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) }
            val manager = withContext(Dispatchers.Main) { DarkThemeManager(context, dispatchers, prefs) }
            withTimeout(3_000) { manager.mutableDarkThemeSharedFlow.subscriptionCount.first { it > 0 } }
            body(manager, settings, context)
        } finally {
            scope.cancel()
            withContext(Dispatchers.Main) { AppCompatDelegate.setDefaultNightMode(originalMode); NeonEffectsPreference.update(originalEffects) }
            app.deleteSharedPreferences(preferenceName)
        }
    }
    private suspend fun settled() = withContext(Dispatchers.Main) { repeat(5) { yield() } }
    @Test fun explicitThemeChoiceDisablesSystemPreferenceAndMirrorsTheChosenMode() = runBlocking {
        fixture(false) { manager, settings, context ->
            for (dark in listOf(true, true, false, false)) {
                manager.setDarkThemeEnabled(dark); settled()
                assertEquals(dark, manager.darkModeStatusFlow.value)
                assertEquals(false, settings["KEY_SYSTEM_DRIVEN_UI_ENABLED"])
                assertEquals(dark, settings["KEY_DARK_THEME_ENABLED"])
                val expected = if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                assertEquals(expected, AppCompatDelegate.getDefaultNightMode())
                assertEquals(expected, context.getSharedPreferences("mirror", 0).getInt("night_mode", -99))
            }
        }
    }
    @Test fun systemThemeUsesResourcesAndMirrorsFollowSystemForTheNextLaunch() = runBlocking {
        for (night in listOf(false, true)) fixture(night) { manager, settings, context ->
            manager.setSystemDrivenUiEnabled(true); settled()
            assertEquals(night, manager.darkModeStatusFlow.value)
            assertEquals(true, settings["KEY_SYSTEM_DRIVEN_UI_ENABLED"])
            assertEquals(false, settings["KEY_DARK_THEME_ENABLED"])
            assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, context.getSharedPreferences("mirror", 0).getInt("night_mode", -99))
            manager.setSystemDrivenUiEnabled(false); settled()
            assertFalse(manager.darkModeStatusFlow.value)
        }
    }
    @Test fun reduceEffectsUpdatesItsOwnPreferenceWithoutChangingThemeChoice() = runBlocking {
        fixture(false) { manager, settings, _ ->
            manager.setDarkThemeEnabled(true); settled()
            val theme = settings.toMap()
            for (enabled in listOf(true, false)) {
                manager.setReduceEffects(enabled)
                assertEquals(enabled, manager.reduceEffectsFlow.value)
                assertEquals(enabled, settings[NeonEffectsPreference.KEY])
                theme.forEach { (key, value) -> assertEquals(value, settings[key]) }
            }
        }
    }
}
