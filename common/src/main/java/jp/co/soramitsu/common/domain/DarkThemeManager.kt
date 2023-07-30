package jp.co.soramitsu.common.domain

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.data.SoraPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class DarkThemeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coroutineManager: CoroutineManager,
    private val soraPreferences: SoraPreferences,
) {

    private data class DarkModeSettings(
        val isSystemDrivenUiEnabled: Boolean,
        val isDarkModeEnabled: Boolean
    )

    private val mutableDarkThemeSharedFlow = MutableSharedFlow<DarkModeSettings>(
        replay = 1,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * [darkModeStatusFlow] is used to read saved state of dark mode preferences off of SoraPreferences
     * and align these results with Application Configuration
     *
     * Hot StateFlow is used to share the same state of UI mode between screens, and avoid unnecessary
     * theme switches
     */
    val darkModeStatusFlow: StateFlow<Boolean> = mutableDarkThemeSharedFlow
        .mapLatest { darkModeSettings ->
            with(darkModeSettings) {
                return@mapLatest if (isSystemDrivenUiEnabled) {
                    /*
                        Resources.Configuration.uiMode as opposed to AppCompatDelegate.getDefaultNightMode()
                        is set to Configuration.UI_MODE_NIGHT_YES when system dark mode is enabled
                        (e.g. extreme battery settings on, or system dark mode is enabled)
                     */
                    val currentAppUiMode = context.resources.configuration.uiMode
                        .and(Configuration.UI_MODE_NIGHT_MASK)

                    if (currentAppUiMode == Configuration.UI_MODE_NIGHT_YES)
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    else
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

                    AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
                } else {
                    /*
                        On the other hand, AppCompatDelegate controls dark theme in scope of
                        our application
                     */
                    val currentAppUiMode = AppCompatDelegate.getDefaultNightMode()

                    when {
                        currentAppUiMode == AppCompatDelegate.MODE_NIGHT_YES && isDarkModeEnabled -> {
                            /*
                                Saved state matches system configuration;
                                no need to change anything
                             */
                            true
                        }
                        currentAppUiMode == AppCompatDelegate.MODE_NIGHT_NO && !isDarkModeEnabled -> {
                            /*
                                Saved state matches system configuration;
                                no need to change anything
                             */
                            false
                        }
                        else -> {
                            /*
                                Something went wrong, saved state is different from system config;
                                might happen due to start of the whole application (vs. moving between screens),
                                while user system is set to light mode
                             */

                            val newUiMode = if (isDarkModeEnabled)
                                AppCompatDelegate.MODE_NIGHT_YES else
                                AppCompatDelegate.MODE_NIGHT_NO

                            AppCompatDelegate.setDefaultNightMode(newUiMode)

                            AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
                        }
                    }
                }
            }
        }.flowOn(coroutineManager.main.immediate).stateIn(
            scope = coroutineManager.applicationScope,
            started = SharingStarted.Eagerly, // Eager mode is used to apply changes as soon as possible
            initialValue = AppCompatDelegate.getDefaultNightMode()
                .equals(AppCompatDelegate.MODE_NIGHT_YES)
        )

    fun updateUiModeFromCache() {
        coroutineManager.applicationScope.launch {
            mutableDarkThemeSharedFlow.emit(
                value = DarkModeSettings(
                    isSystemDrivenUiEnabled = soraPreferences.getBoolean(
                        field = KEY_SYSTEM_DRIVEN_UI_ENABLED,
                        defaultValue = false
                    ),
                    isDarkModeEnabled = soraPreferences.getBoolean(
                        field = KEY_DARK_THEME_ENABLED,
                        defaultValue = false
                    )
                )
            )
        }
    }

    suspend fun setDarkThemeEnabled(isEnabled: Boolean) {
        val newDarkModeValue = soraPreferences.run {
            putBoolean(KEY_SYSTEM_DRIVEN_UI_ENABLED, false) // deactivate this, if the other is chosen
            putBoolean(KEY_DARK_THEME_ENABLED, isEnabled)
            getBoolean(KEY_DARK_THEME_ENABLED, false) // double check, in case it didn't get saved
        }

        // update listeners with new value
        mutableDarkThemeSharedFlow.emit(
            value = DarkModeSettings(
                isSystemDrivenUiEnabled = false,
                isDarkModeEnabled = newDarkModeValue
            )
        )
    }

    suspend fun setSystemDrivenUiEnabled(isEnabled: Boolean) {
        val newDarkModeValue = soraPreferences.run {
            putBoolean(KEY_DARK_THEME_ENABLED, false) // deactivate this, if the other is chosen
            putBoolean(KEY_SYSTEM_DRIVEN_UI_ENABLED, isEnabled)
            getBoolean(KEY_SYSTEM_DRIVEN_UI_ENABLED, false) // double check, in case it didn't get saved
        }

        // update listeners with new value
        mutableDarkThemeSharedFlow.emit(
            value = DarkModeSettings(
                isSystemDrivenUiEnabled = newDarkModeValue,
                isDarkModeEnabled = false
            )
        )
    }

    private companion object {
        const val KEY_DARK_THEME_ENABLED = "KEY_DARK_THEME_ENABLED"
        const val KEY_SYSTEM_DRIVEN_UI_ENABLED = "KEY_SYSTEM_DRIVEN_UI_ENABLED"
    }
}
