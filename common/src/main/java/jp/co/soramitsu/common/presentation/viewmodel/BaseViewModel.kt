/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.navigation.NavOptionsBuilder
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.compose.SnackBarState
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.ui_core.component.toolbar.Action
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState

open class BaseViewModel : ViewModel() {

    val errorLiveData = SingleLiveEvent<String>()
    val errorFromResourceLiveData = SingleLiveEvent<Pair<Int, Int>>()
    val alertDialogLiveData = SingleLiveEvent<Pair<String, String>>()
    val snackBarLiveData = SingleLiveEvent<SnackBarState>()

    protected val _toolbarState = MutableLiveData<SoramitsuToolbarState>()
    val toolbarState: LiveData<SoramitsuToolbarState> = _toolbarState

    protected val _navigationPop = SingleLiveEvent<Unit>()
    val navigationPop: LiveData<Unit> = _navigationPop

    protected val _navEvent = SingleLiveEvent<Pair<String, NavOptionsBuilder.() -> Unit>>()
    val navEvent: LiveData<Pair<String, NavOptionsBuilder.() -> Unit>> = _navEvent

    protected var currentDestination: String = ""
        get() = field.ifEmpty { startScreen() }

    fun setCurDestination(d: String) {
        currentDestination = d
        onCurrentDestinationChanged(d)
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
                            throwable.errorResponseCode.titleResource to it
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
