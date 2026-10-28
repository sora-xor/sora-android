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

package jp.co.soramitsu.feature_ecosystem_impl.presentation.allcurrencies

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_ecosystem_impl.domain.EcoSystemMapper
import jp.co.soramitsu.feature_ecosystem_impl.domain.EcoSystemTokensInteractor
import jp.co.soramitsu.feature_ecosystem_impl.presentation.EcoSystemTokensState
import jp.co.soramitsu.feature_ecosystem_impl.presentation.initialEcoSystemTokensState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
internal class AllCurrenciesViewModel @Inject constructor(
    ecoSystemTokensInteractor: EcoSystemTokensInteractor,
    private val ecoSystemMapper: EcoSystemMapper,
    coroutineManager: CoroutineManager,
) : BaseViewModel() {

    val state = ecoSystemTokensInteractor.subscribeTokens()
        .catch {
            onError(it)
        }
        .map {
            val mapped = ecoSystemMapper.mapEcoSystemTokens(it)
            EcoSystemTokensState(mapped)
        }
        .flowOn(coroutineManager.io)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialEcoSystemTokensState)
}
