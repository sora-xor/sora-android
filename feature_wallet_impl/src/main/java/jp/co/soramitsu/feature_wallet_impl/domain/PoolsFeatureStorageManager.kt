/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.domain

import javax.inject.Inject
import jp.co.soramitsu.common.domain.SingleFeatureStorageManager
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach

class PoolsFeatureStorageManager @Inject constructor(
    private val poolsInteractor: PoolsInteractor
) : SingleFeatureStorageManager {

    override fun subscribe(address: String): Flow<String> {
        return poolsInteractor.subscribePoolsChangesOfAccount(address)
            .debounce(700)
            .onEach {
                poolsInteractor.updatePools()
            }
    }
}
