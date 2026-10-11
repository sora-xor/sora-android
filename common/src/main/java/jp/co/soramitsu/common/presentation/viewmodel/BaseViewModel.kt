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

package jp.co.soramitsu.common.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.navigation.NavOptionsBuilder
import jp.co.soramitsu.androidfoundation.fragment.SingleLiveEvent
import jp.co.soramitsu.androidfoundation.fragment.trigger
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.ConfirmDialogData
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.NetworkFeeUnavailable
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.presentation.compose.SnackBarState
import jp.co.soramitsu.common.presentation.transaction.TransactionOutcomeTexts
import jp.co.soramitsu.ui_core.component.toolbar.Action
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import kotlinx.coroutines.CancellationException

open class BaseViewModel : ViewModel() {

    val errorLiveData = SingleLiveEvent<String>()
    val errorFromResourceLiveData = SingleLiveEvent<Pair<Int, Int>>()
    val alertDialogLiveData = SingleLiveEvent<Pair<String, String>>()
    val confirmDialogLiveData = SingleLiveEvent<ConfirmDialogData>()

    /** Asks the screen to open this app's page in the store, where the user can update it. */
    val openAppStoreLiveData = SingleLiveEvent<Unit>()
    val snackBarLiveData = SingleLiveEvent<SnackBarState>()
    val copiedToast = SingleLiveEvent<Unit>()

    protected val _toolbarState = MutableLiveData<SoramitsuToolbarState>()
    val toolbarState: LiveData<SoramitsuToolbarState> = _toolbarState

    protected val _navigationPop = SingleLiveEvent<Unit>()
    val navigationPop: LiveData<Unit> = _navigationPop

    protected val _navEvent = SingleLiveEvent<Pair<String, NavOptionsBuilder.() -> Unit>>()
    val navEvent: LiveData<Pair<String, NavOptionsBuilder.() -> Unit>> = _navEvent

    protected val _navToStart = SingleLiveEvent<String>()
    val navToStart: LiveData<String> = _navToStart

    protected var currentDestination: String = ""
        get() = field.ifEmpty { startScreen() }

    fun setCurDestination(d: String) {
        currentDestination = d
        onCurrentDestinationChanged(d)
    }

    /**
     * True between onResume and onPause of this view model's screen, that is, while it is the
     * screen in front. It is false while the app is in the background and while another screen
     * is on top of it; after a pause that other screen is the PIN lock.
     */
    protected var screenInFront = false
        private set

    /** A result that arrived while the screen was not in front. It is shown when it is. */
    private var heldUntilInFront: (() -> Boolean)? = null

    /**
     * True while a result is waiting to be shown. The screen keeps its action off meanwhile, so
     * what was just sent cannot be sent a second time from a screen that has not said so yet.
     */
    protected val resultHeld: Boolean
        get() = heldUntilInFront != null

    /** The screen came to the front. A result that was held for it is shown now. */
    open fun onScreenResumed() {
        screenInFront = true
        showHeldResult()
    }

    /** The screen left the front: the app went to the background or another screen is on top. */
    open fun onScreenPaused() {
        screenInFront = false
    }

    /**
     * Shows a result that needs this screen in front, typically by opening another screen. A
     * transaction is followed until it is final, which can take two minutes, so its result can
     * arrive while the app is in the background or while the PIN lock is on top. Navigating at
     * that moment is either dropped (the result would never be shown) or removes the screen on
     * top (the PIN lock would be gone without the PIN). The result is therefore held and [show]
     * runs only while this screen is in front: at once when it is, otherwise on its next resume.
     *
     * [show] returns true when the result was shown. When it returns false nothing happened and
     * it is asked again on the next resume.
     */
    protected fun showWhenInFront(show: () -> Boolean) {
        heldUntilInFront = show
        showHeldResult()
    }

    private fun showHeldResult() {
        if (!screenInFront) return
        val show = heldUntilInFront ?: return
        if (show()) heldUntilInFront = null
    }

    fun onError(throwable: Throwable) {
        FirebaseWrapper.recordException(throwable)

        if (throwable is SoraException) {
            when (throwable.kind) {
                SoraException.Kind.BUSINESS -> {
                    throwable.errorResponseCode?.messageResource?.let {
                        errorFromResourceLiveData.setValue(throwable.errorResponseCode.titleResource to it)
                    }
                }
                SoraException.Kind.HTTP -> {
                    throwable.message?.let {
                        alertDialogLiveData.setValue(throwable.errorTitle to it)
                    }
                }
                SoraException.Kind.NETWORK -> {
                    throwable.message?.let {
                        errorLiveData.setValue(it)
                    }
                }
                SoraException.Kind.UNEXPECTED -> {
                    throwable.errorResponseCode?.messageResource?.let {
                        errorFromResourceLiveData.setValue(
                            R.string.something_went_wrong to R.string.unexpected_error
                        )
                    }
                }
            }
        }
    }

    open fun startScreen(): String = theOnlyRoute

