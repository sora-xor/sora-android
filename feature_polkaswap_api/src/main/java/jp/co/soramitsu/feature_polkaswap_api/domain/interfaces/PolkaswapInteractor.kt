/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_api.domain.interfaces

import jp.co.soramitsu.common.domain.PoolDex

interface PolkaswapInteractor {
    suspend fun getPoolDexList(): List<PoolDex>
}
