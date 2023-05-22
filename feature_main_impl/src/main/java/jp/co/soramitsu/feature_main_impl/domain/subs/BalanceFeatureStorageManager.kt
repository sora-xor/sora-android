/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain.subs

import javax.inject.Inject
import jp.co.soramitsu.common.domain.SingleFeatureStorageManager
import jp.co.soramitsu.feature_assets_api.data.interfaces.AssetsRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

class BalanceFeatureStorageManager @Inject constructor(
    private val walletRepository: WalletRepository,
    private val assetsRepository: AssetsRepository,
) : SingleFeatureStorageManager {

    override fun subscribe(address: String): Flow<String> {
        return walletRepository.observeStorageAccount(address)
            .onEach {
                assetsRepository.updateBalancesVisibleAssets(address)
            }
    }
}
