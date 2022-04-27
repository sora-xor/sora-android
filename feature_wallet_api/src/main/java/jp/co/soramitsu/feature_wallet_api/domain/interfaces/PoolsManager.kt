/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import kotlinx.coroutines.flow.Flow

interface PoolsManager {

    fun bind()

    fun unbind()

    fun isLoading(): Flow<Boolean>
}
