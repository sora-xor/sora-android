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
import jp.co.soramitsu.common.logger.WalletDiagnostics
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.WalletStorageFailures
import jp.co.soramitsu.core_db.WalletRecoveryOptions
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.sora.splash.domain.PendingRecoveryStartupScheduler
import jp.co.soramitsu.sora.splash.domain.SplashInteractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
    val showMigrationRecovery = SingleLiveEvent<WalletMigrationRecoveryUiState>()

    /** The usual moment the start is decided ([runtimeInitiated]); before it the early route shows only a route. */
    private val usualMoment = CompletableDeferred<Unit>()

    init {
        viewModelScope.launch {
            tryCatch {
                delay(500)
                _runtimeInitiated.value = true
            }
            usualMoment.complete(Unit)
        }
        viewModelScope.launch {
            tryCatch {
                delay(5000)
                loadingTextVisiblity.trigger()
            }
        }
    }

    private var verificationStarted = false

    /** The early route of a start that plays no animation ([routeEarly]); at most one per view model. */
    private var earlyRoute: Job? = null

    /** [nextScreen] has begun: from here on it alone decides the start. */
    private var nextScreenStarted = false

    /** The early route opened the wallet (or onboarding): the start is decided and is not routed again. */
    private var routedEarly = false

    /**
     * The early route has decided this start: a recreated startup screen neither routes again nor starts the
     * progress screen's timer.
     */
    val isStartDecided: Boolean get() = routedEarly

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

    /**
     * Android 12 and later (and Remove animations) play no animation on the startup screen, so the wallet need not
     * wait for one: when the verification has passed and the routing reads answer at their first try, the wallet (or
     * onboarding) opens at once. Everything else is left to [nextScreen] at its usual moment, with its usual retries,
     * progress screen and recovery options: a verification that fails or has not finished, a busy disk or Keystore,
     * an unexpected error, or [mayOpen] saying no (a recovery state the screen knows of). Before the usual moment
     * nothing is shown from here but a successful route, and the verification is still awaited before it.
     */
    fun routeEarly(mayOpen: () -> Boolean) {
        if (earlyRoute != null || nextScreenStarted) return
        earlyRoute = viewModelScope.launch {
            val route = try {
                if (!interactor.getMigrationDoneAsync().await()) return@launch
                // The reads run to their end even when nextScreen gives way to them: a read may finish a pending
                // wallet deletion step, which is never cut short here.
                withContext(NonCancellable) { decideRoute(readOnce = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Left to nextScreen, which reads again and shows what it always showed.
                return@launch
            }
            ensureActive()
            if (nextScreenStarted || !mayOpen()) return@launch
            // Nothing suspends from here to the route, so nextScreen cannot start in between.
            routedEarly = true
            try {
                WalletDiagnostics.log(WalletDiagnostics.Breadcrumb.SPLASH_NAVIGATION)
                pendingRecoveryStartupScheduler.scheduleAfterWalletMigration()
                show(route)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                // As in nextScreen: startup never crashes into a loop. The recovery options appear no sooner than
                // nextScreen could have shown them.
                usualMoment.await()
                showRecoveryAfterUnexpectedError("WALLET_VERIFICATION_FAILED", error)
            }
        }
    }

    fun nextScreen() {
        // The early route already opened the wallet: the start is not decided twice.
        if (routedEarly) return
        nextScreenStarted = true
        // An early route still waiting for the verification or a read gives way: from here the start is exactly
        // the usual one.
        earlyRoute?.cancel()
        viewModelScope.launch {
            try {
                val migrationDone = interactor.getMigrationDoneAsync().await()
                WalletDiagnostics.log(WalletDiagnostics.Breadcrumb.SPLASH_NAVIGATION)
                if (!migrationDone) {
                    showMigrationRecovery.value = recoveryState(
                        code = failureCode("WALLET_VERIFICATION_FAILED"),
                    )
                    return@launch
                }
                routeAfterVerifiedWallet()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                // Startup never crashes into a loop: anything unexpected shows the recovery
                // options (Try again, support) instead.
                showRecoveryAfterUnexpectedError("WALLET_VERIFICATION_FAILED", error)
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
        show(decideRoute(readOnce = false))
    }

    /** Where a verified wallet's start goes. */
    private sealed interface Route {
        data object Main : Route
        data class Onboarding(val state: OnboardingState) : Route
    }

    /**
     * Reads the registration state, then whether an account exists. [readOnce] reads each once and lets a failure
     * through (the early route); otherwise each read is retried as [readForRouting] does.
     */
    private suspend fun decideRoute(readOnce: Boolean): Route {
        val state = read(readOnce) { interactor.getRegistrationState() }
        val hasAccount = read(readOnce) { interactor.hasExistingWallet() }
        return when {
            hasAccount -> Route.Main
            state == OnboardingState.REGISTRATION_FINISHED -> Route.Onboarding(OnboardingState.INITIAL)
            else -> Route.Onboarding(state)
        }
    }

    private suspend fun <T> read(once: Boolean, read: suspend () -> T): T = if (once) read() else readForRouting(read)

    private fun show(route: Route) {
        when (route) {
            Route.Main -> showMainScreen.trigger()
            is Route.Onboarding -> showOnBoardingScreen.value = route.state
        }
    }

    /**
     * One routing read after the wallet verified. A busy disk, database or Keystore is read
     * again after a short pause; if it is still busy, the recovery screen gets a transient code
     * and keeps retrying by itself (an update never stops for what passes on its own).
     */
    private suspend fun <T> readForRouting(read: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return read()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val code = transientRoutingCode(error) ?: throw error
                if (attempt >= ROUTING_RETRY_DELAYS.size) throw TransientRoutingFailure(code, error)
                delay(ROUTING_RETRY_DELAYS[attempt++])
            }
        }
    }

    private fun transientRoutingCode(error: Throwable): String? = when {
        !WalletStorageFailures.isTransient(error) -> null
        WalletStorageFailures.isKeyMaterialUnavailable(error) -> WalletRecoveryOptions.KEYSTORE_TRANSIENT
        else -> WalletRecoveryOptions.STORAGE_IO
    }

    private class TransientRoutingFailure(val code: String, cause: Throwable) :
        IllegalStateException(code, cause)

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
            } catch (error: Throwable) {
                showRecoveryAfterUnexpectedError("WALLET_VERIFICATION_FAILED", error)
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

    private suspend fun showRecoveryAfterUnexpectedError(default: String, error: Throwable? = null) {
        val code = (error as? TransientRoutingFailure)?.code
            ?: interactor.currentRecoveryCode()
            ?: default
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

    private companion object {
        /** Pauses before reading the routing facts again (about 4 s in all). */
        val ROUTING_RETRY_DELAYS = longArrayOf(250L, 1_000L, 3_000L)
    }
}
