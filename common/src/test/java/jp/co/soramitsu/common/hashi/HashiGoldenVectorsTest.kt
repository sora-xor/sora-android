package jp.co.soramitsu.common.hashi

import java.math.BigInteger
import jp.co.soramitsu.common.ethereum.Eip1559Transaction
import jp.co.soramitsu.common.ethereum.EthAbi
import jp.co.soramitsu.common.ethereum.EthAddress
import jp.co.soramitsu.common.ethereum.EthHex
import jp.co.soramitsu.common.ethereum.EthSignature
import jp.co.soramitsu.common.ethereum.EthereumKeyDerivation
import jp.co.soramitsu.common.ethereum.EthereumSigner
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every byte of the HASHI bridge golden vectors (`hashi-bridge-2026-11-24/vectors`, generated
 * from live mainnet with ethers 6.13.4 and polkadot.js, read-only) reproduced by the wallet's own
 * code: key derivation, EIP-55, ABI calldata (five real mainnet transactions included), EIP-1559
 * signing, SORA storage keys and the bridge RPC answers.
 */
class HashiGoldenVectorsTest {

    private val json = Json
    private val eth: JsonObject = load("hashi/eth.json")
    private val sora: JsonObject = load("hashi/sora.json")

    private fun load(path: String): JsonObject =
        json.parseToJsonElement(checkNotNull(javaClass.classLoader!!.getResource(path)).readText()).jsonObject

    private fun JsonElement.str(): String = jsonPrimitive.content
    private fun JsonObject.str(name: String): String = getValue(name).str()
    private fun hex(text: String): ByteArray = EthHex.decode(text)
    private fun lowerHex(bytes: ByteArray): String = EthHex.encode(bytes)

    // --- Ethereum account ------------------------------------------------------------------------

    @Test
    fun `key derivation matches every case (BIP-39 seed, m 44h 60h 0h 0 0, public key, address)`() {
        val derivation = eth.getValue("keyDerivation").jsonObject
        assertEquals(EthereumKeyDerivation.DEFAULT_PATH, derivation.str("path"))
        val cases = derivation.getValue("cases").jsonArray
        assertEquals(5, cases.size)
        cases.forEach { element ->
            val case = element.jsonObject
            val mnemonic = case.str("mnemonic")
            val seed = EthereumKeyDerivation.bip39Seed(mnemonic)
            assertEquals(case.str("bip39SeedHex"), lowerHex(seed))
            val key = EthereumKeyDerivation.derive(mnemonic)
            assertEquals(mnemonic, case.str("privateKey"), lowerHex(key.privateKey))
            assertEquals(case.str("publicKeyUncompressed"), lowerHex(key.publicKey))
            assertEquals(case.str("address"), key.checksumAddress)
            key.destroy()
            assertTrue("destroy wipes the private key", key.privateKey.all { it == 0.toByte() })
        }
    }

    @Test
    fun `EIP-55 checksums match and a wrong checksum is refused`() {
        eth.getValue("checksum").jsonArray.forEach { element ->
            val case = element.jsonObject
            val lower = case.str("lower")
            val checksummed = case.str("checksummed")
            assertEquals(checksummed, EthAddress.checksum(hex(lower)))
            assertArrayEquals(hex(lower), EthAddress.parse(lower))
            assertArrayEquals(hex(lower), EthAddress.parse(checksummed))
            assertArrayEquals(hex(lower), EthAddress.parse("0x" + lower.substring(2).uppercase()))
            // Flip the case of the first letter: a mixed-case address with a wrong checksum.
            val index = checksummed.indexOfFirst { it.isLetter() && it != 'x' }
            val flipped = checksummed.substring(0, index) +
                (if (checksummed[index].isUpperCase()) checksummed[index].lowercaseChar() else checksummed[index].uppercaseChar()) +
                checksummed.substring(index + 1)
            assertNull(flipped, EthAddress.parse(flipped))
        }
    }

    // --- ABI -------------------------------------------------------------------------------------

