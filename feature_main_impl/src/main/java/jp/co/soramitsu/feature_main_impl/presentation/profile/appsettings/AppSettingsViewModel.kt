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

package jp.co.soramitsu.feature_main_impl.presentation.profile.appsettings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DarkThemeManager
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_blockexplorer_api.data.ProductionFeatureManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val mainRouter: MainRouter,
    private val darkThemeManager: DarkThemeManager,
    private val productionFeatureManager: ProductionFeatureManager,
) : BaseViewModel() {

    internal var state by mutableStateOf(
        AppSettingsState(
            systemAppearanceChecked = false,
            darkModeChecked = false,
            testNetworksAvailable = false,
            testNetworksChecked = false,
        )
    )
        private set

    init {
        _toolbarState.value = initSmallTitle2(
            title = R.string.settings_header_app,
        )

        viewModelScope.launch {
            darkThemeManager.mutableDarkThemeSharedFlow.collectLatest {
                state =
                    state.copy(
                        darkModeChecked = it.isDarkModeEnabled,
                        systemAppearanceChecked = it.isSystemDrivenUiEnabled
                    )
            }
        }
        viewModelScope.launch {
            darkThemeManager.reduceEffectsFlow.collectLatest {
                state = state.copy(reduceEffectsChecked = it)
            }
        }
        viewModelScope.launch {
            tryCatch {
                // Refreshes the PI flags, then shows the switch as the wallet applies it: from
                // the same snapshot the Nexus portfolio uses. A rejected indexer read (features
                // off for that read only) then never shows the switch off while Taira is shown.
                productionFeatureManager.getState()
                val shown = productionFeatureManager.observeState().first()
                state = state.copy(
                    testNetworksAvailable = shown.tairaAvailable,
                    testNetworksChecked = shown.tairaVisible,
                )
            }
        }
    }

    fun onLanguageClick() {
        mainRouter.showSelectLanguage()
    }

    fun toggleSystemAppearance(checked: Boolean) {
        viewModelScope.launch {
            tryCatch { darkThemeManager.setSystemDrivenUiEnabled(checked) }
        }.invokeOnCompletion {
            state = state.copy(
                systemAppearanceChecked = checked,
                darkModeChecked = false,
            )
        }
    }

    fun toggleDarkMode(checked: Boolean) {
        viewModelScope.launch {
            tryCatch { darkThemeManager.setDarkThemeEnabled(checked) }
        }.invokeOnCompletion {
            state = state.copy(
                systemAppearanceChecked = false,
                darkModeChecked = checked,
            )
        }
    }

    fun toggleReduceEffects(checked: Boolean) {
        viewModelScope.launch {
            tryCatch { darkThemeManager.setReduceEffects(checked) }
        }
    }

    fun toggleTestNetworks(checked: Boolean) {
        if (!state.testNetworksAvailable) return
        viewModelScope.launch {
            tryCatch {
                productionFeatureManager.setTairaVisible(checked)
                state = state.copy(testNetworksChecked = checked)
            }
        }
    }
}
