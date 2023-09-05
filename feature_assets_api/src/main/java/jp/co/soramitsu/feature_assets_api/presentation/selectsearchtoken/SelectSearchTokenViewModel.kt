/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_assets_api.presentation.selectsearchtoken

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.isMatchFilter
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

    private var filter: String = ""
    private val assets = mutableListOf<Asset>()

    init {
        viewModelScope.launch {
            interactor.subscribeAssetsActiveOfCurAccount()
                .catch {
                    // todo add
                }
                .collectLatest {
                    assets.clear()
                    assets.addAll(it)
                    reCalcFilter()
                }
        }
    }

    fun onFilterChange(value: String) {
        filter = value
        reCalcFilter()
    }

    private fun reCalcFilter() {
        val curFilter = filter.lowercase()
        val list = if (curFilter.isBlank()) {
            mapAssetsToCardState(assets, numbersFormatter)
        } else {
            buildList {
                addAll(
                    mapAssetsToCardState(
                        assets.filter {
                            it.token.isMatchFilter(curFilter)
                        },
                        numbersFormatter
                    )
                )
            }
        }
        _state.value = _state.value.copy(list = list)
    }
}
