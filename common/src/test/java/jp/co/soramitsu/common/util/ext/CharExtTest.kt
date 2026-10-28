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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharExtTest {

    @Test
    fun `validating valid chars`() {
        val input = arrayOf('a', 'ф', '1', '3', '\u0308')

        for (char in input) {
            assertTrue(char.isValidNameChar())
        }
    }

    @Test
    fun `validating invalid chars`() {
        val input = arrayOf('-', ',', '.', '+')

        for (char in input) {
            assertFalse(char.isValidNameChar())
        }
    }
}
