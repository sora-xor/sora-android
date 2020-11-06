/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
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

    fun logException(throwable: Throwable) {
        FirebaseCrashlytics.getInstance().recordException(throwable)
        throwable.printStackTrace()
    }

    protected open fun onError(errorMessageResource: Int) {
        errorFromResourceLiveData.value = Event(errorMessageResource)
    }

    override fun onCleared() {
        super.onCleared()
        if (!disposables.isDisposed) disposables.dispose()
    }

    protected fun <T> Observable<T>.subscribeWithDefaultError(onNext: (T) -> Unit): Disposable {
        return subscribe(onNext, ::onError)
    }
}