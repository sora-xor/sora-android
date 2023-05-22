/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

interface RepeatStrategy {
    suspend fun repeat(block: suspend () -> Unit)
}

object RepeatStrategyBuilder {
    fun infinite(): RepeatStrategy = InfiniteRepeatStrategy()
}

private class InfiniteRepeatStrategy : RepeatStrategy {
    override suspend fun repeat(block: suspend () -> Unit) {
        while (true) {
            block.invoke()
        }
    }
}
