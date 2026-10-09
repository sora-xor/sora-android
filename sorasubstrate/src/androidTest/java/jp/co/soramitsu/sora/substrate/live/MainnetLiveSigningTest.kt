package jp.co.soramitsu.sora.substrate.live

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocketFactory
import java.math.BigInteger
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.data.network.connection.NetworkStateListener
import jp.co.soramitsu.common.domain.AppStateProvider
import jp.co.soramitsu.common.io.FileManagerImpl
import jp.co.soramitsu.common.network.BoundedHttpTextClient
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Sora2BoundedRuntimeRpcClient
import jp.co.soramitsu.sora.substrate.runtime.Sora2RuntimeContract
import jp.co.soramitsu.sora.substrate.runtime.Sora2RuntimeRpcRemoteException
import jp.co.soramitsu.sora.substrate.runtime.requireSameSora2MutationRuntimeIdentity
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicBuilderFactory
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.sora.substrate.substrate.WsConnectionManager
import jp.co.soramitsu.sora.substrate.substrate.WsLogger
import jp.co.soramitsu.sora.substrate.substrate.deriveSeed32
import jp.co.soramitsu.sora.substrate.substrate.transfer
import jp.co.soramitsu.xcrypto.seed.Mnemonic
import jp.co.soramitsu.xcrypto.seed.MnemonicCreator
import jp.co.soramitsu.xsubstrate.encrypt.EncryptionType
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.xsubstrate.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.xsubstrate.wsrpc.SocketService
import jp.co.soramitsu.xsubstrate.wsrpc.recovery.ConstantReconnectStrategy
import jp.co.soramitsu.xsubstrate.wsrpc.recovery.Reconnector
import jp.co.soramitsu.xsubstrate.wsrpc.request.RequestExecutor
import jp.co.soramitsu.xsubstrate.wsrpc.state.SocketStateMachine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import timber.log.Timber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
 * uses the app's own RuntimeManager, ExtrinsicBuilderFactory, SubstrateCalls and bounded RPC
 * client, and the packaged native sr25519 library, with a throwaway key made from a new random
 * phrase (never a user key):
 *
 * 1. Build and sign a 1-planck XOR transfer from the throwaway account to itself with the live
 *    runtime context (spec version, transaction version and metadata read from the node).
 * 2. `payment_queryInfo` returns a fee for the signed bytes.
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
        val socket = SocketService(
            gson,
            WsLogger(),
            WebSocketFactory(),
            Reconnector(strategy = ConstantReconnectStrategy(step = 10_000L)),
            RequestExecutor(),
        )
        val connection = WsConnectionManager(
            socket,
            NoLifecycleAppState,
            coroutineManager,
            NetworkStateListener(),
            context,
        )
        socket.addStateObserver { state: SocketStateMachine.State ->
            Log.i(TAG, "socket state ${state.javaClass.simpleName}")
        }
        // Same order as NodeManagerImpl: select the node, then observe the socket.
        connection.setAddress(Sora2RuntimeContract.RUNTIME_WS_ENDPOINT)
        connection.observeAppState()
        socket.start(Sora2RuntimeContract.RUNTIME_WS_ENDPOINT)
        try {
            withTimeout(CONNECT_TIMEOUT_MILLIS) { connection.connectionState.first { it } }

            val runtimeManager = RuntimeManager(
                fileManager = FileManagerImpl(context),
                gson = gson,
                soraPreferences = preferences,
                sora2RuntimeRpcClient = rpc,
                connectionManager = connection,
                boundedHttpTextClient = http,
                coroutineManager = coroutineManager,
                soraConfigManager = SoraConfigManager(json, http, preferences),
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
            val fee = SubstrateCalls(socket, runtimeManager, connection).getExtrinsicFee(signed)
            assertNotNull("payment_queryInfo returned no fee", fee)
            assertTrue("fee must be positive, was $fee", checkNotNull(fee) > BigInteger.ZERO)
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
        } finally {
            socket.stop()
        }
    }

    private object NoLifecycleAppState : AppStateProvider {
        override val isForeground: Boolean = true
        override val isBackground: Boolean = false
        override fun observeState() = emptyFlow<AppStateProvider.AppEvent>()
    }

    private companion object {
        const val TAG = "SoraLiveSigning"
        const val LIVE_ARGUMENT = "soraLiveNetwork"
        const val CONNECT_TIMEOUT_MILLIS = 60_000L
        const val INVALID_TRANSACTION = 1010L
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
