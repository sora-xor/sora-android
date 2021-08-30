/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.network.substrate

import kotlinx.coroutines.flow.Flow

interface ConnectionManager {

    fun start(url: String)

    fun isStarted(): Boolean

    fun stop()

    fun connectionState(): Flow<Boolean>
}
