/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import org.junit.Test

class IntExtTest {

    @Test
    fun `calc trailing zeros of Int`() {
        val value = 1445
        assert(value.trailingZeros() == 0)
    }

    @Test
    fun `calc trailing zeros of Int 2`() {
        val value = 852
        assert(value.trailingZeros() == 2)
    }
}