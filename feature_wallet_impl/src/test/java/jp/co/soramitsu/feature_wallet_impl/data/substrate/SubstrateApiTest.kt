package jp.co.soramitsu.feature_wallet_impl.data.substrate

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.gson.Gson
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.feature_wallet_impl.data.network.substrate.SubstrateApiImpl
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
@Ignore("no tests")
class SubstrateApiTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Rule
    @JvmField
    val rxSchedulerRule = RxSchedulersRule()

    @Mock
    private lateinit var socket: SocketService

    @Mock
    private lateinit var crypto: CryptoAssistant

    @Mock
    private lateinit var gson: Gson

    @Mock
    private lateinit var runtime: RuntimeSnapshot

    private lateinit var api: SubstrateApiImpl

    @Before
    fun setUp() {
        api = SubstrateApiImpl(socket, crypto, gson)
    }
}
