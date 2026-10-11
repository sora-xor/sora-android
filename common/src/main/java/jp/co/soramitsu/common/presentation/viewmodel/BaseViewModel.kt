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
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.presentation.compose.SnackBarState
import jp.co.soramitsu.ui_core.component.toolbar.Action
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import kotlinx.coroutines.CancellationException

open class BaseViewModel : ViewModel() {

    val errorLiveData = SingleLiveEvent<String>()
    val errorFromResourceLiveData = SingleLiveEvent<Pair<Int, Int>>()
    val alertDialogLiveData = SingleLiveEvent<Pair<String, String>>()
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
     * Reports a failure of something the person started or is looking at. Every failure ends in
     * a message: one that carries its own text shows it, and everything else (an exception that
     * is not a [SoraException], or an unexpected one) gets a calm text that says what is known
     * ([FailureNotice]). A cancelled coroutine is not a failure and shows nothing.
     *
     * A screen that shows progress while an action runs puts its controls back in
     * [onErrorResetProgress], so a failure never leaves it without its buttons.
     *
     * May be called from any thread.
     */
    fun onError(throwable: Throwable) {
        report(throwable, explainEverything = true)
    }

    /**
     * Reports a failure of background work nobody is waiting for: a subscription that feeds the
     * screen, or a periodic refresh. It is recorded, and the screen's own state (for example the
     * connection banner) shows it. Only a failure that carries its own message is shown, as
     * before; nothing else interrupts the person.
     */
    protected fun onBackgroundError(throwable: Throwable) {
        report(throwable, explainEverything = false)
    }

    private fun report(throwable: Throwable, explainEverything: Boolean) {
        if (throwable is CancellationException) return
        FirebaseWrapper.recordException(throwable)
        if (explainEverything) onErrorResetProgress()

        if (throwable !is SoraException) {
            if (explainEverything) showFailureNotice(FailureNotice.of(throwable))
            return
        }
        when (throwable.kind) {
            SoraException.Kind.BUSINESS -> {
                val code = throwable.errorResponseCode
                if (code != null && code.messageResource != 0) {
                    errorFromResourceLiveData.emit(code.titleResource to code.messageResource)
                } else if (explainEverything) {
                    showFailureNotice(FailureNotice.of(throwable))
                }
            }
            SoraException.Kind.HTTP -> {
                val message = throwable.message
                if (!message.isNullOrEmpty() || throwable.errorTitle.isNotEmpty()) {
                    alertDialogLiveData.emit(throwable.errorTitle to message.orEmpty())
                } else if (explainEverything) {
                    showFailureNotice(FailureNotice.of(throwable))
                }
            }
            SoraException.Kind.NETWORK -> {
                val message = throwable.message
                if (!message.isNullOrEmpty()) {
                    errorLiveData.emit(message)
                } else if (explainEverything) {
                    showFailureNotice(FailureNotice.NETWORK)
                }
            }
            // An unexpected failure is explained by what caused it, never by a bare
            // "Something went wrong".
            SoraException.Kind.UNEXPECTED -> showFailureNotice(FailureNotice.of(throwable))
        }
    }

    private fun showFailureNotice(notice: FailureNotice) {
        errorFromResourceLiveData.emit(notice.titleResource to notice.messageResource)
    }

    /**
     * Sets the event from the main thread and posts it from any other. Some screens report
     * failures from a background dispatcher (`flowOn`, `launch(io)`); setting a LiveData value
     * there throws, which would turn the report itself into a crash.
     */
    private fun <T> SingleLiveEvent<T>.emit(event: T) {
        try {
            value = event
        } catch (_: IllegalStateException) {
            postValue(event)
        }
    }

    /**
     * Called for every failure reported through [onError], before its message is shown. A screen
     * that hides or disables its controls while an action runs restores them here.
     */
    protected open fun onErrorResetProgress() = Unit

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

    protected open fun onError(errorMessageResource: Int) {
        errorFromResourceLiveData.value =
            R.string.common_error_general_title to errorMessageResource
    }
}
