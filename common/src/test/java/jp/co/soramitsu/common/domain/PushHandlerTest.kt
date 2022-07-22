/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class PushHandlerTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var pushHandler: PushHandler

    @Before
    fun setup() {
        pushHandler = PushHandler()
    }

    @Test
    fun `invitations handler test`() = runTest {
        var pushesCount = 0

        launch {
            pushHandler.observeNewPushes().take(3).collect {
                pushesCount++
            }
        }
        assertEquals(0, pushesCount)
        pushHandler.pushReceived()
        advanceUntilIdle()
        assertEquals(1, pushesCount)
        pushHandler.pushReceived()
        advanceUntilIdle()
        pushHandler.pushReceived()
        advanceUntilIdle()
        assertEquals(3, pushesCount)
    }
}