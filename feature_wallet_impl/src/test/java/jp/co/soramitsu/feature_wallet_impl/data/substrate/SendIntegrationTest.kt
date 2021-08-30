package jp.co.soramitsu.feature_wallet_impl.data.substrate

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocketFactory
import jp.co.soramitsu.common.data.network.substrate.Constants
import jp.co.soramitsu.common.data.network.substrate.Pallete
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromByteArrayOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.scale
import jp.co.soramitsu.fearless_utils.wsrpc.recovery.Reconnector
import jp.co.soramitsu.fearless_utils.wsrpc.request.RequestExecutor
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.substrate.AccountData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigInteger

private const val MY_ADDRESS = "5GLphDhkjfuSuxmsCTxWsyUM7mJxRG2FVk5dgVArD857ZH5M"
private const val PUBLIC_KEY = "bd3be03f90f94cfa04890fc22e7d469cf3942e7233782c4079d8fab33ba16855"
private const val PRIVATE_KEY = "fef2663ea72d68472cb2938842424de0a6945418b5ef96f1f75594e666853521"
private const val TO_ADDRESS = "5CaY7JMUpN7uFad6ZzwMGnZGwrZ5XLthENtSm2BbfXXCvBur"

private const val GENESIS = "f5ded4eac940310dd22ab394288c7598fa92a82c4e5a82f742e443856bab0072"
private const val URL = "wss://ws.framenode-1.s1.dev.sora2.soramitsu.co.jp"

@ExperimentalCoroutinesApi
@ExperimentalUnsignedTypes
@Ignore("Manual run only")
@RunWith(MockitoJUnitRunner::class)
class SendIntegrationTest {
    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    private val gsonMapper = Gson()
    private lateinit var socketService: SocketService

    @Before
    fun setup() {
        socketService = SocketService(gsonMapper, object : Logger {
            override fun log(message: String?) {
                println("socket log=$message")
            }

            override fun log(throwable: Throwable?) {
                println("socket log err=${throwable?.message}")
            }
        }, WebSocketFactory(), Reconnector(), RequestExecutor())

        socketService.start(URL)
    }

    @After
    fun tearDown() {
        socketService.stop()
    }

    @Test
    fun `check prefix byte`() {
        val runtime = TestRuntimeProvider.buildRuntime("sora2")
        val valueConstant =
            runtime.metadata.module(Pallete.SYSTEM.palleteName).constants[Constants.SS58Prefix.constantName]
        val prefix =
            (valueConstant?.type?.fromByteArrayOrNull(
                runtime,
                valueConstant.value
            ) as? BigInteger)?.toInt()
                ?: 69
        assertEquals(prefix, 42)
    }

    @Test
    fun `storage assets_assetInfos`() = runBlockingTest {
        val runtime = TestRuntimeProvider.buildRuntime("sora2")

        val address = "5EcDoG4T1SLbop4bxBjLL9VJaaytZxGXA7mLaY9y84GYpzsR".toAccountId()
        val toke = "0x0200040000000000000000000000000000000000000000000000000000000000".fromHex()
        val storageKey = runtime.metadata.module("Assets").storage("AssetInfos").storageKey()
        val storageKey123 = runtime.metadata.module("Assets").storage("AssetInfos").storageKey()
        val stotoac =
            runtime.metadata.module("Tokens").storage("Accounts").storageKey(runtime, address, toke)
        val stora = runtime.metadata.module("Assets").storage("AssetInfos").type.value
        val r = socketService.executeAsync(
            request = RuntimeRequest(
                "state_getStorage",
                listOf(
                    stotoac
                )
            ),
            mapper = scale(AccountData).nonNull()
        )
//        val qwe = stora?.fromHexOrNull(runtime, r[0].changes[0][1]!!)
//        val ss = ((qwe as? List<*>)?.get(0) as? ByteArray)?.toString(Charsets.UTF_8)
//        val sss = ((qwe as? List<*>)?.get(1) as? ByteArray)?.toString(Charsets.UTF_8)
        val free = r[AccountData.free]
        assertTrue(free == BigInteger.ONE)
    }
}

data class Qwwe(val block: String, val changes: List<List<String>>)
