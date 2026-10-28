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
import jp.co.soramitsu.common.domain.subscriptionKeys
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge

class GlobalSubscriptionManager @Inject constructor(
    private val featureStorageManagers:
    @JvmSuppressWildcards Map<String, SingleFeatureStorageManager>,
    private val userRepository: UserRepository,
) {

    fun start(): Flow<String> {
        return userRepository.flowCurSoraAccount()
            .flatMapLatest { account ->
                val subscriptionList = subscriptionKeys.mapNotNull {
                    featureStorageManagers[it]?.subscribe(account.substrateAddress)
                }
                subscriptionList.merge()
            }
    }
}
