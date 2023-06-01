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

package jp.co.soramitsu.feature_ethereum_api.domain.interfaces

import java.math.BigDecimal
import java.math.BigInteger
import java.security.KeyPair
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthereumCredentials
import jp.co.soramitsu.feature_ethereum_api.domain.model.Gas

interface EthereumRepository {

    fun getValTokenAddress(ethCredentials: EthereumCredentials): String

    fun getSerializedProof(ethCredentials: EthereumCredentials): String

    suspend fun getEthCredentials(mnemonic: String): EthereumCredentials

    fun startWithdraw(
        amount: BigDecimal,
        srcAccountId: String,
        ethAddress: String,
        transactionFee: String,
        keyPair: KeyPair
    )

    fun startCombinedValErcTransfer(
        partialAmount: BigDecimal,
        amount: BigDecimal,
        srcAccountId: String,
        withdrawEthAddress: String,
        transferEthAddress: String,
        transactionFee: String,
        ethCredentials: EthereumCredentials,
        keyPair: KeyPair
    )

    fun startCombinedValTransfer(
        partialAmount: BigDecimal,
        amount: BigDecimal,
        transferAccountId: String,
        transferName: String,
        transactionFee: BigDecimal,
        description: String,
        ethCredentials: EthereumCredentials,
        keyPair: KeyPair,
        tokenAddress: String
    )

    fun getEthWalletAddress(ethCredentials: EthereumCredentials): String

    fun calculateValErc20TransferFee(): BigDecimal

    fun calculateValErc20WithdrawFee(): BigDecimal

    fun setGasPrice(gasPriceInGwei: BigInteger): BigDecimal

    fun setGasLimit(gasLimit: BigInteger): BigDecimal

    fun transferValErc20(
        to: String,
        amount: BigDecimal,
        ethCredentials: EthereumCredentials,
        tokenAddress: String
    )

    suspend fun getEthRegistrationState(): EthRegisterState

    suspend fun registrationStarted(operationId: String)

    suspend fun registrationCompleted(operationId: String)

    suspend fun registrationFailed(operationId: String)

    fun getGasEstimations(gasLimit: BigInteger, ethCredentials: EthereumCredentials): Gas

    fun getBlockChainExplorerUrl(transactionHash: String): String

    fun updateValErc20AndEthBalance(
        ethCredentials: EthereumCredentials,
        ethWalletAddress: String,
        tokenAddress: String
    )

    fun confirmWithdraw(
        ethCredentials: EthereumCredentials,
        amount: BigDecimal,
        txHash: String,
        accountId: String,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        tokenAddress: String
    ): String

    fun getActualEthRegisterState(): EthRegisterState.State

    fun isBridgeEnabled(ethCredentials: EthereumCredentials): Boolean

    fun calculateValErc20CombinedFee(): BigDecimal
}
