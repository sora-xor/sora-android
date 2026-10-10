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

package jp.co.soramitsu.feature_main_impl.presentation.profile.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.core_db.WalletUpgradeBackup
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
            tryCatch {
                state = state.copy(retainedWallets = findRetainedWallets())
            }
        }
    }

    /**
     * Key sets found at startup that no account uses (an unfinished account setup, an account
     * removed in an older version, or keys kept by "Set up this device again"). Only offered in
     * normal mode, where adding an account is allowed.
     */
    private suspend fun findRetainedWallets(): List<String> {
        if (WalletRecoveryCapabilityGate.isUserMutationBlocked()) return emptyList()
        return WalletUpgradeBackup.quarantine().retainedWalletIds
            .filter { !userRepository.accountExists(it) }
            .sorted()
    }

    /**
     * Restores one wallet found on this phone. The repository proves the stored keys derive this
     * address before it adds the account; no key material is written, moved or deleted.
     */
    fun restoreRetainedWallet(address: String) {
        if (state.restoring != null) return
        state = state.copy(restoring = address)
        viewModelScope.launch {
            val result = runCatching {
                userRepository.adoptRetainedWallet(
                    address = address,
                    accountName = resourceManager.getString(R.string.safe_mode_restored_wallet_name),
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
