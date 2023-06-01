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

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.feature_ethereum_impl.util.GasProvider
import org.web3j.abi.TypeReference
import org.web3j.abi.Utils
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Uint
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Uint8
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.RemoteCall
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.Contract
import org.web3j.tx.FastRawTransactionManager
import org.web3j.utils.Convert

class SmartContractApi(
    web3j: Web3j,
    fastRawTransactionManager: FastRawTransactionManager,
    gasProvider: GasProvider,
) : Contract(
    BIN_NOT_PROVIDED,
    "ethereumConfigProvider.config.masterContract",
    web3j,
    fastRawTransactionManager,
    gasProvider
) {

    companion object {
        private const val FUNC_MINTTOKENSBYPEERS = "mintTokensByPeers"
        private const val FUNC_VALTOKENINSTANCE = "tokenInstance"
        private const val FUNC_USED = "used"
        private const val FUNC_PROOF = "proof"
    }

    fun used(txHash: ByteArray): RemoteCall<BigInteger> {
        val function = Function(
            FUNC_USED,
            listOf(Bytes32(txHash)),
            listOf<TypeReference<*>>(object : TypeReference<Uint>() {})
        )
        return executeRemoteCallSingleValueReturn(function, BigInteger::class.java)
    }

    fun proof(): RemoteCall<ByteArray> {
        val function = Function(
            FUNC_PROOF,
            listOf(),
            listOf<TypeReference<*>>(object : TypeReference<Bytes32>() {})
        )
        return executeRemoteCallSingleValueReturn(function, ByteArray::class.java)
    }

    fun valTokenInstance(): RemoteCall<String> {
        val function = Function(
            FUNC_VALTOKENINSTANCE,
            listOf(),
            listOf<TypeReference<*>>(object : TypeReference<Address>() {})
        )
        return executeRemoteCallSingleValueReturn<String>(function, String::class.java)
    }

    fun mintTokensByPeers(
        amount: BigDecimal,
        beneficiary: String,
        txHash: ByteArray,
        v: List<BigInteger>,
        r: List<ByteArray>,
        s: List<ByteArray>,
        from: String,
        tokenAddress: String
    ): RemoteCall<TransactionReceipt> {
        val amountInWei = Convert.toWei(amount, Convert.Unit.ETHER).toBigInteger()
        return executeRemoteCallTransaction(
            getMintTokenFunction(
                amountInWei,
                beneficiary,
                txHash,
                v,
                r,
                s,
                from,
                tokenAddress
            )
        )
    }

    private fun getMintTokenFunction(
        amount: BigInteger,
        beneficiary: String,
        txHash: ByteArray,
        v: List<BigInteger>,
        r: List<ByteArray>,
        s: List<ByteArray>,
        from: String,
        tokenAddress: String
    ): Function {
        return Function(
            FUNC_MINTTOKENSBYPEERS,
            listOf(
                Address(tokenAddress),
                Uint256(amount),
                Address(beneficiary),
                Bytes32(txHash),
                DynamicArray(
                    Uint8::class.java,
                    Utils.typeMap<BigInteger, Uint8>(v, Uint8::class.java)
                ),
                DynamicArray(
                    Bytes32::class.java,
                    Utils.typeMap<ByteArray, Bytes32>(r, Bytes32::class.java)
                ),
                DynamicArray(
                    Bytes32::class.java,
                    Utils.typeMap<ByteArray, Bytes32>(s, Bytes32::class.java)
                ),
                Address(from)
            ),
            emptyList()
        )
    }
}
