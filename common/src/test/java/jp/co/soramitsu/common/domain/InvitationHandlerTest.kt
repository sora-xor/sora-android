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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class InvitationHandlerTest {

    private lateinit var invitationHandler: InvitationHandler

    @Before
    fun setup() {
        invitationHandler = InvitationHandler()
    }

    @Test
    fun `invitations handler test`() = runTest {
        val actual = mutableListOf<String>()
        launch {
            invitationHandler.observeInvitationApplies().take(3).collect { actual.add(it) }
        }
        invitationHandler.invitationApplied()
        advanceUntilIdle()
        invitationHandler.invitationApplied()
        advanceUntilIdle()
        invitationHandler.invitationApplied()
        advanceUntilIdle()
        assertEquals(listOf("", "", ""), actual)
    }
}
