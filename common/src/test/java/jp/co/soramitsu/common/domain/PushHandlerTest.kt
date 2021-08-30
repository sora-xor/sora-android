/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class PushHandlerTest {

    private lateinit var pushHandler: PushHandler

    private val dispatcher = TestCoroutineDispatcher()

    @Before
    fun setup() {
        pushHandler = PushHandler()
    }

    @Test
    fun `invitations handler test`() = runBlocking {
        var pushesCount = 0

        launch(dispatcher) {
            pushHandler.observeNewPushes().take(3).collect {
                pushesCount++
            }
        }
        assertEquals(0, pushesCount)

        pushHandler.pushReceived()
        assertEquals(1, pushesCount)

        pushHandler.pushReceived()
        pushHandler.pushReceived()
        assertEquals(3, pushesCount)
    }
}