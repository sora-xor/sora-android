/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.common.domain

import android.content.Context
import android.content.res.Configuration
import java.io.File
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.presentation.compose.theme.neon.NeonEffectsPreference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class DarkThemeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coroutineManager: CoroutineManager,
    private val soraPreferences: SoraPreferences,
) {

    data class DarkModeSettings(
        val isSystemDrivenUiEnabled: Boolean,
        val isDarkModeEnabled: Boolean
    )

    val mutableDarkThemeSharedFlow = MutableSharedFlow<DarkModeSettings>(
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
                    mirrorNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

                    AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
                } else {
                    /*
                        On the other hand, AppCompatDelegate controls dark theme in scope of
                        our application
                     */
                    val currentAppUiMode = AppCompatDelegate.getDefaultNightMode()
                    mirrorNightMode(
                        if (isDarkModeEnabled) AppCompatDelegate.MODE_NIGHT_YES
                        else AppCompatDelegate.MODE_NIGHT_NO
                    )

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
//            Eager mode is used to apply changes as soon as possible
            started = SharingStarted.Eagerly,
            initialValue = AppCompatDelegate.getDefaultNightMode()
                .equals(AppCompatDelegate.MODE_NIGHT_YES)
        )

    /**
     * The in-app "Reduce effects" switch (SORA Neon). Stored under its own key; the theme keys
     * above are never touched by it.
     */
    val reduceEffectsFlow: StateFlow<Boolean> = NeonEffectsPreference.reduceEffects

    fun updateUiModeFromCache() {
        coroutineManager.applicationScope.launch {
            runCatching {
                soraPreferences.getBoolean(field = NeonEffectsPreference.KEY, defaultValue = false)
            }.onSuccess(NeonEffectsPreference::update)
                .onFailure { Timber.w(it, "Could not read the reduce effects setting") }
        }
        coroutineManager.applicationScope.launch {
            mutableDarkThemeSharedFlow.emit(
                value = DarkModeSettings(
                    isSystemDrivenUiEnabled = soraPreferences.getBoolean(
                        field = KEY_SYSTEM_DRIVEN_UI_ENABLED,
                        defaultValue = false
                    ),
                    isDarkModeEnabled = soraPreferences.getBoolean(
                        field = KEY_DARK_THEME_ENABLED,
                        defaultValue = context.getSharedPreferences(NIGHT_MODE_MIRROR_FILE, Context.MODE_PRIVATE)
                            .getBoolean(KEY_NEW_INSTALL_DARK, false)
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

    suspend fun setReduceEffects(enabled: Boolean) {
        soraPreferences.putBoolean(NeonEffectsPreference.KEY, enabled)
        NeonEffectsPreference.update(
            soraPreferences.getBoolean(NeonEffectsPreference.KEY, false) // double check, as above
        )
    }

    /**
     * Copies the resolved night mode into a tiny synchronous file, so the next cold start can
     * apply it before the first Activity (the splash) is drawn. The stored theme settings above
     * stay the source of truth and are never changed here.
     */
    private fun mirrorNightMode(mode: Int) {
        runCatching {
            context.getSharedPreferences(NIGHT_MODE_MIRROR_FILE, Context.MODE_PRIVATE).edit {
                putInt(KEY_NIGHT_MODE_MIRROR, mode)
            }
        }.onFailure { Timber.w(it, "Could not mirror the night mode") }
    }

    companion object {
        private const val KEY_DARK_THEME_ENABLED = "KEY_DARK_THEME_ENABLED"
        private const val KEY_SYSTEM_DRIVEN_UI_ENABLED = "KEY_SYSTEM_DRIVEN_UI_ENABLED"
        private const val NIGHT_MODE_MIRROR_FILE = "sora_night_mode_mirror"
        private const val KEY_NIGHT_MODE_MIRROR = "night_mode"
        private const val KEY_NEW_INSTALL_DARK = "new_install_dark_default"

        /** Capture before startup creates wallet/preferences files. Upgrades retain the old light
         * default and every explicit theme choice still wins. This writes presentation metadata
         * only, never wallet settings or credentials. Any existing app data means "not new". */
        fun captureNewInstallDefault(context: Context) {
            runCatching {
                val mirror = context.getSharedPreferences(NIGHT_MODE_MIRROR_FILE, Context.MODE_PRIVATE)
                if (!mirror.contains(KEY_NEW_INSTALL_DARK)) {
                    val roots = listOf(File(context.applicationInfo.dataDir, "databases"),
                        File(context.applicationInfo.dataDir, "shared_prefs"), context.filesDir, context.noBackupFilesDir)
                    val newInstall = roots.all { directory ->
                        !directory.exists() || directory.listFiles()?.isEmpty() == true
                    }
                    val editor = mirror.edit()
                    editor.putBoolean(KEY_NEW_INSTALL_DARK, newInstall)
                    if (!editor.commit()) throw IllegalStateException("Unable to persist the initial theme")
                    if (newInstall) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                }
            }.onFailure { Timber.w(it, "Could not capture the initial theme default") }
        }

        /**
         * Applies the night mode mirrored on the previous run, before any Activity exists, so a
         * dark-theme user does not see a light splash first. Does nothing on the first run after
         * install or upgrade (the asynchronous [updateUiModeFromCache] still decides then).
         */
        fun applyCachedNightMode(context: Context) {
            runCatching {
                val mode = context.getSharedPreferences(NIGHT_MODE_MIRROR_FILE, Context.MODE_PRIVATE)
                    .let { mirror ->
                        mirror.getInt(KEY_NIGHT_MODE_MIRROR,
                            if (mirror.getBoolean(KEY_NEW_INSTALL_DARK, false)) AppCompatDelegate.MODE_NIGHT_YES
                            else AppCompatDelegate.MODE_NIGHT_UNSPECIFIED)
                    }
                if (
                    mode == AppCompatDelegate.MODE_NIGHT_YES ||
                    mode == AppCompatDelegate.MODE_NIGHT_NO ||
                    mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                ) {
                    AppCompatDelegate.setDefaultNightMode(mode)
                }
            }.onFailure { Timber.w(it, "Could not apply the mirrored night mode") }
        }
    }
}
