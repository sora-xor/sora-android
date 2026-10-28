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

package jp.co.soramitsu.feature_main_impl.domain.subs

import javax.inject.Inject
import jp.co.soramitsu.common.domain.SingleFeatureStorageManager
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
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
