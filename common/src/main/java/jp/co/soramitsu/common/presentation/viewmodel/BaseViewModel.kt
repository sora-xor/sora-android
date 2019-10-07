/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.crashlytics.android.Crashlytics
import io.reactivex.disposables.CompositeDisposable
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.util.Event

open class BaseViewModel : ViewModel() {

    val errorLiveData = MutableLiveData<Event<String>>()
    val errorFromResourceLiveData = MutableLiveData<Event<Int>>()
    val alertDialogLiveData = MutableLiveData<Event<Pair<String, String>>>()

    protected val disposables = CompositeDisposable()

    fun onError(throwable: Throwable) {
        logException(throwable)
        if (throwable is SoraException) {
            when (throwable.kind) {
                SoraException.Kind.BUSINESS -> {
                    errorFromResourceLiveData.value = Event(throwable.errorResponseCode!!.stringResource)
                }
                SoraException.Kind.HTTP -> {
                    alertDialogLiveData.value = Event(Pair(throwable.errorTitle, throwable.message!!))
                }
                SoraException.Kind.NETWORK -> {
                    errorLiveData.value = Event(throwable.message!!)
                }
                SoraException.Kind.UNEXPECTED -> {
                    errorFromResourceLiveData.value = Event(throwable.errorResponseCode!!.stringResource)
                }
            }
        }
    }

    fun logException(throwable: Throwable) {
        Crashlytics.logException(throwable)
        throwable.printStackTrace()
    }

    protected open fun onError(errorMessageResource: Int) {
        errorFromResourceLiveData.value = Event(errorMessageResource)
    }

    override fun onCleared() {
        super.onCleared()
        if (!disposables.isDisposed) disposables.dispose()
    }
}