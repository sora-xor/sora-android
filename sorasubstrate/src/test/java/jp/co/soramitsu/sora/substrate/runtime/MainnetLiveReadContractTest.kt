package jp.co.soramitsu.sora.substrate.runtime

import com.google.gson.Gson
import java.math.BigInteger
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.network.BoundedHttpTextClient
import jp.co.soramitsu.common_wallet.data.XorBalanceDto
import jp.co.soramitsu.common_wallet.domain.BalanceWrapper
import jp.co.soramitsu.sora.substrate.substrate.decodeXorAccountData
import jp.co.soramitsu.sora.substrate.testing.TestRuntimeSnapshots
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xsubstrate.runtime.metadata.RuntimeMetadataReader
import jp.co.soramitsu.xsubstrate.runtime.metadata.module
import jp.co.soramitsu.xsubstrate.runtime.metadata.storage
import jp.co.soramitsu.xsubstrate.runtime.metadata.storageKey
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.RuntimeMetadataSchemaV14
import jp.co.soramitsu.xsubstrate.scale.EncodableStruct
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * LIVE NETWORK TEST against SORA mainnet, read-only: it sends no transaction and needs no
 * account. Off by default and not part of CI; run it with:
 *
 *     RUN_LIVE_SORA_RPC_TESTS=true ./gradlew :sorasubstrate:testProductionDebugUnitTest \
 *       --tests '*MainnetLiveReadContractTest*' --rerun
 *
 * Keep `--rerun`: the environment variable is not a Gradle task input.
 *
 * It uses the production HTTPS client and the production JSON-RPC client, so a pass means that
 * both reviewed endpoints answer every read the app sends, that they are the same chain and
 * runtime, that the live runtime needs no app update, and that live `System.Account` values
 * decode to the transferable amount the chain allows. Run it before each release candidate and,
 * pointed at the staging network's runtime, before each mainnet runtime upgrade.
 */
class MainnetLiveReadContractTest {

    private val http = BoundedHttpTextClient()
    private val gson = Gson()

    /** The client exactly as the app builds it: two reviewed endpoints. */
    private val client by lazy { Sora2BoundedRuntimeRpcClient(http, gson, Sora2AddressCodec()) }

    /** One reviewed endpoint on its own, over the same production HTTPS client. */
    private fun only(endpoint: String) = Sora2BoundedRuntimeRpcClient(
        transport = Sora2RuntimeRpcTransport { requestBody, maximumResponseBytes ->
            http.postJson(
                rawUrl = endpoint,
                requestBody = requestBody,
                maximumBytes = maximumResponseBytes,
                absoluteTimeoutMillis = BoundedHttpTextClient.MAXIMUM_ABSOLUTE_TIMEOUT_MILLIS,
            ).body
        },
        gson = gson,
    )

    @Before
    fun requireOptIn() {
        assumeTrue(System.getenv("RUN_LIVE_SORA_RPC_TESTS") == "true")
    }

    @Test
    fun `both reviewed endpoints are SORA mainnet on the same runtime`() = runBlocking {
        val identities = ENDPOINTS.map { endpoint ->
            val single = only(endpoint)
            val identity = single.getFinalizedIdentity()
            val codeHash = single.getRuntimeCodeHashAtFinalized(identity.finalizedHash)
            println(
                "LIVE $endpoint finalized=${identity.finalizedBlockNumber} " +
                    "spec=${identity.runtimeVersion.specVersion} " +
                    "tx=${identity.runtimeVersion.transactionVersion} code=$codeHash"
            )
            assertEquals(Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH, identity.genesisHash)
            Triple(identity, codeHash, endpoint)
        }
        val (first, second) = identities
        assertEquals(first.first.runtimeVersion.specVersion, second.first.runtimeVersion.specVersion)
        assertEquals(
            first.first.runtimeVersion.transactionVersion,
            second.first.runtimeVersion.transactionVersion,
        )
        assertEquals(first.second, second.second)
        // Two healthy nodes finalize the same chain within a few blocks of each other.
        assertTrue(
            kotlin.math.abs(
                first.first.finalizedBlockNumber - second.first.finalizedBlockNumber
            ) < 50
        )
    }

