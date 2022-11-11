/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.model.ToolbarState
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.ui_core.component.toolbar.Action

open class BaseViewModel : ViewModel() {

    val errorLiveData = MutableLiveData<Event<String>>()
    val errorFromResourceLiveData = MutableLiveData<Event<Pair<Int, Int>>>()
    val alertDialogLiveData = SingleLiveEvent<Event<Pair<String, String>>>()

    protected val _toolbarState = MutableLiveData(ToolbarState())
    val toolbarState: LiveData<ToolbarState> = _toolbarState

    fun onError(throwable: Throwable) {
        FirebaseWrapper.recordException(throwable)

        if (throwable is SoraException) {
            when (throwable.kind) {
                SoraException.Kind.BUSINESS -> {
                    throwable.errorResponseCode?.messageResource?.let {
                        errorFromResourceLiveData.value = Event(Pair(throwable.errorResponseCode.titleResource, it))
                    }
                }
                SoraException.Kind.HTTP -> {
                    throwable.message?.let {
                        alertDialogLiveData.value = Event(Pair(throwable.errorTitle, it))
                    }
                }
                SoraException.Kind.NETWORK -> {
                    throwable.message?.let {
                        errorLiveData.value = Event(it)
                    }
                }
                SoraException.Kind.UNEXPECTED -> {
                    throwable.errorResponseCode?.messageResource?.let {
                        errorFromResourceLiveData.value = Event(Pair(throwable.errorResponseCode.titleResource, it))
                    }
                }
            }
        }
    }

    open fun onToolbarAction() = Unit

    open fun onToolbarMenuItemSelected(action: Action) = Unit

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
        errorFromResourceLiveData.value = Event(Pair(R.string.common_error_general_title, errorMessageResource))
    }
}
