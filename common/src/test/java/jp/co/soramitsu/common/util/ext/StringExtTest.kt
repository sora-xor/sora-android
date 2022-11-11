/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import org.junit.Assert.assertEquals
import org.junit.Test

class StringExtTest {

    @Test
    fun `truncate hash called with normal hash`() {
        val hash = "cnSp1MQTuw1JiDn5cg4RGtvE4ZKH17SjY2khAs1GUf7ZZ5hRQ"
        val result = hash.truncateHash()
        val expected = "cnSp1...Z5hRQ"

        assertEquals(expected, result)
    }

    @Test
    fun `truncate hash called with empty string`() {
        val hash = ""
        val result = hash.truncateHash()
        val expected = ""

        assertEquals(expected, result)
    }

    @Test
    fun `truncate hash called with less 10 symbol hash`() {
        val hash = "cnSp1"
        val result = hash.truncateHash()
        val expected = "cnSp1"

        assertEquals(expected, result)
    }

    @Test
    fun `truncate user address with normal address`() {
        val hash = "cnSp1MQTuw1JiDn5cg4RGtvE4ZKH17SjY2khAs1GUf7ZZ5hRQ"
        val result = hash.truncateUserAddress()
        val expected = "cnSp1...Z5hRQ"

        assertEquals(expected, result)
    }

    @Test
    fun `truncate user address with empty string`() {
        val hash = ""
        val result = hash.truncateUserAddress()
        val expected = ""

        assertEquals(expected, result)
    }

    @Test
    fun `truncate user address with less 20 address`() {
        val hash = "cnSp1"
        val result = hash.truncateUserAddress()
        val expected = "cnSp1"

        assertEquals(expected, result)
    }

    @Test
    fun `remove hex prefix with prefix`() {
        val hash = "0x7a250d5630b4cf539739df2c5dacb4c659f2488d"
        val result = hash.removeHexPrefix()
        val expected = "7a250d5630b4cf539739df2c5dacb4c659f2488d"

        assertEquals(expected, result)
    }

    @Test
    fun `remove hex prefix without prefix`() {
        val hash = "7a250d5630b4cf539739df2c5dacb4c659f2488d"
        val result = hash.removeHexPrefix()
        val expected = "7a250d5630b4cf539739df2c5dacb4c659f2488d"

        assertEquals(expected, result)
    }

    @Test
    fun `add hex prefix`() {
        val hash = "7a250d5630b4cf539739df2c5dacb4c659f2488d"
        val result = hash.addHexPrefix()
        val expected = "0x7a250d5630b4cf539739df2c5dacb4c659f2488d"

        assertEquals(expected, result)
    }

    @Test
    fun `get initials called with normal name`() {
        val name = "John Doe"
        val result = name.getInitials()
        val expected = "JD"

        assertEquals(expected, result)
    }

    @Test
    fun `get initials called with long name`() {
        val name = "John Very Good Guy Doe"
        val result = name.getInitials()
        val expected = "JD"

        assertEquals(expected, result)
    }

    @Test
    fun `get initials called with name from one word`() {
        val name = "John"
        val result = name.getInitials()
        val expected = ""

        assertEquals(expected, result)
    }

    @Test
    fun `is erc20 address with normal address`() {
        val address = "0x7a250d5630b4cf539739df2c5dacb4c659f2488d"
        val result = address.isErc20Address()
        val expected = true

        assertEquals(expected, result)
    }

    @Test
    fun `is erc20 address with spaces address`() {
        val address = "0x7a250d5630b4 cf539739df2c 5dacb4c659f2488d"
        val result = address.isErc20Address()
        val expected = false

        assertEquals(expected, result)
    }

    @Test
    fun `is erc20 address without prefix`() {
        val address = "7a250d5630b4cf539739df2c5dacb4c659f2488d"
        val result = address.isErc20Address()
        val expected = false

        assertEquals(expected, result)
    }

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