    @Test
    fun `the live runtime needs no app update and its metadata fits the limits`() = runBlocking {
        val identity = client.getFinalizedIdentity()
        val metadata = client.getMetadataAtFinalized(identity.finalizedHash)
        val bytes = (metadata.length - 2) / 2
        println(
            "LIVE metadata bytes=$bytes sha256=${metadata.runtimeMetadataSha256()} " +
                "limit=${Sora2BoundedRuntimeRpcClient.MAXIMUM_METADATA_RESPONSE_BYTES}"
        )
        // The response is hex: two characters per byte, well inside the 16 MiB response limit.
        assertTrue(metadata.length < Sora2BoundedRuntimeRpcClient.MAXIMUM_METADATA_RESPONSE_BYTES / 2)

        @Suppress("UNCHECKED_CAST")
        val parsed = RuntimeMetadataReader.read(metadata).metadata
            as EncodableStruct<RuntimeMetadataSchemaV14>
        assertEquals(
            "The live runtime lists a signed extension with data the encoder does not write",
            emptyList<String>(),
            Sora2SignedExtensionAdmission.unsupportedExtensions(parsed),
        )
        // The second endpoint serves the same metadata for the same block.
        assertEquals(
            metadata.runtimeMetadataSha256(),
            only(ENDPOINTS[1]).getMetadataAtFinalized(identity.finalizedHash)
                .runtimeMetadataSha256(),
        )
    }

    @Test
    fun `live account balances decode to what the chain lets the account move`() = runBlocking {
        val identity = client.getFinalizedIdentity()
        val runtime = TestRuntimeSnapshots.build(
            metadataHex = client.getMetadataAtFinalized(identity.finalizedHash),
            typesJson = TestRuntimeSnapshots.packagedTypes,
            runtimeVersion = identity.runtimeVersion.specVersion,
        )
        val storage = runtime.metadata.module(Pallete.SYSTEM.palletName)
            .storage(Storage.ACCOUNT.storageName)
        var decoded = 0
        var locked = 0
        ACCOUNTS_WITH_LOCKS.forEach { accountId ->
            val key = storage.storageKey(runtime, accountId.fromHex())
            val raw = try {
                client.getStorageAt(key, identity.finalizedHash)
            } catch (error: Sora2RuntimeIdentityException) {
                // The account no longer exists; the others still prove the decoding.
                println("LIVE account ${accountId.take(12)} has no storage: ${error.message}")
                return@forEach
            }
            val account = decodeXorAccountData(runtime, storage, raw)
            val transferable = BalanceWrapper.calcTransferable(
                XorBalanceDto(
                    free = account.free,
                    reserved = account.reserved,
                    miscFrozen = account.miscFrozen,
                    feeFrozen = account.feeFrozen,
                    bonded = BigInteger.ZERO,
                    redeemable = BigInteger.ZERO,
                    unbonding = BigInteger.ZERO,
                )
            )

            // Independent reading of the same bytes: four u32, then free, reserved, frozen
            // as little-endian u128.
            val bytes = raw.fromHex()
            fun u128(index: Int) =
                BigInteger(1, bytes.copyOfRange(16 + 16 * index, 32 + 16 * index).reversedArray())
            val free = u128(0)
            val reserved = u128(1)
            val frozen = u128(2)
            val expected = (free - (frozen - reserved).max(BigInteger.ZERO)).max(BigInteger.ZERO)
            println(
                "LIVE account ${accountId.take(12)} free=$free reserved=$reserved " +
                    "frozen=$frozen transferable=$transferable"
            )
            assertEquals(free, account.free)
            assertEquals(reserved, account.reserved)
            assertEquals(expected, transferable)
            decoded += 1
            if (frozen > reserved) {
                locked += 1
                assertNotEquals(free, transferable)
            }
        }
        assertTrue("No sampled account could be read", decoded > 0)
        println("LIVE decoded=$decoded withLock=$locked")
    }

