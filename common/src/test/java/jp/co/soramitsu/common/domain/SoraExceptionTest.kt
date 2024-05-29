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

import java.io.IOException
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `http 400 error`() {
        val title = "Title"
        val body = "Body"

        val resourcesManager = mock(ResourceManager::class.java)
        given(resourcesManager.getString(R.string.common_error_invalid_parameters)).willReturn("Title")
        given(resourcesManager.getString(R.string.common_error_invalid_parameters_body)).willReturn("Body")

        val error = SoraException.httpError(400, resourcesManager)

        assertNull(error.errorResponseCode)
        assertEquals(error.errorTitle, title)
        assertEquals(error.message, body)
        assertEquals(error.kind, SoraException.Kind.HTTP)
    }

    @Test
    fun `http 401 error`() {
        val title = "Title"
        val body = "Body"

        val resourcesManager = mock(ResourceManager::class.java)
        given(resourcesManager.getString(R.string.common_error_unauthorized_title)).willReturn("Title")
        given(resourcesManager.getString(R.string.common_error_unauthorized_body)).willReturn("Body")

        val error = SoraException.httpError(401, resourcesManager)

        assertNull(error.errorResponseCode)
        assertEquals(error.errorTitle, title)
        assertEquals(error.message, body)
        assertEquals(error.kind, SoraException.Kind.HTTP)
    }

    @Test
    fun `http 404 error`() {
        val title = "Title"
        val body = "Body"

        val resourcesManager = mock(ResourceManager::class.java)
        given(resourcesManager.getString(R.string.common_error_not_found_title)).willReturn("Title")
        given(resourcesManager.getString(R.string.common_error_general_message)).willReturn("Body")

        val error = SoraException.httpError(404, resourcesManager)

        assertNull(error.errorResponseCode)
        assertEquals(error.errorTitle, title)
        assertEquals(error.message, body)
        assertEquals(error.kind, SoraException.Kind.HTTP)
    }

    @Test
    fun `http 500 error`() {
        val title = "Title"
        val body = ""

        val resourcesManager = mock(ResourceManager::class.java)
        given(resourcesManager.getString(R.string.common_error_internal_error_title)).willReturn("Title")

        val error = SoraException.httpError(500, resourcesManager)

        assertNull(error.errorResponseCode)
        assertEquals(error.errorTitle, title)
        assertEquals(error.message, body)
        assertEquals(error.kind, SoraException.Kind.HTTP)
    }

    @Test
    fun `http another error`() {
        val title = "Title"
        val body = "Body"

        val resourcesManager = mock(ResourceManager::class.java)
        given(resourcesManager.getString(R.string.common_error_general_title)).willReturn("Title")
        given(resourcesManager.getString(R.string.common_error_general_message)).willReturn("Body")

        val error = SoraException.httpError(501, resourcesManager)

        assertNull(error.errorResponseCode)
        assertEquals(error.errorTitle, title)
        assertEquals(error.message, body)
        assertEquals(error.kind, SoraException.Kind.HTTP)
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