    @Test
    fun `ABI calldata of every bridge and ERC-20 call matches`() {
        val bridge = HashiBridgeContract.bridge
        eth.getValue("abi").jsonArray.forEach { element ->
            val case = element.jsonObject
            val args = case.getValue("args").jsonArray.map { it.str() }
            val actual = when (case.str("fn")) {
                "approve", "approve(0)" -> HashiBridgeCalls.approve(hex(args[0]), BigInteger(args[1]))
                "allowance" -> HashiBridgeCalls.allowance(hex(args[0]), hex(args[1]))
                "balanceOf" -> HashiBridgeCalls.balanceOf(hex(args[0]))
                "decimals" -> HashiBridgeCalls.decimals()
                "used" -> HashiBridgeCalls.used(hex(args[0]))
                "sendERC20ToSidechain" -> HashiBridgeCalls.sendErc20ToSidechain(hex(args[0]), BigInteger(args[1]), hex(args[2]))
                "sendEthToSidechain" -> HashiBridgeCalls.sendEthToSidechain(hex(args[0]))
                else -> error("unexpected ${case.str("fn")}")
            }
            assertEquals(case.str("fn"), case.str("calldata"), lowerHex(actual))
        }
        assertArrayEquals(hex(HashiBridgeContract.BRIDGE_ADDRESS), bridge)
    }

    @Test
    fun `_sidechainTokens calldata for XOR, PSWAP, TBCD and KUSD matches`() {
        val tokens = eth.getValue("contracts").jsonObject.getValue("thischainTokensFromBridge_sidechainTokens").jsonObject
        assertEquals(4, tokens.size)
        tokens.values.forEach { element ->
            val case = element.jsonObject
            assertEquals(case.str("calldata"), lowerHex(HashiBridgeCalls.sidechainTokens(hex(case.str("assetId")))))
        }
    }

    @Test
    fun `five real mainnet transaction inputs are rebuilt byte for byte`() {
        val transactions = eth.getValue("realTransactions").jsonArray
        assertEquals(5, transactions.size)
        transactions.forEach { element ->
            val tx = element.jsonObject
            val args = tx.getValue("args").jsonArray.map { it.jsonObject }
            val values = args.map { arg ->
                val value = arg.getValue("value")
                when (val type = arg.str("type")) {
                    "address" -> EthAbi.Value.Address(hex(value.str()))
                    "uint256" -> EthAbi.Value.Uint(BigInteger(value.str()))
                    "bytes32" -> EthAbi.Value.Bytes32(hex(value.str()))
                    "uint8[]" -> EthAbi.Value.StaticArray(
                        "uint8", value.jsonArray.map { EthAbi.Value.Uint(BigInteger(it.str()), 8) },
                    )
                    "bytes32[]" -> EthAbi.Value.StaticArray("bytes32", value.jsonArray.map { EthAbi.Value.Bytes32(hex(it.str())) })
                    else -> error("unexpected type $type")
                }
            }
            val name = tx.str("function").substringBefore('(')
            assertEquals(tx.str("function"), EthAbi.signatureOf(name, values))
            assertEquals(tx.str("selector"), lowerHex(EthAbi.selector(tx.str("function"))))
            assertEquals(tx.str("label"), tx.str("input"), lowerHex(EthAbi.encodeCall(name, values)))

            // The same bytes through the named builders the wallet uses.
            fun a(i: Int) = args[i].getValue("value")
            fun v(i: Int) = a(i).jsonArray.map { it.str().toInt() }
            fun b(i: Int) = a(i).jsonArray.map { hex(it.str()) }
            val built = when (name) {
                "receiveByEthereumAssetAddress" -> HashiBridgeCalls.receiveByEthereumAssetAddress(
                    hex(a(0).str()), BigInteger(a(1).str()), hex(a(2).str()), hex(a(3).str()), hex(a(4).str()), v(5), b(6), b(7),
                )
                "receiveBySidechainAssetId" -> HashiBridgeCalls.receiveBySidechainAssetId(
                    hex(a(0).str()), BigInteger(a(1).str()), hex(a(2).str()), hex(a(3).str()), hex(a(4).str()), v(5), b(6), b(7),
                )
                "mintTokensByPeers" -> HashiBridgeCalls.mintTokensByPeers(
                    hex(a(0).str()), BigInteger(a(1).str()), hex(a(2).str()), hex(a(3).str()), v(4), b(5), b(6), hex(a(7).str()),
                )
                "sendERC20ToSidechain" -> HashiBridgeCalls.sendErc20ToSidechain(hex(a(0).str()), BigInteger(a(1).str()), hex(a(2).str()))
                else -> error("unexpected $name")
            }
            assertEquals(tx.str("label"), tx.str("input"), lowerHex(built))
            // Every real claim used v = 27 or 28.
            args.filter { it.str("type") == "uint8[]" }.forEach { arg ->
                assertTrue(arg.getValue("value").jsonArray.all { it.str() == "27" || it.str() == "28" })
            }
        }
    }

