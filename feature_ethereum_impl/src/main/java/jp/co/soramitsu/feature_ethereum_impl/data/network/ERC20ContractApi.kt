package jp.co.soramitsu.feature_ethereum_impl.data.network

import jp.co.soramitsu.feature_ethereum_impl.util.GasProvider
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.RemoteCall
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.Contract
import org.web3j.tx.FastRawTransactionManager
import java.math.BigInteger
import java.util.Collections

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
