package jp.co.soramitsu.sora.substrate.live

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocketFactory
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.io.FileManagerImpl
import jp.co.soramitsu.common.network.BoundedHttpTextClient
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Sora2BoundedRuntimeRpcClient
import jp.co.soramitsu.sora.substrate.runtime.Sora2RuntimeContract
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.sora.substrate.substrate.WsLogger
import jp.co.soramitsu.xsubstrate.wsrpc.SocketService
import jp.co.soramitsu.xsubstrate.wsrpc.recovery.ConstantReconnectStrategy
import jp.co.soramitsu.xsubstrate.wsrpc.recovery.Reconnector
import jp.co.soramitsu.xsubstrate.wsrpc.request.RequestExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * LIVE NETWORK TEST. Off by default, like [MainnetLiveSigningTest]. Run it on an emulator with:
 *
 *     ANDROID_SERIAL=emulator-5838 ./gradlew :sorasubstrate:connectedProductionDebugAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.soraLiveNetwork=true \
 *       -Pandroid.testInstrumentationRunnerArguments.class=jp.co.soramitsu.sora.substrate.live.MainnetLiveBalanceReadTest
 *
 * The Portfolio view reads wallets that are not selected by address, read-only, with the app's own
 * [SubstrateCalls]. This reads real balances that way from the SORA2 mainnet, for an address that
 * is nobody's wallet: the reserves of a Polkaswap XYK pool (public chain data). No key is involved
 * and nothing is submitted.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MainnetLiveBalanceReadTest {

    @Before
    fun requireLiveNetworkOptIn() {
        assumeTrue(
            "Live mainnet test skipped; pass the instrumentation argument $LIVE_ARGUMENT=true",
            InstrumentationRegistry.getArguments().getString(LIVE_ARGUMENT) == "true",
        )
    }

    @Test
    fun poolReservesReadThroughTheAppsOwnReader() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val gson = Gson()
        val json = Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        }
        val http = BoundedHttpTextClient()
        val preferences = SoraPreferences(context)
        val runtimeManager = RuntimeManager(
            fileManager = FileManagerImpl(context),
            gson = gson,
            soraPreferences = preferences,
            sora2RuntimeRpcClient = Sora2BoundedRuntimeRpcClient(http, gson, Sora2AddressCodec()),
            boundedHttpTextClient = http,
            coroutineManager = CoroutineManager(),
            soraConfigManager = SoraConfigManager(json, http, preferences),
            recoveryRuntimeRetention = { emptySet() },
        )
        val socket = SocketService(
            gson,
            WsLogger(),
            WebSocketFactory(),
            Reconnector(strategy = ConstantReconnectStrategy(step = 10_000L)),
            RequestExecutor(),
        )
        socket.start(Sora2RuntimeContract.RUNTIME_WS_ENDPOINT)
        try {
            val calls = SubstrateCalls(socket, runtimeManager, ReadOnlyConnection)
            val (xor, tokens) = withTimeout(READ_TIMEOUT_MS) {
                calls.fetchXORBalances(POOL_RESERVES) to calls.fetchBalances(POOL_RESERVES, TOKENS)
            }
            val free = mapBalance(xor.free, 18)
            Log.i(TAG, "XOR free=$free, tokens=${tokens.map { mapBalance(it, 18) }}")
            // A liquidity pool's reserves hold XOR, and what they hold is read token by token.
            assertTrue("XOR of the pool reserves: $free", free > BigDecimal.ZERO)
            assertEquals(TOKENS.size, tokens.size)
            assertTrue("tokens of the pool reserves: $tokens", tokens.all { it >= BigInteger.ZERO })
        } finally {
            socket.stop()
        }
    }

    /** The reads never ask the connection for a signing transport. */
    private object ReadOnlyConnection : ConnectionManager by unavailableConnection()

    private companion object {
        const val TAG = "PortfolioLiveRead"
        const val LIVE_ARGUMENT = "soraLiveNetwork"
        const val READ_TIMEOUT_MS = 120_000L

        /** The reserves account of a Polkaswap XYK pool: a technical account, not a person's wallet. */
        const val POOL_RESERVES = "cnTQ1kbv7PBNNQrEb1tZpmK7jT6zmAjZB72t6j7HHmgtiMKgB"

        val TOKENS = listOf(
            "0x0200040000000000000000000000000000000000000000000000000000000000", // VAL
            "0x0200050000000000000000000000000000000000000000000000000000000000", // PSWAP
            "0x0200060000000000000000000000000000000000000000000000000000000000", // DAI
            "0x0200070000000000000000000000000000000000000000000000000000000000", // ETH
        )
    }
}

private fun unavailableConnection(): ConnectionManager =
    java.lang.reflect.Proxy.newProxyInstance(
        ConnectionManager::class.java.classLoader,
        arrayOf(ConnectionManager::class.java),
    ) { _, method, _ -> throw UnsupportedOperationException(method.name) } as ConnectionManager
