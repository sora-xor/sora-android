/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