    // --- Approved requests and the claim -------------------------------------------------------

    private fun pswap() = HashiBridgeAsset(
        kind = HashiAssetKind.THISCHAIN,
        assetId = "0x0200050000000000000000000000000000000000000000000000000000000000",
        soraPrecision = 18,
        ethToken = hex("0x519C1001D550C0a1DaE7d1fC220f7d14c2A521BB"),
        ethDecimals = 18,
    )

    @Test
    fun `approved requests parse with byte-array r and s, v plus 27, and plan the exact claim calldata`() {
        val cases = eth.getValue("approvedRequestToClaim").jsonArray
        assertEquals(2, cases.size)
        cases.forEach { element ->
            val case = element.jsonObject
            val requestHash = hex(case.str("requestHash"))
            val request = checkNotNull(HashiRpcParsers.approvedRequest(case.getValue("rpcResponse"), requestHash))
            assertEquals(BigInteger(case.str("amount")), request.amount)
            assertEquals(case.getValue("vFromRpc").jsonArray.map { it.jsonPrimitive.int }, request.signatures.map { it.v })
            assertEquals(case.getValue("vForContract").jsonArray.map { it.jsonPrimitive.int }, request.signatures.map { it.contractV })
            assertEquals(case.getValue("r").jsonArray.map { it.str() }, request.signatures.map { lowerHex(it.r) })
            assertEquals(case.getValue("s").jsonArray.map { it.str() }, request.signatures.map { lowerHex(it.s) })
            val currency = request.currency as HashiCurrency.AssetId
            val asset = HashiBridgeAsset(
                kind = HashiAssetKind.THISCHAIN,
                assetId = lowerHex(currency.id),
                soraPrecision = 18,
                ethToken = ByteArray(20) { 1 },
                ethDecimals = 18,
            )
            val claim = HashiClaimPlanner.plan(asset, request, expectedRecipient = request.to, expectedAmount = request.amount)
            assertEquals(HashiClaimCall.Method.RECEIVE_BY_SIDECHAIN_ASSET_ID, claim.method)
            assertEquals("Bridge", case.str("contract"))
            assertArrayEquals(HashiBridgeContract.bridge, claim.contract)
            assertEquals(case.str("calldata"), lowerHex(claim.data))
            assertArrayEquals(requestHash, claim.txHash)
            assertEquals(lowerHex(HashiBridgeCalls.used(requestHash)), lowerHex(HashiBridgeCalls.used(claim.txHash)))
        }
    }

