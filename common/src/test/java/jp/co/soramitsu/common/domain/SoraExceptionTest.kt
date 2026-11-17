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

import java.io.IOException
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock

class SoraExceptionTest {

    @Test
    fun `buisness error`() {
        val responseCode = ResponseCode.WRONG_PIN_CODE
        val error = SoraException.businessError(responseCode)

        assertEquals(error.errorResponseCode, responseCode)
        assertEquals(error.errorTitle, "")
        assertEquals(error.message, responseCode.toString())
        assertEquals(error.kind, SoraException.Kind.BUSINESS)
    }

    @Test
    fun `network error`() {
        val exception = IOException()
        val resourceManager = mock(ResourceManager::class.java)

        given(resourceManager.getString(R.string.common_error_network))
            .willReturn("Error")

        val error = SoraException.networkError(resourceManager, exception)

        assertEquals(error.cause, exception)
        assertEquals(error.errorTitle, "")
        assertEquals(error.kind, SoraException.Kind.NETWORK)
    }

    @Test
    fun `unexpected error`() {
        val message = "Some error"
        val throwable = Throwable(message)
        val error = SoraException.unexpectedError(throwable)

        assertEquals(error.errorResponseCode, ResponseCode.GENERAL_ERROR)
        assertEquals(error.cause, throwable)
        assertEquals(error.errorTitle, "")
        assertEquals(error.message, message)
        assertEquals(error.kind, SoraException.Kind.UNEXPECTED)
    }

    @Test
    fun `unexpected error empty title`() {
        val throwable = Throwable()
        val error = SoraException.unexpectedError(throwable)

        assertEquals(error.errorResponseCode, ResponseCode.GENERAL_ERROR)
        assertEquals(error.cause, throwable)
        assertEquals(error.errorTitle, "")
        assertEquals(error.message, "")
        assertEquals(error.kind, SoraException.Kind.UNEXPECTED)
    }
}
