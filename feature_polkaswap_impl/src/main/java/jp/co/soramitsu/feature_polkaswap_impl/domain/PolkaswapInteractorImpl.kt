/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.domain

import jp.co.soramitsu.common.domain.PoolDex
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapRepository

abstract class PolkaswapInteractorImpl(
    private val polkaswapRepository: PolkaswapRepository,
) : PolkaswapInteractor {
    private var baseDex: List<PoolDex>? = null

    override suspend fun getPoolDexList(): List<PoolDex> {
        return baseDex ?: (
            polkaswapRepository.getPoolBaseTokens().also {
                baseDex = it
            }
            )
    }
}