    open fun onCurrentDestinationChanged(curDest: String) = Unit

    open fun onBackPressed() {
        _navigationPop.trigger()
    }

    open fun onNavIcon() {
        _navigationPop.trigger()
    }

    open fun onMenuItem(action: Action) = Unit

    open fun onToolbarSearch(value: String) = Unit

    open fun onAction() = Unit

    suspend fun tryCatchFinally(finally: () -> Unit, block: suspend () -> Unit) {
        try {
            block.invoke()
        } catch (t: Throwable) {
            onError(t)
        } finally {
            finally.invoke()
        }
    }

    suspend fun tryCatch(block: suspend () -> Unit) {
        try {
            block.invoke()
        } catch (t: Throwable) {
            onError(t)
        }
    }

    /**
     * Runs [block] inside a collector or a launched coroutine and reports a failure through
     * [onError] instead of letting it leave viewModelScope, where an uncaught exception ends the
     * app. Unlike [tryCatch] it passes cancellation on, so it is safe inside collectLatest.
     */
    protected suspend fun guarded(block: suspend () -> Unit) {
        try {
            block.invoke()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            onError(t)
        }
    }

    protected open fun onError(errorMessageResource: Int) {
        errorFromResourceLiveData.value =
            R.string.common_error_general_title to errorMessageResource
    }

    /**
     * A transaction could not be prepared, so nothing reached the network. Unlike [onError] this
     * always tells the user, whatever the error type: a confirm button must never end in silence.
     */
    protected fun showTransactionNotSent(resourceManager: ResourceManager, error: Throwable) {
        if (error is SoraException && error.hasOwnMessage()) {
            // A business error names its own reason (for example an unavailable fee rate).
            onError(error)
            return
        }
        FirebaseWrapper.recordException(error)
        val text = TransactionOutcomeTexts.notSent(resourceManager, error)
        alertDialogLiveData.setValue(text.title to text.message)
    }

    /**
     * True only when [onError] shows a specific message for this error. Every other error,
     * including a [SoraException] that [onError] would pass over in silence or answer with a
     * generic "unexpected error", gets the "Not sent" explanation instead.
     */
    private fun SoraException.hasOwnMessage(): Boolean =
        kind == SoraException.Kind.BUSINESS &&
            errorResponseCode?.messageResource?.let { it != 0 } == true

    /** The node refused the transaction before broadcast: a failure, with its reason. */
    protected fun showTransactionRefused(
        resourceManager: ResourceManager,
        outcome: TransactionOutcome.RefusedByNode,
    ) {
        val text = TransactionOutcomeTexts.refused(resourceManager, outcome)
        alertDialogLiveData.setValue(text.title to text.message)
    }

    /**
     * A screen that is built from network fees could not get them. The dialog says why and
     * offers the action that can help. While the network cannot be reached that is Try again.
     * When the network was upgraded and asks for a newer app, asking again gives the same
     * answer, so the action is Update (the store page of this app) and the screen is left.
     * Close always leaves the screen.
     */
    protected fun showFeesUnavailable(
        resourceManager: ResourceManager,
        error: Throwable,
        onClose: () -> Unit,
        onRetry: () -> Unit,
    ) {
        val reason = NetworkFeeUnavailable.from(error)
        confirmDialogLiveData.setValue(
            ConfirmDialogData(
                title = resourceManager.getString(R.string.network_fee_unavailable_short),
                message = resourceManager.getString(
                    when (reason) {
                        NetworkFeeUnavailable.NETWORK_UNREACHABLE ->
                            R.string.tx_outcome_screen_needs_network
                        NetworkFeeUnavailable.APP_UPDATE_REQUIRED ->
                            R.string.network_fee_needs_app_update
                        NetworkFeeUnavailable.OTHER -> R.string.network_fee_unavailable
                    }
                ),
                confirmText = resourceManager.getString(
                    if (reason.retryCanHelp) R.string.common_try_again else R.string.common_update
                ),
                dismissText = resourceManager.getString(R.string.common_close),
                onDismiss = onClose,
                onConfirm = if (reason.retryCanHelp) {
                    onRetry
                } else {
                    {
                        openAppStoreLiveData.trigger()
                        onClose()
                    }
                },
            )
        )
    }

    /**
     * The same action for this account is still waiting for confirmation. Sending it again needs
     * a deliberate second confirmation; dismissing the question sends nothing.
     */
    protected fun askBeforeSendingAgain(resourceManager: ResourceManager, sendAgain: () -> Unit) {
        confirmDialogLiveData.setValue(
            ConfirmDialogData(
                title = resourceManager.getString(R.string.tx_outcome_duplicate_title),
                message = resourceManager.getString(R.string.tx_outcome_duplicate_message),
                confirmText = resourceManager.getString(R.string.tx_outcome_duplicate_send_again),
                dismissText = resourceManager.getString(R.string.tx_outcome_duplicate_wait),
                onConfirm = sendAgain,
            )
        )
    }
}
