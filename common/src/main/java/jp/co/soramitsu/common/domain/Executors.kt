/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import java.util.concurrent.Executors

private val io = Executors.newSingleThreadExecutor()

fun ioThread(block: () -> Unit) {
    io.execute(block)
}