    @Test
    fun `the sora json approved request answer parses the same way`() {
        val rpc = sora.getValue("rpc").jsonObject.getValue("getApprovedRequests").jsonArray[0].jsonObject
        val hash = hex(rpc.getValue("params").jsonArray[0].jsonArray[0].str())
        val request = checkNotNull(HashiRpcParsers.approvedRequest(rpc.getValue("response"), hash))
        assertEquals(BigInteger("2724999999999999994633471"), request.amount)
        assertEquals("0x94b3c5fc67b0c63befcc74d7afcd333baa697b2a", lowerHex(request.to))
        // Bound to the transfer: the expected Ethereum amount is computed from the SORA amount the
        // chain recorded for the request (PSWAP is owned, factor 1: the same number).
        val outgoing = sora.getValue("rpc").jsonObject.getValue("getRequests").jsonObject.getValue("response")
            .jsonObject.getValue("Ok").jsonArray[0].jsonArray[0].jsonObject.getValue("Outgoing").jsonArray[0]
            .jsonObject.getValue("Transfer").jsonObject
        val soraAmount = BigInteger(outgoing.str("amount"))
        val expected = HashiAmounts.expectedEthAmount(soraAmount, pswap(), BigInteger.ONE)
        val claim = HashiClaimPlanner.plan(pswap(), request, hex("0x94b3c5Fc67b0c63beFCC74D7Afcd333baa697B2A"), expected)
        assertEquals(eth.getValue("approvedRequestToClaim").jsonArray[0].jsonObject.str("calldata"), lowerHex(claim.data))
        val mismatch = runCatching {
            HashiClaimPlanner.plan(pswap(), request, request.to, expected.add(BigInteger.ONE))
        }.exceptionOrNull() as HashiClaimAmountMismatchException
        assertEquals(expected.add(BigInteger.ONE), mismatch.expected)
        assertEquals(request.amount, mismatch.approved)
    }

    @Test
    fun `an approved request for another hash or network, or with v 27, is refused`() {
        val case = eth.getValue("approvedRequestToClaim").jsonArray[0].jsonObject
        val response = case.getValue("rpcResponse")
        val requestHash = hex(case.str("requestHash"))
        assertRefused { HashiRpcParsers.approvedRequest(response, ByteArray(32)) }
        val text = response.toString()
        // v: 0/1 get +27, 27/28 stay, anything else is refused.
        val already27 = checkNotNull(
            HashiRpcParsers.approvedRequest(json.parseToJsonElement(text.replace("\"v\":0", "\"v\":27")), requestHash)
        )
        assertEquals(listOf(27, 27, 27), already27.signatures.map { it.contractV })
        assertRefused {
            HashiRpcParsers.approvedRequest(json.parseToJsonElement(text.replace("\"v\":0", "\"v\":2")), requestHash)
        }
        assertRefused {
            HashiRpcParsers.approvedRequest(json.parseToJsonElement(text.replace("\"v\":0", "\"v\":29")), requestHash)
        }
        assertRefused {
            HashiRpcParsers.approvedRequest(
                json.parseToJsonElement(
                    text.replace(
                        "\"network_id\":\"0x0000000000000000000000000000000000000000000000000000000000000000\"",
                        "\"network_id\":\"0x0000000000000000000000000000000000000000000000000000000000000001\"",
                    )
                ),
                requestHash,
            )
        }
        assertRefused { HashiRpcParsers.approvedRequest(buildJsonObject { put("Err", "x") }, requestHash) }
        assertNull(HashiRpcParsers.approvedRequest(json.parseToJsonElement("{\"Ok\":[]}"), requestHash))
    }

    @Test
    fun `a claim for another recipient is refused`() {
        val case = eth.getValue("approvedRequestToClaim").jsonArray[0].jsonObject
        val request = checkNotNull(HashiRpcParsers.approvedRequest(case.getValue("rpcResponse"), hex(case.str("requestHash"))))
        assertRefused { HashiClaimPlanner.plan(pswap(), request, ByteArray(20) { 7 }, request.amount) }
        assertRefused { HashiClaimPlanner.plan(pswap(), request, ByteArray(20), request.amount) }
    }

    // --- EIP-1559 ----------------------------------------------------------------------------------

