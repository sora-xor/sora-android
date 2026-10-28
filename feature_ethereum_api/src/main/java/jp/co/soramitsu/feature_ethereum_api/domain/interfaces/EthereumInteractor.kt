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

package jp.co.soramitsu.feature_ethereum_api.domain.interfaces

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_api.domain.model.Gas

interface EthereumInteractor {

    fun getAddress(): String

    fun startWithdraw(amount: BigDecimal, ethAddress: String, transactionFee: String)

    fun transferValERC20(to: String, amount: BigDecimal)

    fun updateFeeWithCurrentGasLimitAndPrice(gasLimit: BigInteger, gasPrice: BigInteger): BigDecimal

    fun getActualEthRegisterState(): EthRegisterState.State

    fun startCombinedValErcTransfer(partialAmount: BigDecimal, amount: BigDecimal, ethAddress: String, transactionFee: String)

    fun startCombinedValTransfer(partialAmount: BigDecimal, amount: BigDecimal, peerId: String, peerFullName: String, transactionFee: BigDecimal, description: String)

    /**
     * @return first - ethereum config is ok, second - bridge is ok
     */
    fun isBridgeEnabled(): Pair<Boolean, Boolean>

    fun getMinerFeeInitialDataForWithdraw(): Gas

    fun getMinerFeeInitialDataForTransfer(): Gas

    fun getMinerFeeInitialDataForTransferWithdraw(): Gas

    fun retryWithdrawTransaction(soranetHash: String)
}
