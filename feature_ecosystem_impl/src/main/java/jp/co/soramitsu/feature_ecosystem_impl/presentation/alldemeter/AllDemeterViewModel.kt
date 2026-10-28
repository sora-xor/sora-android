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

package jp.co.soramitsu.feature_ecosystem_impl.presentation.alldemeter

import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.format.formatFiatSuffix
import jp.co.soramitsu.androidfoundation.fragment.SingleLiveEvent
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.StringTriple
import jp.co.soramitsu.common_wallet.presentation.compose.BasicFarmListItemState
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor
import kotlinx.coroutines.launch

@HiltViewModel
internal class AllDemeterViewModel @Inject constructor(
    demeterFarmingInteractor: DemeterFarmingInteractor,
    numbersFormatter: NumbersFormatter,
) : BaseViewModel() {

    private val _farmsLiveData = SingleLiveEvent(AllFarmsState(emptyList(), true))
    val farmsLiveData: LiveData<AllFarmsState> = _farmsLiveData

    init {
        viewModelScope.launch {
            _farmsLiveData.value = AllFarmsState(
                demeterFarmingInteractor.getFarmedBasicPools().mapIndexed { index, farm ->
                    BasicFarmListItemState(
                        StringTriple(farm.tokenBase.id, farm.tokenTarget.id, farm.tokenReward.id),
                        number = (index + 1).toString(),
                        token1Icon = farm.tokenBase.iconUri(),
                        token2Icon = farm.tokenTarget.iconUri(),
                        rewardTokenIcon = farm.tokenReward.iconUri(),
                        rewardTokenSymbol = farm.tokenReward.symbol,
                        text1 = "%s-%s".format(farm.tokenBase.symbol, farm.tokenTarget.symbol),
                        text2 = farm.tokenBase.printFiat(farm.tvl.formatFiatSuffix()).orEmpty(),
                        text3 = farm.apr.let {
                            "%s%%".format(numbersFormatter.format(it, 2))
                        },
                    )
                }
            )
        }
    }
}
