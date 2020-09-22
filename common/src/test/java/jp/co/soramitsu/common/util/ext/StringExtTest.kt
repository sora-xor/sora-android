/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Test

class StringExtTest {

    @Test
    fun `parsing 4 digit otp code from normal string`() {
        val input = "Your sms code 1234"

        val code = "1234"

        assertEquals(input.parseOtpCode(), code)
    }

    @Test
    fun `parsing 4 digit otp code from string with longer number`() {
        val input = "Your sms code 12345"

        val code = "1234"

        assertEquals(input.parseOtpCode(), code)
    }

    @Test
    fun `parsing 4 digit otp code from string without number`() {
        val input = "Your sms code "

        assertEquals(input.parseOtpCode(), "")
    }
}