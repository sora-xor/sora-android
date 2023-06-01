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

package jp.co.soramitsu.feature_ethereum_impl.util

import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthereumCredentials
import jp.co.soramitsu.feature_ethereum_impl.data.mappers.EthereumCredentialsMapper
import jp.co.soramitsu.feature_ethereum_impl.data.network.ERC20ContractApi
import jp.co.soramitsu.feature_ethereum_impl.data.network.SmartContractApi
import org.web3j.tx.FastRawTransactionManager
import org.web3j.tx.response.NoOpProcessor

@Singleton
class ContractsApiProvider @Inject constructor(
    private val web3jProvider: Web3jProvider,
    private val ethereumCredentialsMapper: EthereumCredentialsMapper,
) {

    companion object {
        const val DEFAULT_GAS_LIMIT_TRANSFER = 200000
        const val DEFAULT_GAS_LIMIT_WITHDRAW = 350000
    }

    private var smartContractApi: SmartContractApi? = null
    private var erc20ContractApi: ERC20ContractApi? = null
    private var fastRawTransactionManager: FastRawTransactionManager? = null
    private val gasProvider = GasProvider(BigInteger.ZERO, BigInteger.ZERO)

    fun getSmartContractApi(ethCredentials: EthereumCredentials): SmartContractApi {
        if (smartContractApi == null) {
            smartContractApi = SmartContractApi(web3jProvider.web3j, getOrCreateFastRawTransactionManager(ethCredentials), gasProvider)
        }
        return smartContractApi!!
    }

    fun getErc20ContractApi(ethCredentials: EthereumCredentials, tokenAddress: String): ERC20ContractApi {
        if (erc20ContractApi == null) {
            erc20ContractApi = ERC20ContractApi(web3jProvider.web3j, getOrCreateFastRawTransactionManager(ethCredentials), gasProvider, tokenAddress)
        }
        return erc20ContractApi!!
    }

    fun fetchGasPrice(): BigInteger {
        val gasPrice = web3jProvider.web3j.ethGasPrice().send().gasPrice
        gasProvider.price = gasPrice
        return gasPrice
    }

    fun getGasLimit(): BigInteger {
        return gasProvider.estimatedGas
    }

    fun getGasPrice(): BigInteger {
        return gasProvider.price
    }

    fun setGasLimit(gasLimit: BigInteger) {
        gasProvider.estimatedGas = gasLimit
    }

    fun setGasPrice(gasPrice: BigInteger) {
        gasProvider.price = gasPrice
    }

    private fun getOrCreateFastRawTransactionManager(ethCredentials: EthereumCredentials): FastRawTransactionManager {
        if (fastRawTransactionManager == null) {
            val credentials = ethereumCredentialsMapper.getCredentials(ethCredentials.privateKey)
            fastRawTransactionManager = FastRawTransactionManager(web3jProvider.web3j, credentials, NoOpProcessor(web3jProvider.web3j))
        }

        return fastRawTransactionManager!!
    }
}
