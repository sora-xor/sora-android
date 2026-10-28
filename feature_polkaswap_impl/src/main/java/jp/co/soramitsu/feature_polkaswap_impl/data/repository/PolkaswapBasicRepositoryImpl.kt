/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_polkaswap_impl.data.repository

import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.UserPoolJoinedLocalNullable
import jp.co.soramitsu.feature_blockexplorer_api.data.BlockExplorerManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

abstract class PolkaswapBasicRepositoryImpl(
    private val db: AppDatabase,
    private val blockExplorerManager: BlockExplorerManager,
) {
    protected fun getPoolStrategicBonusAPY(
        reserveAccountOfPool: String,
    ): Double? =
        blockExplorerManager.getTempApy(reserveAccountOfPool)

    protected suspend fun getPoolBaseTokenDexId(tokenId: String): Int {
        return db.poolDao().getPoolBaseToken(tokenId)?.dexId ?: 0
    }

    protected fun getUserPool(
        baseTokenId: String,
        targetTokenId: String,
        address: String,
    ): Flow<UserPoolJoinedLocalNullable?> {
        return db.poolDao().subscribePool(targetTokenId, baseTokenId).map { list ->
            if (list.isEmpty()) {
                null
            } else {
                list.find {
                    it.userPoolLocal?.accountAddress == address
                } ?: UserPoolJoinedLocalNullable(
                    basicPoolLocal = list[0].basicPoolLocal,
                    userPoolLocal = null,
                )
            }
        }
    }
}
