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

package jp.co.soramitsu.feature_assets_api.presentation.selectsearchtoken

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.isMatchFilter
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common_wallet.presentation.compose.states.mapAssetsToCardState
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@HiltViewModel
class SelectSearchTokenViewModel @Inject constructor(
    private val interactor: AssetsInteractor,
    private val numbersFormatter: NumbersFormatter,
) : ViewModel() {

    private val _state = MutableStateFlow(SelectSearchAssetState(emptyList()))
    val state = _state.asStateFlow()

    private var searchTokenFilter = emptySearchTokenFilter
    private val assets = mutableListOf<Asset>()

    init {
        viewModelScope.launch {
            interactor.subscribeAssetsActiveOfCurAccount()
                .catch {
                    FirebaseWrapper.recordException(it)
                    emit(emptyList())
                }
                .collectLatest {
                    assets.clear()
                    assets.addAll(it)
                    reCalcFilter()
                }
        }
    }

    fun onFilterChange(value: SearchTokenFilter) {
        searchTokenFilter = value
        reCalcFilter()
    }

    private val tokenFilterInclude: (Asset) -> Boolean = {
        searchTokenFilter.tokenIds.contains(it.token.id)
    }

    private fun reCalcFilter() {
        val tokensFiltered =
            if (searchTokenFilter.tokenIds.isEmpty()) assets
            else assets.filter(tokenFilterInclude)
        val curFilterValue = searchTokenFilter.filter.lowercase()
        val list = if (curFilterValue.isBlank()) {
            mapAssetsToCardState(tokensFiltered, numbersFormatter)
        } else {
            buildList {
                addAll(
                    mapAssetsToCardState(
                        tokensFiltered.filter {
                            it.token.isMatchFilter(curFilterValue)
                        },
                        numbersFormatter
                    )
                )
            }
        }
        _state.value = _state.value.copy(list = list)
    }
}
