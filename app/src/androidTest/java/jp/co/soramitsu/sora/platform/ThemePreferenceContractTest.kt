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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

/** Real Android theme resolution, with presentation settings isolated from wallet preferences. */
@RunWith(AndroidJUnit4::class)
class ThemePreferenceContractTest {
    private suspend fun fixture(night: Boolean, body: suspend (DarkThemeManager, MutableMap<String, Boolean>, Context) -> Unit) =
        fixtureWith(night) { manager, settings, context, _ -> body(manager, settings, context) }
    private suspend fun fixtureWith(
        night: Boolean,
        body: suspend (DarkThemeManager, MutableMap<String, Boolean>, Context, SoraPreferences) -> Unit,
    ) {
        val originalMode = AppCompatDelegate.getDefaultNightMode()
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
            body(manager, settings, context, prefs)
        } finally {
            scope.cancel()
            withContext(Dispatchers.Main) { AppCompatDelegate.setDefaultNightMode(originalMode) }
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
    /** The 3.9.2 open-testing build stored a "Reduce effects" switch; the value stays on the phone, unread. */
    @Test fun storedReduceEffectsValueIsLeftInPlaceAndNotRead() = runBlocking {
        fixtureWith(false) { manager, settings, _, prefs ->
            settings["neon_reduce_effects"] = true
            settings["KEY_DARK_THEME_ENABLED"] = true
            manager.updateUiModeFromCache()
            assertTrue(withTimeout(3_000) { manager.darkModeStatusFlow.first { it } })
            assertEquals(true, settings["neon_reduce_effects"])
            coVerify(exactly = 0) { prefs.getBoolean("neon_reduce_effects", any()) }
            coVerify(exactly = 0) { prefs.putBoolean("neon_reduce_effects", any()) }
        }
    }
}
