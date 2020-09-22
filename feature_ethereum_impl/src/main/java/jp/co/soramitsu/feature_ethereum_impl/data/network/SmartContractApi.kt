package jp.co.soramitsu.feature_ethereum_impl.data.network

import jp.co.soramitsu.feature_ethereum_impl.BuildConfig
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
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.RemoteCall
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.Contract
import org.web3j.utils.Convert
import java.math.BigDecimal
import java.math.BigInteger

class SmartContractApi(
    web3j: Web3j,
    credentials: Credentials,
    gasProvider: GasProvider
) : Contract(BIN_NOT_PROVIDED, BuildConfig.MASTER_CONTRACT_ADDRESS, web3j, credentials, gasProvider) {

    companion object {
        private const val FUNC_MINTTOKENSBYPEERS = "mintTokensByPeers"
        private const val FUNC_XORTOKENINSTANCE = "tokenInstance"
        private const val FUNC_USED = "used"
    }

    fun used(txHash: ByteArray): RemoteCall<BigInteger> {
        val function = Function(FUNC_USED,
            listOf(Bytes32(txHash)),
            listOf<TypeReference<*>>(object : TypeReference<Uint>() {}))
        return executeRemoteCallSingleValueReturn(function, BigInteger::class.java)
    }

    fun xorTokenInstance(): RemoteCall<String> {
        val function = Function(FUNC_XORTOKENINSTANCE,
            listOf(),
            listOf<TypeReference<*>>(object : TypeReference<Address>() {}))
        return executeRemoteCallSingleValueReturn<String>(function, String::class.java)
    }

    fun mintTokensByPeers(amount: BigDecimal, beneficiary: String, txHash: ByteArray, v: List<BigInteger>, r: List<ByteArray>, s: List<ByteArray>, from: String, xorTokenAddress: String): RemoteCall<TransactionReceipt> {
        val amountInWei = Convert.toWei(amount, Convert.Unit.ETHER).toBigInteger()
        return executeRemoteCallTransaction(getMintTokenFunction(amountInWei, beneficiary, txHash, v, r, s, from, xorTokenAddress))
    }

    private fun getMintTokenFunction(amount: BigInteger, beneficiary: String, txHash: ByteArray, v: List<BigInteger>, r: List<ByteArray>, s: List<ByteArray>, from: String, xorTokenAddress: String): org.web3j.abi.datatypes.Function {
        return Function(
            FUNC_MINTTOKENSBYPEERS,
            listOf(Address(xorTokenAddress),
                Uint256(amount),
                Address(beneficiary),
                Bytes32(txHash),
                DynamicArray(
                    Uint8::class.java,
                    Utils.typeMap<BigInteger, Uint8>(v, Uint8::class.java)),
                DynamicArray(
                    Bytes32::class.java,
                    Utils.typeMap<ByteArray, Bytes32>(r, Bytes32::class.java)),
                DynamicArray(
                    Bytes32::class.java,
                    Utils.typeMap<ByteArray, Bytes32>(s, Bytes32::class.java)),
                Address(from)),
            emptyList())
    }
}