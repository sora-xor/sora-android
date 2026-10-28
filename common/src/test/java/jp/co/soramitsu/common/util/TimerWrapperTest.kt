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

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class TimerWrapperTest {

    private lateinit var timer: TimerWrapper

    @Before fun setUp() {
        timer = TimerWrapper()
    }

    @Test fun `calc time left show correct time`() {
        assertEquals("00:00:00", timer.calcTimeLeft(0))
        assertEquals("00:01:00", timer.calcTimeLeft(60000))
        assertEquals("06:06:40", timer.calcTimeLeft(22000000))
        assertEquals("12:13:20", timer.calcTimeLeft(44000000))
        assertEquals("23:59:59", timer.calcTimeLeft(86399000))
    }
}
