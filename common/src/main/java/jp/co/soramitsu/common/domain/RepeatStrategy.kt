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

package jp.co.soramitsu.common.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

interface RepeatStrategy {
    suspend fun repeat(block: suspend () -> Unit)
}

interface RetryStrategy {
    @Throws(RuntimeException::class)
    suspend fun <T> retryIf(
        retries: Int,
        predicate: suspend (cause: Throwable) -> Boolean,
        block: suspend () -> T,
    ): T
}

object RepeatStrategyBuilder {
    fun infinite(): RepeatStrategy = InfiniteRepeatStrategy()
}

object RetryStrategyBuilder {
    fun build(): RetryStrategy = RetryStrategyImpl()
}

private class InfiniteRepeatStrategy : RepeatStrategy {
    override suspend fun repeat(block: suspend () -> Unit) {
        while (true) {
            currentCoroutineContext().ensureActive()
            block.invoke()
        }
    }
}

private class RetryStrategyImpl : RetryStrategy {

    override suspend fun <T> retryIf(
        retries: Int,
        predicate: suspend (cause: Throwable) -> Boolean,
        block: suspend () -> T,
    ): T {
        var attempt = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                return block.invoke()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                currentCoroutineContext().ensureActive()
                if (attempt >= retries || !predicate(error)) throw error
                attempt++
                delay(500)
            }
        }
    }
}
