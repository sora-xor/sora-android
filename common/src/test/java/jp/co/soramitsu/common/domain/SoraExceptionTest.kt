/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.resourses.ResourceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import java.io.IOException

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