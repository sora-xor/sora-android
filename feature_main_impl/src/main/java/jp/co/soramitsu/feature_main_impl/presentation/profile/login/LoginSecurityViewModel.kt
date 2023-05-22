/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.profile.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import kotlinx.coroutines.launch

@HiltViewModel
class LoginSecurityViewModel @Inject constructor(
    private val mainRouter: MainRouter,
    private val mainInteractor: MainInteractor
) : BaseViewModel() {

    internal var state by mutableStateOf(LoginSecurityState(false, false))
        private set

    init {
        _toolbarState.value = initSmallTitle2(
            title = R.string.settings_login_title,
        )
        viewModelScope.launch {
            val available = mainInteractor.isBiometryAvailable()
            val enabled = mainInteractor.isBiometryEnabled()
            state = LoginSecurityState(
                bioAvailable = available,
                bioEnabled = enabled && available,
            )
        }
    }

    fun changePin() {
        mainRouter.showPin(PinCodeAction.CHANGE_PIN_CODE)
    }

    fun toggleBio(checked: Boolean) {
        state = state.copy(bioEnabled = checked)
        viewModelScope.launch {
            tryCatch {
                mainInteractor.setBiometryEnabled(checked)
            }
        }
    }
}
