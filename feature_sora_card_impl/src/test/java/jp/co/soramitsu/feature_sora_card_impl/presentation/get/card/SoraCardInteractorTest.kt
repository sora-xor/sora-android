package jp.co.soramitsu.feature_sora_card_impl.presentation.get.card

import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_sora_card_api.domain.SoraCardInteractor
import jp.co.soramitsu.feature_sora_card_impl.domain.SoraCardClientProxy
import jp.co.soramitsu.feature_sora_card_impl.domain.SoraCardInteractorImpl
import jp.co.soramitsu.oauth.base.sdk.contract.IbanInfo
import jp.co.soramitsu.oauth.base.sdk.contract.IbanStatus
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardCommonVerification
import jp.co.soramitsu.test_data.PolkaswapTestData.POOL_DATA
import jp.co.soramitsu.test_data.TestAssets
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.wheneverBlocking

@RunWith(MockitoJUnitRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SoraCardInteractorTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var ai: AssetsInteractor

    @Mock
    private lateinit var pi: PoolsInteractor

    @Mock
    private lateinit var sccp: SoraCardClientProxy

    @Mock
    private lateinit var dfi: DemeterFarmingInteractor

    @Mock
    private lateinit var cm: CoroutineManager

    private lateinit var soraCardInteractor: SoraCardInteractor

    @OptIn(ExperimentalStdlibApi::class)
    @Before
    fun setUp() = runTest {
        `when`(cm.io).thenReturn(this.coroutineContext[CoroutineDispatcher]!!)
        wheneverBlocking { sccp.init() } doReturn (true to "")
        wheneverBlocking { sccp.getVersion() } doReturn Result.success("2.3.4")
        wheneverBlocking { sccp.getApplicationFee() } doReturn "$8.98"
        wheneverBlocking {
            sccp.getKycStatus()
        } doReturn Result.success(SoraCardCommonVerification.Successful)
        wheneverBlocking { ai.subscribeAssetOfCurAccount(any()) } doReturn flowOf(TestAssets.xorAsset())
        wheneverBlocking { pi.getPoolsCacheOfCurAccount() } doReturn listOf(POOL_DATA)
        wheneverBlocking { dfi.getStakedFarmedBalanceOfAsset(any()) } doReturn BigDecimal.ONE
        soraCardInteractor = SoraCardInteractorImpl(
            assetsInteractor = ai,
            poolsInteractor = pi,
            soraCardClientProxy = sccp,
            demeterFarmingInteractor = dfi,
            coroutineManager = cm,
        )
    }

    @Test
    fun `test init with full data`() = runTest {
        wheneverBlocking { sccp.getIBAN() } doReturn Result.success(
            IbanInfo(
                "iban",
                IbanStatus.ACTIVE,
                "$12.34",
                "ok",
            )
        )
        wheneverBlocking { sccp.getPhone() } doReturn "+123"
        advanceUntilIdle()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            soraCardInteractor.initialize()
        }
        advanceTimeBy(1100.milliseconds)
        val bs = soraCardInteractor.basicStatus.value
        assertTrue(bs.initialized)
    }

    @Test
    fun `test call status set`() = runTest {
        wheneverBlocking { sccp.getIBAN() } doReturn Result.success(null)
        wheneverBlocking { sccp.getPhone() } doReturn "+123"
        advanceUntilIdle()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            soraCardInteractor.initialize()
        }
        advanceTimeBy(1100.milliseconds)
        var bs = soraCardInteractor.basicStatus.value
        assertTrue(bs.initialized)
        soraCardInteractor.setStatus(SoraCardCommonVerification.Pending)
        advanceUntilIdle()
        bs = soraCardInteractor.basicStatus.value
        assertTrue(bs.initialized)
    }
}
