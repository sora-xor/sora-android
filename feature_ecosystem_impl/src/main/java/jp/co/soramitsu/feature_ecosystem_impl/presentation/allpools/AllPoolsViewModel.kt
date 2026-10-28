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

package jp.co.soramitsu.feature_ecosystem_impl.presentation.allpools

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_ecosystem_impl.domain.EcoSystemMapper
import jp.co.soramitsu.feature_ecosystem_impl.domain.EcoSystemPoolsInteractor
import jp.co.soramitsu.feature_ecosystem_impl.presentation.EcoSystemPoolsState
import jp.co.soramitsu.feature_ecosystem_impl.presentation.initialEcoSystemPoolsState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
internal class AllPoolsViewModel @Inject constructor(
    ecoSystemPoolsInteractor: EcoSystemPoolsInteractor,
    coroutineManager: CoroutineManager,
    private val ecoSystemMapper: EcoSystemMapper,
) : BaseViewModel() {

    val poolsState = ecoSystemPoolsInteractor.subscribeBasicPools()
        .catch {
            onError(it)
        }
        .map {
            val mapped = ecoSystemMapper.mapEcoSystemPools(it)
            EcoSystemPoolsState(
                pools = mapped,
            )
        }
        .flowOn(coroutineManager.io)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialEcoSystemPoolsState)
}
