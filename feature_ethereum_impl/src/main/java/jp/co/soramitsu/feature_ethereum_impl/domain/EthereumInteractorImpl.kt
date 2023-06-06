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

package jp.co.soramitsu.feature_ethereum_impl.domain

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_api.domain.model.Gas
import jp.co.soramitsu.feature_ethereum_impl.util.ContractsApiProvider

class EthereumInteractorImpl(
    private val ethereumRepository: EthereumRepository,
    private val credentialsRepository: CredentialsRepository,
) : EthereumInteractor {

    override fun transferValERC20(to: String, amount: BigDecimal) {
//        return credentialsRepository.retrieveMnemonic()
//            .flatMap { ethereumRepository.getEthCredentials(it) }
//            .flatMap { ethCreds -> ethereumRepository.getValTokenAddress(ethCreds).map { Pair(ethCreds, it) } }
//            .flatMapCompletable { ethereumRepository.transferValErc20(to, amount, it.first, it.second) }
    }

    private fun getMinerFeeInitialData(gasLimit: BigInteger): Gas {
        return Gas(BigInteger.ZERO, BigInteger.ZERO, emptyList())
//        return credentialsRepository.retrieveMnemonic()
//            .flatMap { ethereumRepository.getEthCredentials(it) }
//            .flatMap { ethereumRepository.getGasEstimations(gasLimit, it) }
    }

    override fun getMinerFeeInitialDataForTransfer(): Gas {
        return getMinerFeeInitialData(ContractsApiProvider.DEFAULT_GAS_LIMIT_TRANSFER.toBigInteger())
    }

    override fun getMinerFeeInitialDataForTransferWithdraw(): Gas {
        return getMinerFeeInitialData(ContractsApiProvider.DEFAULT_GAS_LIMIT_TRANSFER.toBigInteger() + ContractsApiProvider.DEFAULT_GAS_LIMIT_WITHDRAW.toBigInteger())
    }

    override fun retryWithdrawTransaction(soranetHash: String) {
//        return credentialsRepository.getAddress()
//            .flatMapCompletable { accountId ->
//                credentialsRepository.retrieveKeyPair()
//                    .flatMapCompletable { keyPair ->
//                        credentialsRepository.retrieveMnemonic()
//                            .flatMap {
//                                ethereumRepository.getEthCredentials(it)
//                            }
//                            .flatMapCompletable { ethereumCredentials ->
//                                ethereumRepository.getValTokenAddress(ethereumCredentials)
//                                    .flatMapCompletable { tokenAddress ->
//                                        Completable.complete()
//                                    }
//                            }
//                    }
//            }
    }

    override fun getMinerFeeInitialDataForWithdraw(): Gas {
        return getMinerFeeInitialData(ContractsApiProvider.DEFAULT_GAS_LIMIT_WITHDRAW.toBigInteger())
    }

    override fun getAddress(): String {
        return ""
//        return credentialsRepository.retrieveMnemonic()
//            .flatMap { ethereumRepository.getEthCredentials(it) }
//            .flatMap { ethereumRepository.getEthWalletAddress(it) }
    }

    override fun updateFeeWithCurrentGasLimitAndPrice(
        gasLimit: BigInteger,
        gasPrice: BigInteger
    ): BigDecimal {
        return BigDecimal.ZERO
//        return credentialsRepository.retrieveMnemonic()
//            .flatMap { ethereumRepository.setGasLimit(gasLimit) }
//            .flatMap { ethereumRepository.setGasPrice(gasPrice) }
    }

    override fun startWithdraw(amount: BigDecimal, ethAddress: String, transactionFee: String) {
//        return credentialsRepository.getAddress()
//            .flatMapCompletable { accountId ->
//                credentialsRepository.retrieveKeyPair()
//                    .flatMapCompletable { keyPair ->
//                        ethereumRepository.startWithdraw(amount, accountId, ethAddress, transactionFee, KeyPair(null, null))
//                    }
//            }
    }

    override fun getActualEthRegisterState(): EthRegisterState.State {
        return ethereumRepository.getActualEthRegisterState()
    }

    override fun startCombinedValErcTransfer(
        partialAmount: BigDecimal,
        amount: BigDecimal,
        ethAddress: String,
        transactionFee: String
    ) {
//        return credentialsRepository.retrieveMnemonic()
//            .flatMap { ethereumRepository.getEthCredentials(it) }
//            .flatMap { ethCredentials -> ethereumRepository.getEthWalletAddress(ethCredentials).map { Pair(it, ethCredentials) } }
//            .flatMapCompletable {
//                credentialsRepository.getAddress()
//                    .flatMapCompletable { accountId ->
//                        credentialsRepository.retrieveKeyPair()
//                            .flatMapCompletable { keypair ->
//                                ethereumRepository.startCombinedValErcTransfer(partialAmount, amount, accountId, it.first, ethAddress, transactionFee, it.second, KeyPair(null, null))
//                            }
//                    }
//            }
    }

    override fun startCombinedValTransfer(
        partialAmount: BigDecimal,
        amount: BigDecimal,
        peerId: String,
        peerFullName: String,
        transactionFee: BigDecimal,
        description: String
    ) {
//        return credentialsRepository.retrieveKeyPair()
//            .flatMapCompletable { keyPair ->
//                credentialsRepository.retrieveMnemonic()
//                    .flatMapCompletable {
//                        ethereumRepository.getEthCredentials(it)
//                            .flatMap { ethCreds -> ethereumRepository.getValTokenAddress(ethCreds).map { Pair(ethCreds, it) } }
//                            .flatMapCompletable { ethereumRepository.startCombinedValTransfer(partialAmount, amount, peerId, peerFullName, transactionFee, description, it.first, KeyPair(null, null), it.second) }
//                    }
//            }
    }

    override fun isBridgeEnabled(): Pair<Boolean, Boolean> =
        true to true
//        if (healthChecker.ethreumConfigState.value != true)
//            Single.just(Pair(first = false, second = true))
//        else credentialsRepository.retrieveMnemonic()
//            .flatMap {
//                ethereumRepository.getEthCredentials(it)
//                    .flatMap { ethCreds ->
//                        ethereumRepository.isBridgeEnabled(ethCreds).map { br ->
//                            Pair(first = true, second = br)
//                        }
//                    }
//            }
}
