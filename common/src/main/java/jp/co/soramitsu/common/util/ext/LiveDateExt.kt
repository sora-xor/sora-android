/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData

fun <T> MutableLiveData<T>.setValueIfNew(newValue: T) {
    if (this.value != newValue) value = newValue
}

fun <T> MutableLiveData<T>.setValueIfEmpty(newValue: T) {
    if (this.value == null) value = newValue
}

fun <T, V> LiveData<T>.map(mapper: (T) -> V): LiveData<V> {
    return MediatorLiveData<V>().also { mediator ->
        mediator.addSource(this) {
            mediator.value = mapper.invoke(it)
        }
    }
}