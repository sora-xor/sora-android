/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import io.reactivex.Completable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.internal.functions.Functions

inline fun Completable.subscribeToError(crossinline consumer: (Throwable) -> Unit) = subscribe(Functions.EMPTY_ACTION, Consumer {
    consumer.invoke(it)
})

operator fun CompositeDisposable.plusAssign(disposable: Disposable) {
    add(disposable)
}