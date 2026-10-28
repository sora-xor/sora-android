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

package jp.co.soramitsu.feature_main_impl.presentation.profile.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.fragment.SingleLiveEvent
import jp.co.soramitsu.androidfoundation.fragment.trigger
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import kotlinx.coroutines.launch

@HiltViewModel
class LoginSecurityViewModel @Inject constructor(
    private val mainRouter: MainRouter,
    private val mainInteractor: MainInteractor,
    private val userRepository: UserRepository,
    private val resourceManager: ResourceManager,
) : BaseViewModel() {

    internal var state by mutableStateOf(LoginSecurityState(false, false))
        private set

    private val _importAccountEvent = SingleLiveEvent<Unit>()

    /** Opens Import account, where a wallet's own recovery phrase adopts its stored keys. */
    val importAccountEvent: LiveData<Unit> = _importAccountEvent

    init {
        _toolbarState.value = initSmallTitle2(
            title = R.string.settings_login_title,
        )
        viewModelScope.launch {
            val available = mainInteractor.isBiometryAvailable()
            val enabled = mainInteractor.isBiometryEnabled()
            state = state.copy(
                bioAvailable = available,
                bioEnabled = enabled && available,
            )
        }
        viewModelScope.launch {
            // Key sets found at startup that no account uses (an unfinished account setup, an
            // account removed in an older version, or keys kept by "Set up this device again").
            // Only listed in normal mode, where adding an account is allowed. When the list
            // cannot be read right now the section is not shown; nothing depends on it.
            try {
                val retained = userRepository.retainedWallets()
                state = state.copy(
                    retainedWallets = retained.map { it.address },
                    needRecoveryPhrase = retained.filter { it.needsRecoveryPhrase }.map { it.address },
                )
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Throwable) {
                onBackgroundError(error)
            }
        }
    }

    fun importRecoveryPhrase() {
        _importAccountEvent.trigger()
    }

    /**
     * Restores one wallet found on this phone. The repository proves the stored keys derive this
     * address before it adds the account; no key material is written, moved or deleted.
     */
    fun restoreRetainedWallet(address: String) {
        if (state.restoring != null || address in state.needRecoveryPhrase) return
        state = state.copy(restoring = address)
        viewModelScope.launch {
            val result = runCatching {
                userRepository.adoptRetainedWallet(
                    address = address,
                    accountName = resourceManager.getString(R.string.safe_mode_restored_wallet_name),
                    // Restoring here adds the account; the wallet the person is using stays
                    // selected ("It is now in your accounts").
                    makeCurrent = false,
                )
            }
            state = if (result.isSuccess) {
                state.copy(restoring = null, restored = state.restored + address)
            } else {
                result.exceptionOrNull()?.let { FirebaseWrapper.recordException(it) }
                state.copy(restoring = null, notRestorable = state.notRestorable + address)
            }
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
