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

package jp.co.soramitsu.test_shared

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout

/**
 * Collect the receiving [Flow] in the given [TestScope], then run assertions on the flow's emissions in the given
 * [assertionBlock] by using the available functions in [TestFlowCollector]. Usually, you can simply pass the scope
 * given in the [runTest] function.
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> Flow<T>.test(
    scope: TestScope,
    assertionBlock: suspend TestFlowCollector<T>.() -> Unit = {},
) {
    with(TestFlowCollector(this, scope)) {
        assertionBlock()
        finishAssertion()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TestFlowCollector<T>(private val flow: Flow<T>, scope: TestScope) {

    private val values = mutableListOf<T>()

    private val job = scope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
        flow.collect { values.add(it) }
    }

    suspend fun finishAssertion() {
        job.cancelAndJoin()
    }

    suspend fun awaitValue(at: Int): T {
        return try {
            withTimeout(1.seconds) {
                while (values.getOrNull(at) == null) {
                    delay(50.milliseconds)
                }
                values[at]
            }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError("No value was emitted within 1 second at index $at")
        }
    }

    suspend fun ensureNoValue(at: Int) {
        try {
            val value = awaitValue(at)
            throw UnexpectedValueException(value, at)
        } catch (e: AssertionError) {
            if (e is UnexpectedValueException) {
                throw e
            }
            // all good, expected the exception to be thrown
        }
    }

    private class UnexpectedValueException(value: Any?, at: Int) :
        AssertionError("Expected no value to be emitted at index $at but received: $value")
}
