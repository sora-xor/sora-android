package jp.co.soramitsu.feature_wallet_impl.data.substrate

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.gson.Gson
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.feature_wallet_impl.data.network.substrate.SubstrateApiImpl
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

    @Mock
    private lateinit var socket: SocketService

    @Mock
    private lateinit var crypto: CryptoAssistant

    private lateinit var api: SubstrateApiImpl

    @Before
    fun setUp() {
        api = SubstrateApiImpl(socket, crypto)
    }
}
