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