    @Test
    fun `EIP-1559 unsigned bytes, signing hash, RFC 6979 low-s signature, raw transaction and hash match`() {
        val cases = eth.getValue("eip1559").jsonArray
        assertEquals(3, cases.size)
        cases.forEach { element ->
            val case = element.jsonObject
            val tx = Eip1559Transaction(
                chainId = case.getValue("chainId").jsonPrimitive.long,
                nonce = BigInteger(case.getValue("nonce").str()),
                maxPriorityFeePerGas = BigInteger(case.str("maxPriorityFeePerGas")),
                maxFeePerGas = BigInteger(case.str("maxFeePerGas")),
                gasLimit = BigInteger(case.str("gasLimit")),
                to = hex(case.str("to")),
                value = BigInteger(case.str("value")),
                data = hex(case.str("data")),
            )
            assertEquals(case.str("unsignedSerialized"), lowerHex(tx.unsignedSerialized()))
            assertEquals(case.str("signingHash"), lowerHex(tx.signingHash()))
            val privateKey = hex(case.str("privateKey"))
            val signed = tx.sign(privateKey, expectedFrom = hex(case.str("from")))
            val signature = case.getValue("signature").jsonObject
            assertEquals(signature.str("r"), lowerHex(signed.signature.r))
            assertEquals(signature.str("s"), lowerHex(signed.signature.s))
            assertEquals(signature.getValue("yParity").jsonPrimitive.int, signed.signature.yParity)
            assertEquals(case.str("rawSigned"), signed.rawHex)
            assertEquals(case.str("txHash"), signed.hashHex)
            // The signature recovers the sender.
            val recovered = checkNotNull(EthereumSigner.recoverPublicKey(tx.signingHash(), signed.signature))
            assertEquals(case.str("from"), EthAddress.checksum(EthAddress.fromPublicKey(recovered)))
        }
    }

    @Test
    fun `signing is deterministic and refuses a transaction from another account`() {
        val case = eth.getValue("eip1559").jsonArray[1].jsonObject
        val tx = Eip1559Transaction(
            1, BigInteger.valueOf(7), BigInteger("1000000"), BigInteger("12345678901"), BigInteger("60000"),
            hex(case.str("to")), BigInteger.ZERO, hex(case.str("data")),
        )
        val key = hex(case.str("privateKey"))
        assertEquals(tx.sign(key).rawHex, tx.sign(key).rawHex)
        assertRefused { tx.sign(key, expectedFrom = ByteArray(20)) }
        // A high-s signature is never produced.
        val s = tx.sign(key).signature.sValue
        assertTrue(s <= jp.co.soramitsu.common.ethereum.Secp256k1.order.shiftRight(1))
        val unused = EthSignature(ByteArray(32) { 1 }, ByteArray(32) { 2 }, 0)
        assertFalse(unused.r.contentEquals(unused.s))
    }

    // --- SORA storage, enums and RPC answers ------------------------------------------------------

    @Test
    fun `storage keys and values match`() {
        val storage = sora.getValue("storage").jsonObject
        val recovery = storage.getValue("recoveryBridgeHaltKey").jsonObject
        assertEquals(recovery.str("keyHex"), HashiBridgeContract.RECOVERY_HALT_KEY)
        assertEquals(recovery.str("keyHex"), lowerHex(recovery.str("utf8").toByteArray()))
        val guardian = storage.getValue("guardianHaltKey").jsonObject
        assertEquals(guardian.str("keyHex"), HashiBridgeContract.GUARDIAN_HALT_KEY)
        assertEquals(guardian.str("keyHex"), lowerHex(guardian.str("utf8").toByteArray()))

        val statuses = storage.getValue("requestStatuses").jsonObject
        val args = statuses.getValue("exampleArgs").jsonArray
        assertEquals(0, args[0].jsonPrimitive.int)
        assertEquals(statuses.str("exampleKey"), HashiSoraStorage.requestStatusKey(hex(args[1].str())))
        assertEquals(HashiRequestStatus.APPROVALS_READY, HashiRequestStatus.fromScale(statuses.str("exampleValueHex")))

        val bridgeStatus = storage.getValue("bridgeStatuses").jsonObject
        assertEquals(bridgeStatus.str("key"), HashiSoraStorage.bridgeStatusKey())
        val contract = storage.getValue("bridgeContractAddress").jsonObject
        assertEquals(contract.str("key"), HashiSoraStorage.bridgeContractAddressKey())
        assertArrayEquals(HashiBridgeContract.bridge, HashiSoraStorage.decodeAddress(contract.str("valueHex")))
        val valMaster = storage.getValue("valMasterContractAddress").jsonObject
        assertEquals(valMaster.str("key"), HashiSoraStorage.valMasterContractAddressKey)
        assertArrayEquals(HashiBridgeContract.valMaster, HashiSoraStorage.decodeAddress(valMaster.str("valueHex")))
        val xorMaster = storage.getValue("xorMasterContractAddress").jsonObject
        assertArrayEquals(HashiBridgeContract.legacyXorMaster, HashiSoraStorage.decodeAddress(xorMaster.str("valueHex")))

        assertTrue(guardian.getValue("liveValueAtGeneration") is JsonNull)
    }

