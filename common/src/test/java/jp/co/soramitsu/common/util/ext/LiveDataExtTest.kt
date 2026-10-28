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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class LiveDataExtTest {

    @Rule @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Test
    fun `set value if new called`() {
        val liveData = MutableLiveData<String>()
        val value1 = "value1"
        val value2 = "value2"
        val results = mutableListOf<String>()
        val expected = listOf(value1, value2)
        liveData.observeForever {
            results.add(it)
        }

        liveData.setValueIfNew(value1)
        liveData.setValueIfNew(value1)
        liveData.setValueIfNew(value2)

        assertEquals(expected, results)
    }

    @Test
    fun `set value if empty called`() {
        val liveData = MutableLiveData<String>()
        val value1 = "value1"
        val value2 = "value2"

        liveData.observeForever {
            assertEquals(value1, it)
        }

        liveData.setValueIfEmpty(value1)
        liveData.setValueIfEmpty(value2)
    }
}