    @Test
    fun `status reads and old state are served through the two endpoints`() = runBlocking {
        val identity = client.getFinalizedIdentity()
        val absent = "0x${"00".repeat(31)}01"
        // A finalized block is read by number from an endpoint that has finalized it.
        val recent = client.getCanonicalBlock(
            blockNumber = identity.finalizedBlockNumber,
            finalizedHeight = identity.finalizedBlockNumber,
            recoveryHashes = setOf(absent),
        )
        assertEquals(identity.finalizedHash, recent.blockHash)
        assertTrue(recent.matchingExtrinsicIndices.isEmpty())

        // State from 2,000 blocks ago: the first endpoint has discarded it, the archive
        // endpoint answers. Before, this fall-back needed the first endpoint to say so.
        val old = client.getCanonicalBlock(
            blockNumber = identity.finalizedBlockNumber - 2_000,
            finalizedHeight = identity.finalizedBlockNumber,
            recoveryHashes = setOf(absent),
        )
        val events = client.getStorageAt(SYSTEM_EVENTS_KEY, old.blockHash)
        assertTrue(events.startsWith("0x") && events.length > 4)
        assertEquals(
            identity.runtimeVersion.specVersion > 0,
            client.getHistoricRuntimeVersionForStatus(old.blockHash).specVersion > 0,
        )
        println("LIVE old block ${old.blockNumber} events chars=${events.length}")
    }

    @Test
    fun `the remote type aliases arrive intact when the config host compresses them`() =
        runBlocking {
            // The client asks for gzip. The config CDN compresses, the nodes do not; either
            // way the text the app parses must be the reviewed file.
            val remote = http.getUtf8(REMOTE_TYPES_URL, 8 * 1024 * 1024)
            assertEquals(Sora2RuntimeContract.TYPES_FILE_SHA256, remote.rawSha256())
            assertEquals(TestRuntimeSnapshots.packagedTypes, remote)
        }

    @Test
    fun `nonce is read for an account through the production client`() = runBlocking {
        val address = requireNotNull(
            Sora2AddressCodec().toSoraAddressOrNull(ACCOUNTS_WITH_LOCKS.first().fromHex())
        )
        val nonce = client.getAccountNextIndex(address)
        assertTrue(nonce >= BigInteger.ZERO)
    }

    private companion object {
        val ENDPOINTS = listOf(
            Sora2RuntimeContract.RUNTIME_RPC_ENDPOINT,
            Sora2RuntimeContract.SECOND_RUNTIME_RPC_ENDPOINT,
        )

        /** `substrate_types_android` of the SORA remote config (config.polkaswap2.io). */
        const val REMOTE_TYPES_URL =
            "https://config.polkaswap2.io/prod/substrate_types_mobile_android.json"

        // twox128("System") + twox128("Events")
        const val SYSTEM_EVENTS_KEY =
            "0x26aa394eea5630e07c48ae0c9558cef780d41e5e16056765bc8461851072c9d7"

        // Public mainnet account ids that held locked XOR on 10 October 2026 (audit sample).
        // Their balances change; the test compares two readings of whatever is there now.
        val ACCOUNTS_WITH_LOCKS = listOf(
            "3cc8ecf73b2361bbbda88d737449c67efb3273e3fdf5042354858953aa086031",
            "a09c78e97aab69781d1c7100103a94c33a1015cffd6719a6bbedb1b6fc817774",
            "8ea9e59548d320651f8703a39f2fccbc806d0e8fc8e5db49c997e4773e8b3d38",
            "d08b33d0ed1f9a6d322f2e2ba9925c5e6a7882bbb1043d55a2158e1580e70c30",
        )
    }
}
