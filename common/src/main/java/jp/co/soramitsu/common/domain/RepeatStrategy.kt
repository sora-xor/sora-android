/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.common.domain

import kotlinx.coroutines.delay

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
        var shallRetry: Boolean
        do {
            shallRetry = false
            runCatching {
                block.invoke()
            }.onSuccess {
                return it
            }.onFailure { t ->
                if (predicate(t) && attempt < retries) {
                    shallRetry = true
                    attempt++
                    delay(500)
                } else {
                    throw t
                }
            }
        } while (shallRetry)
        throw RuntimeException("RetryStrategy")
    }
}
