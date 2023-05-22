/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.profile.appsettings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_api.launcher.MainRouter

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val mainRouter: MainRouter,
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
    }

    fun onLanguageClick() {
        mainRouter.showSelectLanguage()
    }

    fun toggleSystemAppearance(checked: Boolean) {
        state = state.copy(systemAppearanceChecked = checked)
    }

    fun toggleDarkMode(checked: Boolean) {
        state = state.copy(darkModeChecked = checked)
    }
}
