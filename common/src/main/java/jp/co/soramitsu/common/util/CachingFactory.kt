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

package jp.co.soramitsu.common.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CachingFactory<Args : CachingFactory.Args, Value>(
    private val factory: suspend Args.() -> Value
) {

    abstract class Args

    private val cacheMutex = Mutex()
    private var cachedValue = mutableMapOf<Args, Value>()

    suspend fun nullableValue(args: Args): Value? {
        initialize(args)
        return cachedValue[args]
    }

    suspend fun value(args: Args): Value {
        initialize(args)
        return checkNotNull(cachedValue[args])
    }

    private suspend fun initialize(args: Args) {
        if (cachedValue[args] == null) {
            cacheMutex.withLock {
                if (cachedValue[args] == null) {
                    cachedValue[args] = factory(args)
                }
            }
        }
    }
}
