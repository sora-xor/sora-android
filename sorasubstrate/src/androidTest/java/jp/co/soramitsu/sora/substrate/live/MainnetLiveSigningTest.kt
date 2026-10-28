package jp.co.soramitsu.sora.substrate.live

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import java.math.BigInteger
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.io.FileManagerImpl
import jp.co.soramitsu.common.network.BoundedHttpTextClient
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Sora2BoundedRuntimeRpcClient
import jp.co.soramitsu.sora.substrate.runtime.Sora2RuntimeContract
import jp.co.soramitsu.sora.substrate.runtime.Sora2RuntimeRpcRemoteException
import jp.co.soramitsu.sora.substrate.runtime.requireSameSora2MutationRuntimeIdentity
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicBuilderFactory
import jp.co.soramitsu.sora.substrate.substrate.deriveSeed32
import jp.co.soramitsu.sora.substrate.substrate.transfer
import jp.co.soramitsu.xcrypto.seed.Mnemonic
import jp.co.soramitsu.xcrypto.seed.MnemonicCreator
import jp.co.soramitsu.xsubstrate.encrypt.EncryptionType
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.xsubstrate.encrypt.seed.substrate.SubstrateSeedFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import timber.log.Timber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * LIVE NETWORK TEST. Off by default; it never runs in CI or in a plain
 * `connectedProductionDebugAndroidTest`. Run it on an emulator with:
 *
 *     ANDROID_SERIAL=emulator-5580 ./gradlew :sorasubstrate:connectedProductionDebugAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.soraLiveNetwork=true \
 *       -Pandroid.testInstrumentationRunnerArguments.class=jp.co.soramitsu.sora.substrate.live.MainnetLiveSigningTest
 *
 * It proves, at zero cost, that the app signs for the runtime that SORA mainnet runs today. It
 * uses the app's own RuntimeManager, ExtrinsicBuilderFactory and bounded RPC client, and the
 * packaged native sr25519 library, with a throwaway key made from a new random phrase (never a
 * user key):
 *
 * 1. Build and sign a 1-planck XOR transfer from the throwaway account to itself with the live
 *    runtime context (spec version, transaction version and metadata read from the node).
 * 2. `payment_queryInfo` returns a fee for the signed bytes (through the reviewed HTTPS client,
 *    the same path the app's fee quote uses).
 * 3. `author_submitExtrinsic` is rejected with 1010 "Inability to pay some fees": the account has
 *    no XOR. The node checks the signature (which covers spec version, transaction version,
 *    genesis and era block) before fees, so this answer proves the signature is valid. A bad
 *    signature, outdated runtime or malformed call fails differently, and the test fails then.
 *    Nothing is ever included in a block.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MainnetLiveSigningTest {

    @Before
    fun requireLiveNetworkOptIn() {
        assumeTrue(
            "Live mainnet test skipped; pass the instrumentation argument $LIVE_ARGUMENT=true",
            InstrumentationRegistry.getArguments().getString(LIVE_ARGUMENT) == "true",
        )
        if (Timber.treeCount == 0) Timber.plant(Timber.DebugTree())
    }

    @Test
    fun throwawayAccountSignsTransferForTheLiveMainnetRuntime() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val gson = Gson()
        val json = Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        }
        val http = BoundedHttpTextClient()
        val preferences = SoraPreferences(context)
        val coroutineManager = CoroutineManager()
        val rpc = Sora2BoundedRuntimeRpcClient(http, gson, Sora2AddressCodec())
        // Since 3.9.0 the runtime, nonce, fee and submission all go through the reviewed HTTPS
        // endpoint; the app's WebSocket (whichever node it shows balances from) is not used.
        val runtimeManager = RuntimeManager(
            fileManager = FileManagerImpl(context),
            gson = gson,
            soraPreferences = preferences,
            sora2RuntimeRpcClient = rpc,
            boundedHttpTextClient = http,
            coroutineManager = coroutineManager,
            soraConfigManager = SoraConfigManager(json, http, preferences),
            recoveryRuntimeRetention = { emptySet() },
        )

        // Throwaway account, derived exactly like a new SORA wallet (sr25519, 32-byte
        // mini-seed, no junctions) from a phrase that exists only in this test run.
        val phrase = MnemonicCreator.randomMnemonic(Mnemonic.Length.TWELVE)
        val seed = SubstrateSeedFactory.deriveSeed32(phrase.words, null).seed
        val keypair = SubstrateKeypairFactory.generate(
            EncryptionType.SR25519,
            seed,
            emptyList(),
        ) as Sr25519Keypair
        val address = runtimeManager.toSoraAddress(keypair.publicKey)
        assertTrue(runtimeManager.isAddressOk(address))
        assertEquals(BigInteger.ZERO, rpc.getAccountNextIndex(address))

        val signingRuntime = runtimeManager.getMutationRuntimeContext()
        assertEquals(
            Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH,
            signingRuntime.genesisHash,
        )
        Log.i(
            TAG,
            "live runtime spec=${signingRuntime.runtimeVersion.specVersion} " +
                "tx=${signingRuntime.runtimeVersion.transactionVersion} " +
                "metadataSha256=${signingRuntime.metadataSha256} " +
                "typesSha256=${signingRuntime.typesSha256} " +
                "finalized=${signingRuntime.finalizedBlockNumber}",
        )

        val builder = ExtrinsicBuilderFactory(rpc)
            .createForSigning(address, keypair, signingRuntime)
            .builder
        builder.transfer(assetId = XOR_ASSET_ID, to = address, amount = BigInteger.ONE)
        val signed = builder.build(useBatchAll = false)

        // (a) The node decodes the signed bytes and quotes a fee (payment_queryInfo).
        val fee = rpc.queryPartialFee(signed)
        assertTrue("fee must be positive, was $fee", fee > BigInteger.ZERO)
        Log.i(TAG, "payment_queryInfo partialFee=$fee")

        // Same pre-transport identity check the app runs before every submission.
        requireSameSora2MutationRuntimeIdentity(
            signing = signingRuntime,
            current = runtimeManager.getMutationRuntimeContext(),
        )

        // (b) author_submitExtrinsic through the app's bounded HTTPS client.
        val rejection = try {
            rpc.submitExtrinsicOnce(signed)
            fail("A throwaway account with no XOR must not be able to pay the fee")
            return@runBlocking
        } catch (error: Sora2RuntimeRpcRemoteException) {
            error
        }
        val reason = rejection.remoteData.orEmpty()
        Log.i(
            TAG,
            "author_submitExtrinsic rejected code=${rejection.code} " +
                "message=${rejection.remoteMessage} data=$reason",
        )
        assertEquals(INVALID_TRANSACTION, rejection.code)
        assertTrue(
            "expected only an inability to pay fees, got: $reason",
            reason.contains("Inability to pay some fees", ignoreCase = true),
        )
        WRONG_SIGNATURE_OR_RUNTIME.forEach { marker ->
            assertFalse("rejected for $marker: $reason", reason.contains(marker, ignoreCase = true))
        }
    }

    /**
     * Status recovery of a submission that is days old: the reviewed node keeps only about 256
     * blocks of state, so System events and the signing runtime of an older block come from the
     * status archive endpoint, at the exact block hash the reviewed node reports.
     */
    @Test
    fun statusReadsForOldBlocksAreAnsweredByTheArchiveEndpoint() = runBlocking {
        val gson = Gson()
        val http = BoundedHttpTextClient()
        val rpc = Sora2BoundedRuntimeRpcClient(http, gson, Sora2AddressCodec())
        val live = rpc.getFinalizedIdentity()
        val oldHeight = live.finalizedBlockNumber - OLD_BLOCK_DISTANCE
        val oldBlock = rpc.getCanonicalBlock(
            blockNumber = oldHeight,
            finalizedHeight = live.finalizedBlockNumber,
            recoveryHashes = setOf("0x${"ab".repeat(32)}"),
        )

        val version = rpc.getHistoricRuntimeVersionForStatus(oldBlock.blockHash)
        val metadata = rpc.getHistoricMetadataForStatus(oldBlock.blockHash)
        val events = rpc.getStorageAt(SYSTEM_EVENTS_STORAGE_KEY, oldBlock.blockHash)
        Log.i(
            TAG,
            "archive read at #$oldHeight: spec=${version.specVersion} " +
                "tx=${version.transactionVersion} metadataBytes=${metadata.length / 2} " +
                "eventsBytes=${events.length / 2}",
        )
        assertTrue(version.specVersion > 0)
        assertTrue(metadata.startsWith("0x6d657461"))
        assertTrue(events.startsWith("0x"))
    }

    private companion object {
        const val TAG = "SoraLiveSigning"
        const val LIVE_ARGUMENT = "soraLiveNetwork"
        const val INVALID_TRANSACTION = 1010L
        const val OLD_BLOCK_DISTANCE = 20_000L
        const val SYSTEM_EVENTS_STORAGE_KEY =
            "0x26aa394eea5630e07c48ae0c9558cef780d41e5e16056765bc8461851072c9d7"
        const val XOR_ASSET_ID =
            "0x0200000000000000000000000000000000000000000000000000000000000000"
        val WRONG_SIGNATURE_OR_RUNTIME = listOf(
            "bad signature",
            "BadProof",
            "outdated",
            "stale",
            "ancient",
            "invalid format",
            "future",
        )
    }
}
