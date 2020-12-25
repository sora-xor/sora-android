/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl.util

import jp.co.soramitsu.feature_ethereum_api.domain.model.EthereumCredentials
import jp.co.soramitsu.feature_ethereum_impl.data.mappers.EthereumCredentialsMapper
import jp.co.soramitsu.feature_ethereum_impl.data.network.ERC20ContractApi
import jp.co.soramitsu.feature_ethereum_impl.data.network.SmartContractApi
import org.web3j.tx.FastRawTransactionManager
import org.web3j.tx.response.NoOpProcessor
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContractsApiProvider @Inject constructor(
    private val web3jProvider: Web3jProvider,
    private val ethereumCredentialsMapper: EthereumCredentialsMapper,
    private val ethereumConfigProvider: EthereumConfigProvider
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
            smartContractApi = SmartContractApi(web3jProvider.web3j, getOrCreateFastRawTransactionManager(ethCredentials), gasProvider, ethereumConfigProvider)
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