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

import jp.co.soramitsu.common.domain.OptionsProvider.nbspace
import jp.co.soramitsu.common.util.NumbersFormatter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FiatTest {

    private val nf: NumbersFormatter = NumbersFormatter()

    @Before
    fun setup() {
    }

    @After
    fun teardown() {
    }

    @Test
    fun test001() {
        val f = formatFiatChange(12.2394, nf)
        assertEquals("+1${nbspace}223.94$nbspace%", f)
    }

    @Test
    fun test002() {
        val f = formatFiatChange(0.0004, nf)
        assertEquals("+0.04$nbspace%", f)
    }

    @Test
    fun test003() {
        val f = formatFiatChange(0.0001, nf)
        assertEquals("+0.01$nbspace%", f)
    }

    @Test
    fun test004() {
        val f = formatFiatChange(0.0000999999, nf)
        assertEquals("0$nbspace%", f)
    }
}