    @Test
    fun `request status enum indices match the runtime`() {
        val enum = sora.getValue("enums").jsonObject.getValue("RequestStatus").jsonArray
        assertEquals(HashiRequestStatus.entries.size, enum.size)
        enum.forEach { element ->
            val variant = element.jsonObject
            val index = variant.getValue("index").jsonPrimitive.int
            assertEquals(variant.str("name"), HashiRequestStatus.entries[index].wireName)
        }
        assertEquals(HashiRequestStatus.FAILED, HashiRequestStatus.fromScale("0x030301"))
        assertRefused { HashiRequestStatus.fromScale("0x06") }
        assertRefused { HashiRequestStatus.fromScale("0x0201") }
    }

    @Test
    fun `event, error and call indices match`() {
        assertEquals(HashiBridgeContract.PALLET_INDEX, sora.getValue("ethBridgePalletIndex").jsonPrimitive.int)
        val event = sora.getValue("events").jsonObject.getValue("RequestRegistered").jsonObject
        assertEquals(HashiBridgeContract.PALLET_INDEX, event.getValue("palletIndex").jsonPrimitive.int)
        assertEquals(HashiBridgeContract.REQUEST_REGISTERED_EVENT_INDEX, event.getValue("eventIndex").jsonPrimitive.int)
        assertEquals(
            "0x1f03",
            lowerHex(byteArrayOf(HashiBridgeContract.PALLET_INDEX.toByte(), HashiBridgeContract.TRANSFER_TO_SIDECHAIN_CALL_INDEX.toByte())),
        )
        val errors = sora.getValue("errors").jsonArray.associate {
            it.jsonObject.getValue("index").jsonPrimitive.int to it.jsonObject.str("name")
        }
        errors.forEach { (index, name) -> assertEquals(name, HashiSoraErrors.nameOf(index)) }
    }

    @Test
    fun `transferToSidechain call bytes match`() {
        val call = sora.getValue("transferToSidechain").jsonObject
        assertEquals("0x1f03", call.str("callIndexHex"))
        call.getValue("examples").jsonArray.forEach { element ->
            val example = element.jsonObject
            val bytes = HashiSoraCalls.transferToSidechain(
                assetId = hex(example.str("assetId")),
                to = hex(example.str("to")),
                amount = BigInteger(example.str("amount")),
                networkId = example.getValue("networkId").jsonPrimitive.int,
            )
            assertEquals(example.str("callHex"), lowerHex(bytes))
        }
        assertRefused {
            HashiSoraCalls.transferToSidechain(ByteArray(32), ByteArray(20), BigInteger.ONE.shiftLeft(128), 0)
        }
    }

