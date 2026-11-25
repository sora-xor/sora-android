package jp.co.soramitsu.common.hashi

import java.math.BigInteger
import jp.co.soramitsu.common.ethereum.EthAbi
import jp.co.soramitsu.common.ethereum.EthAbi.Value

/**
 * Calldata of every Ethereum call the wallet makes for the HASHI bridge, in the argument order of
 * `Bridge.sol` / `MasterToken.sol` (sora2-evm-contracts) and ERC-20.
 */
object HashiBridgeCalls {

    // --- ERC-20 ---------------------------------------------------------------------------------

    fun approve(spender: ByteArray, amount: BigInteger): ByteArray =
        EthAbi.encodeCall("approve", listOf(Value.Address(spender), Value.Uint(amount)))

    fun allowance(owner: ByteArray, spender: ByteArray): ByteArray =
        EthAbi.encodeCall("allowance", listOf(Value.Address(owner), Value.Address(spender)))

    fun balanceOf(owner: ByteArray): ByteArray =
        EthAbi.encodeCall("balanceOf", listOf(Value.Address(owner)))

    fun decimals(): ByteArray = EthAbi.encodeCall("decimals", emptyList())

    // --- Bridge reads ---------------------------------------------------------------------------

    /** `used(bytes32)`: true once a claim with this SORA request hash was paid. */
    fun used(txHash: ByteArray): ByteArray = EthAbi.encodeCall("used", listOf(Value.Bytes32(txHash)))

    /** `_sidechainTokens(bytes32)`: the ERC-20 of a SORA-native (`Thischain`) asset. */
    fun sidechainTokens(assetId: ByteArray): ByteArray =
        EthAbi.encodeCall("_sidechainTokens", listOf(Value.Bytes32(assetId)))

    fun acceptedEthTokens(token: ByteArray): ByteArray =
        EthAbi.encodeCall("acceptedEthTokens", listOf(Value.Address(token)))

    fun sidechainTokensByAddress(token: ByteArray): ByteArray =
        EthAbi.encodeCall("_sidechainTokensByAddress", listOf(Value.Address(token)))

    // --- Ethereum → SORA ------------------------------------------------------------------------

    fun sendErc20ToSidechain(soraAccountId: ByteArray, amount: BigInteger, token: ByteArray): ByteArray =
        EthAbi.encodeCall(
            "sendERC20ToSidechain",
            listOf(Value.Bytes32(soraAccountId), Value.Uint(amount), Value.Address(token)),
        )

    fun sendEthToSidechain(soraAccountId: ByteArray): ByteArray =
        EthAbi.encodeCall("sendEthToSidechain", listOf(Value.Bytes32(soraAccountId)))

    // --- SORA → Ethereum claims -----------------------------------------------------------------

    fun receiveByEthereumAssetAddress(
        token: ByteArray,
        amount: BigInteger,
        to: ByteArray,
        from: ByteArray,
        txHash: ByteArray,
        v: List<Int>,
        r: List<ByteArray>,
        s: List<ByteArray>,
    ): ByteArray = EthAbi.encodeCall(
        "receiveByEthereumAssetAddress",
        listOf(Value.Address(token), Value.Uint(amount), Value.Address(to), Value.Address(from), Value.Bytes32(txHash)) +
            signatureArrays(v, r, s),
    )

    fun receiveBySidechainAssetId(
        assetId: ByteArray,
        amount: BigInteger,
        to: ByteArray,
        from: ByteArray,
        txHash: ByteArray,
        v: List<Int>,
        r: List<ByteArray>,
        s: List<ByteArray>,
    ): ByteArray = EthAbi.encodeCall(
        "receiveBySidechainAssetId",
        listOf(Value.Bytes32(assetId), Value.Uint(amount), Value.Address(to), Value.Address(from), Value.Bytes32(txHash)) +
            signatureArrays(v, r, s),
    )

    /** VAL master; note `from` comes last here. */
    fun mintTokensByPeers(
        token: ByteArray,
        amount: BigInteger,
        to: ByteArray,
        txHash: ByteArray,
        v: List<Int>,
        r: List<ByteArray>,
        s: List<ByteArray>,
        from: ByteArray,
    ): ByteArray = EthAbi.encodeCall(
        "mintTokensByPeers",
        listOf(Value.Address(token), Value.Uint(amount), Value.Address(to), Value.Bytes32(txHash)) +
            signatureArrays(v, r, s) + Value.Address(from),
    )

    private fun signatureArrays(v: List<Int>, r: List<ByteArray>, s: List<ByteArray>): List<Value> {
        require(v.isNotEmpty() && v.size == r.size && v.size == s.size) { "HASHI_SIGNATURES_INVALID" }
        require(v.all { it == 27 || it == 28 }) { "HASHI_SIGNATURE_V_INVALID" }
        return listOf(
            Value.StaticArray("uint8", v.map { Value.Uint(BigInteger.valueOf(it.toLong()), 8) }),
            Value.StaticArray("bytes32", r.map(Value::Bytes32)),
            Value.StaticArray("bytes32", s.map(Value::Bytes32)),
        )
    }
}
