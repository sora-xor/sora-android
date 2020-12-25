/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl.data.repository.converter

import org.web3j.utils.Convert
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

class EtherWeiConverter @Inject constructor() {

    fun fromWeiToEther(amountInWei: BigInteger): BigDecimal {
        return Convert.fromWei(amountInWei.toBigDecimal(), Convert.Unit.ETHER)
    }

    fun fromGweiToEther(amountInGwei: BigInteger): BigDecimal {
        return Convert.fromWei(fromGweiToWei(amountInGwei).toBigDecimal(), Convert.Unit.ETHER)
    }

    fun fromEtherToWei(amountInEther: BigDecimal): BigInteger {
        return Convert.toWei(amountInEther, Convert.Unit.ETHER).toBigInteger()
    }

    fun fromGweiToWei(amountInGwei: BigInteger): BigInteger {
        return Convert.toWei(amountInGwei.toBigDecimal(), Convert.Unit.GWEI).toBigInteger()
    }

    fun fromWeiToGwei(amountInGwei: BigInteger): BigInteger {
        return Convert.fromWei(amountInGwei.toBigDecimal(), Convert.Unit.GWEI).toBigInteger()
    }
}