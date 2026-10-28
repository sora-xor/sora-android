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

fun <A, B> LiveData<A>.zipWith(stream: LiveData<B>): LiveData<Pair<A, B>> {
    val result = MediatorLiveData<Pair<A, B>>()
    result.addSource(this) { a ->
        if (a != null && stream.value != null) {
            result.value = Pair(a, stream.value!!)
        }
    }
    result.addSource(stream) { b ->
        if (b != null && this.value != null) {
            result.value = Pair(this.value!!, b)
        }
    }
    return result
}
