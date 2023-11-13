/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

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
