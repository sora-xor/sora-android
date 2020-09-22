/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl.util

import org.web3j.tx.gas.ContractGasProvider
import java.math.BigInteger

data class GasProvider(
    var estimatedGas: BigInteger,
    var price: BigInteger
) : ContractGasProvider {

    override fun getGasLimit(contractFunc: String?): BigInteger = estimatedGas

    override fun getGasLimit(): BigInteger = estimatedGas

    override fun getGasPrice(contractFunc: String?): BigInteger = price

    override fun getGasPrice(): BigInteger = price
}