    @Test
    fun `registered assets parse and keep kind, precision, token and decimals`() {
        val rpc = sora.getValue("rpc").jsonObject.getValue("getRegisteredAssets").jsonObject
        val rows = rpc.getValue("sampleRows").jsonArray
        val parsed = HashiRpcParsers.registeredAssets(JsonObject(mapOf("Ok" to rows)))
        assertEquals(rows.size, parsed.size)
        val usdt = parsed[0]
        assertEquals(HashiAssetKind.SIDECHAIN, usdt.kind)
        assertEquals("0x0083a6b3fbc6edae06f115c8953ddd7cbfba0b74579d6ea190f96853073b76f4", usdt.assetId)
        assertEquals(18, usdt.soraPrecision)
        assertEquals("0xdac17f958d2ee523a2206206994597c13d831ec7", lowerHex(checkNotNull(usdt.ethToken)))
        assertEquals(6, usdt.ethDecimals)
        val xor = parsed[1]
        assertEquals(HashiAssetKind.THISCHAIN, xor.kind)
        assertNull(xor.ethToken)
        assertEquals(HashiAssetKind.SIDECHAIN_OWNED, parsed[2].kind)
        assertEquals(rpc.getValue("kindCounts").jsonObject.keys, setOf("Sidechain", "Thischain", "SidechainOwned"))
        // A Thischain row with a token, or a Sidechain row without one, is not the documented shape.
        assertRefused {
            HashiRpcParsers.registeredAssets(
                json.parseToJsonElement("{\"Ok\":[[\"Sidechain\",[\"0x${"00".repeat(32)}\",18],null]]}")
            )
        }
    }

    @Test
    fun `getRequests answer parses status ApprovalsReady and the request hash`() {
        val rpc = sora.getValue("rpc").jsonObject.getValue("getRequests").jsonObject
        val rows = HashiRpcParsers.requests(rpc.getValue("response"))
        assertEquals(1, rows.size)
        assertEquals("Outgoing", rows[0].direction)
        assertEquals(HashiRequestStatus.APPROVALS_READY, rows[0].status)
        assertEquals(rpc.getValue("params").jsonArray[0].jsonArray[0].str(), rows[0].requestHash)
        val failed = HashiRpcParsers.requests(
            json.parseToJsonElement("{\"Ok\":[[{\"Incoming\":[{\"Transfer\":{}},\"0x${"11".repeat(32)}\"]},{\"Failed\":{\"Module\":{}}}]]}")
        )
        assertEquals(HashiRequestStatus.FAILED, failed.single().status)
        assertTrue(failed.single().status.isFinal)
        assertFalse(HashiRequestStatus.FROZEN.isFinal)
        assertFalse(HashiRequestStatus.BROKEN.isFinal)
        assertEquals(emptyList<HashiRequestRow>(), HashiRpcParsers.requests(json.parseToJsonElement("{\"Ok\":[]}")))
    }

    @Test
    fun `SORA accounts become the bytes32 recipient of a deposit`() {
        sora.getValue("soraAccountToBytes32").jsonArray.forEach { element ->
            val case = element.jsonObject
            assertEquals(case.str("bytes32"), lowerHex(case.str("address").toAccountId()))
        }
    }

    @Test
    fun `the claim selection rule text names the three calls the planner uses`() {
        val rules = eth.getValue("claimSelectionRule").jsonArray.joinToString("\n") { it.str() }
        listOf("receiveByEthereumAssetAddress", "mintTokensByPeers", "receiveBySidechainAssetId", "used(txHash)").forEach {
            assertTrue(it, it in rules)
        }
        val contracts = eth.getValue("contracts").jsonObject
        assertEquals(contracts.str("bridge"), HashiBridgeContract.BRIDGE_ADDRESS)
        assertEquals(contracts.str("valMaster"), HashiBridgeContract.VAL_MASTER_ADDRESS)
        assertEquals(contracts.str("valToken"), HashiBridgeContract.VAL_TOKEN_ADDRESS)
        assertEquals(contracts.str("xorMasterLegacyDecommissioned"), HashiBridgeContract.LEGACY_XOR_MASTER_ADDRESS)
        assertNotNull(EthAddress.parse(contracts.str("bridge")))
    }

    private fun assertRefused(block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("expected a refusal, got $error", error is IllegalArgumentException || error is IllegalStateException)
    }
}
