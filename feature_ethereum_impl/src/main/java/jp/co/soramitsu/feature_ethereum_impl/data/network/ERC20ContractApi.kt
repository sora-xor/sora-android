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

package jp.co.soramitsu.feature_ethereum_impl.data.network

import java.math.BigInteger
import java.util.Collections
import jp.co.soramitsu.feature_ethereum_impl.util.GasProvider
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.RemoteCall
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.Contract
import org.web3j.tx.FastRawTransactionManager

class ERC20ContractApi(
    web3j: Web3j,
    fastRawTransactionManager: FastRawTransactionManager,
    customGasProvider: GasProvider,
    tokenAddress: String
) : Contract(BIN_NOT_PROVIDED, tokenAddress, web3j, fastRawTransactionManager, customGasProvider) {

    companion object {
        private const val FUNC_BALANCEOF = "balanceOf"
        private const val FUNC_TRANSFER = "transfer"
    }

    fun valBalance(to: String): RemoteCall<BigInteger> {
        val function = org.web3j.abi.datatypes.Function(
            FUNC_BALANCEOF,
            listOf(Address(to)),
            listOf<TypeReference<Uint256>>(object : TypeReference<Uint256>() {}) as List<TypeReference<*>>?
        )
        return executeRemoteCallSingleValueReturn(function, BigInteger::class.java)
    }

    fun transferVal(to: String, amountInWei: BigInteger): RemoteCall<TransactionReceipt> {
        return executeRemoteCallTransaction(getTransferValFunction(to, amountInWei))
    }

    private fun getTransferValFunction(to: String, amount: BigInteger): org.web3j.abi.datatypes.Function {
        return org.web3j.abi.datatypes.Function(
            FUNC_TRANSFER,
            listOf(
                Address(to),
                Uint256(amount)
            ),
            Collections.emptyList()
        )
    }
}
