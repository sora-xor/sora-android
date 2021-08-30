/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.crashlytics.FirebaseCrashlytics
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.util.Event

open class BaseViewModel : ViewModel() {

    val errorLiveData = MutableLiveData<Event<String>>()
    val errorFromResourceLiveData = MutableLiveData<Event<Int>>()
    val alertDialogLiveData = MutableLiveData<Event<Pair<String, String>>>()

    fun onError(throwable: Throwable) {
        logException(throwable)
        if (throwable is SoraException) {
            when (throwable.kind) {
                SoraException.Kind.BUSINESS -> {
                    throwable.errorResponseCode?.stringResource?.let {
                        errorFromResourceLiveData.value = Event(it)
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
                    throwable.errorResponseCode?.stringResource?.let {
                        errorFromResourceLiveData.value = Event(it)
                    }
                }
            }
        }
    }

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

    fun logException(throwable: Throwable) {
        print(throwable.localizedMessage)
        FirebaseCrashlytics.getInstance().recordException(throwable)
        throwable.printStackTrace()
    }

    protected open fun onError(errorMessageResource: Int) {
        errorFromResourceLiveData.value = Event(errorMessageResource)
    }
}
