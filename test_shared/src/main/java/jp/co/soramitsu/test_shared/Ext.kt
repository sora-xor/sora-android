/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.test_shared

import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito

fun <T> anyNonNull(): T {
    Mockito.any<T>()
    return initialized()
}

fun <T : Any> eqNonNull(value: T): T = eq(value) ?: value

private fun <T> initialized(): T = null as T