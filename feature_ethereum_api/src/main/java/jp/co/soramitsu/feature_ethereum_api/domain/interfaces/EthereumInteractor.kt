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

    fun registerEthAccount(): Completable

    fun startWithdraw(amount: BigDecimal, ethAddress: String, transactionFee: String): Completable

    fun transferXorERC20(to: String, amount: BigDecimal): Completable

    fun getMinerFeeInitialData(): Single<Gas>

    fun updateFeeWithCurrentGasLimitAndPrice(gasLimit: BigInteger, gasPrice: BigInteger): Single<BigDecimal>

    fun getActualEthRegisterState(): Single<EthRegisterState.State>

    fun startCombinedXorErcTransfer(partialAmount: BigDecimal, amount: BigDecimal, ethAddress: String, transactionFee: String): Completable

    fun startCombinedXorTransfer(partialAmount: BigDecimal, amount: BigDecimal, peerId: String, peerFullName: String, transactionFee: BigDecimal, description: String): Completable
}