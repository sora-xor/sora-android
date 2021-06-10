/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_api.domain.model.Gas
import java.math.BigDecimal
import java.math.BigInteger

interface EthereumInteractor {

    fun getAddress(): Single<String>

    fun startWithdraw(amount: BigDecimal, ethAddress: String, transactionFee: String): Completable

    fun transferValERC20(to: String, amount: BigDecimal): Completable

    fun updateFeeWithCurrentGasLimitAndPrice(gasLimit: BigInteger, gasPrice: BigInteger): Single<BigDecimal>

    fun getActualEthRegisterState(): Single<EthRegisterState.State>

    fun startCombinedValErcTransfer(partialAmount: BigDecimal, amount: BigDecimal, ethAddress: String, transactionFee: String): Completable

    fun startCombinedValTransfer(partialAmount: BigDecimal, amount: BigDecimal, peerId: String, peerFullName: String, transactionFee: BigDecimal, description: String): Completable

    /**
     * @return first - ethereum config is ok, second - bridge is ok
     */
    fun isBridgeEnabled(): Single<Pair<Boolean, Boolean>>

    fun getMinerFeeInitialDataForWithdraw(): Single<Gas>

    fun getMinerFeeInitialDataForTransfer(): Single<Gas>

    fun getMinerFeeInitialDataForTransferWithdraw(): Single<Gas>

    fun retryWithdrawTransaction(soranetHash: String): Completable
}
