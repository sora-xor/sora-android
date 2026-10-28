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

package jp.co.soramitsu.sora.splash.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.fragment.SingleLiveEvent
import jp.co.soramitsu.androidfoundation.fragment.trigger
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.core_db.WalletRecoveryOptions
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.sora.splash.domain.PendingRecoveryStartupScheduler
import jp.co.soramitsu.sora.splash.domain.SplashInteractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WalletMigrationRecoveryUiState(
    val code: String,
    val canContinueLegacyWallet: Boolean,
    /** Forward paths for this state; Safe Mode renders them. Never empty of exits. */
    val options: WalletRecoveryOptions? = null,
)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val interactor: SplashInteractor,
    private val coroutineManager: CoroutineManager,
    private val pendingRecoveryStartupScheduler: PendingRecoveryStartupScheduler,
) : BaseViewModel() {

    private val _runtimeInitiated = MutableLiveData<Boolean>()
    val runtimeInitiated: LiveData<Boolean> = _runtimeInitiated

    val loadingTextVisiblity = SingleLiveEvent<Unit>()

    val showMainScreen = SingleLiveEvent<Unit>()
    val showOnBoardingScreen = SingleLiveEvent<OnboardingState>()
    val showMainScreenFromInviteLink = SingleLiveEvent<Unit>()
    val showMigrationRecovery = SingleLiveEvent<WalletMigrationRecoveryUiState>()

    init {
        viewModelScope.launch {
            tryCatch {
                delay(500)
                _runtimeInitiated.value = true
            }
        }
        viewModelScope.launch {
            tryCatch {
                delay(5000)
                loadingTextVisiblity.trigger()
            }
        }
    }

    private var verificationStarted = false

    /**
     * Starts the wallet verification of a normal app start (once per view model, so a recreated
     * Activity does not start a second one). Not called when the screen opened for "Try upgrade
     * again" or read-only access: those run their own action and must not race a second
     * verification that could change the recovery mode the person just confirmed.
     */
    fun start() {
        if (verificationStarted) return
        verificationStarted = true
        interactor.startMigration()
    }

    fun nextScreen() {
        viewModelScope.launch {
            try {
                val migrationDone = interactor.getMigrationDoneAsync().await()
                FirebaseWrapper.log(FirebaseWrapper.Breadcrumb.SPLASH_NAVIGATION)
                if (!migrationDone) {
                    showMigrationRecovery.value = recoveryState(
                        code = failureCode("WALLET_VERIFICATION_FAILED"),
                    )
                    return@launch
                }
                routeAfterVerifiedWallet()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Startup never crashes into a loop: anything unexpected shows the recovery
                // options (Try again, support) instead.
                showRecoveryAfterUnexpectedError("WALLET_VERIFICATION_FAILED")
            }
        }
    }

    /**
     * The wallet verified. Registration finished without any account row (for example the
     * database was lost or set aside while the keys were kept, or every account was removed)
     * opens onboarding, which offers restore, instead of an empty main screen.
     */
    private suspend fun routeAfterVerifiedWallet() {
        pendingRecoveryStartupScheduler.scheduleAfterWalletMigration()
        when (val state = interactor.getRegistrationState()) {
            OnboardingState.REGISTRATION_FINISHED -> {
                if (interactor.hasExistingWallet()) {
                    showMainScreen.trigger()
                } else {
                    showOnBoardingScreen.value = OnboardingState.INITIAL
                }
            }
            OnboardingState.INITIAL -> {
                if (interactor.hasExistingWallet()) {
                    showMainScreen.trigger()
                } else {
                    showOnBoardingScreen.value = state
                }
            }
        }
    }

    fun continueLegacyWallet() {
        viewModelScope.launch {
            try {
                if (interactor.canContinueLegacyWallet()) {
                    showMainScreen.trigger()
                } else {
                    showMigrationRecovery.value = recoveryState(
                        code = failureCode("LEGACY_WALLET_SELECTION_UNAVAILABLE"),
                        canContinueLegacyWallet = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                showRecoveryAfterUnexpectedError("LEGACY_WALLET_SELECTION_UNAVAILABLE")
            }
        }
    }

    fun retryWalletMigration() {
        viewModelScope.launch {
            try {
                val migrationDone = withContext(coroutineManager.io) {
                    interactor.retryMigration()
                }
                if (migrationDone) {
                    // The same routing as a normal start: a retry that verifies a phone with no
                    // account (for example after a busy Keystore with only retained keys) opens
                    // onboarding, never an empty main screen.
                    routeAfterVerifiedWallet()
                } else {
                    showMigrationRecovery.value = recoveryState(
                        code = failureCode("WALLET_VERIFICATION_FAILED"),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                showRecoveryAfterUnexpectedError("WALLET_VERIFICATION_FAILED")
            }
        }
    }

    private suspend fun failureCode(default: String): String =
        try {
            interactor.getMigrationFailureCode()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        } ?: interactor.currentRecoveryCode() ?: default

    private suspend fun showRecoveryAfterUnexpectedError(default: String) {
        val code = interactor.currentRecoveryCode() ?: default
        showMigrationRecovery.value = try {
            recoveryState(code)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            WalletMigrationRecoveryUiState(code = code, canContinueLegacyWallet = false)
        }
    }

    private suspend fun recoveryState(
        code: String,
        canContinueLegacyWallet: Boolean? = null,
    ): WalletMigrationRecoveryUiState {
        val options = interactor.recoveryOptions(code)
        return WalletMigrationRecoveryUiState(
            code = code,
            canContinueLegacyWallet = canContinueLegacyWallet
                ?: (options.canReadOnly && interactor.canContinueLegacyWallet()),
            options = options,
        )
    }
}
