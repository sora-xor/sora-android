/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Test

class CharExtTest {

    @Test
    fun `validating valid chars`() {
        val input = arrayOf('a', 'ф','\u093E', '\u0488', '\u0308', '\'', '-', ' ')

        for (char in input) {
            assertTrue(char.isValidNameChar())
        }
    }

    @Test
    fun `validating invalid chars`() {
        val input = arrayOf('1', '\u005D')

        for (char in input) {
            assertFalse(char.isValidNameChar())
        }
    }

}