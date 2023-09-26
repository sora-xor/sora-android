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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val mainRouter: MainRouter,
    private val darkThemeManager: DarkThemeManager
) : BaseViewModel() {

    internal var state by mutableStateOf(
        AppSettingsState(
            systemAppearanceChecked = false,
            darkModeChecked = false,
        )
    )
        private set

    init {
        _toolbarState.value = initSmallTitle2(
            title = R.string.settings_header_app,
        )

        darkThemeManager.darkModeStatusFlow.onEach { isSwitchEnabled ->
            state = state.copy(darkModeChecked = isSwitchEnabled)
        }.launchIn(viewModelScope)
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
                darkModeChecked = false // check DarkThemeManager.setDarkThemeEnabled
            )
        }
    }

    fun toggleDarkMode(checked: Boolean) {
        viewModelScope.launch {
            tryCatch { darkThemeManager.setDarkThemeEnabled(checked) }
        }.invokeOnCompletion {
            state = state.copy(
                systemAppearanceChecked = false, // check DarkThemeManager.setDarkThemeEnabled
                darkModeChecked = checked
            )
        }
    }
}
