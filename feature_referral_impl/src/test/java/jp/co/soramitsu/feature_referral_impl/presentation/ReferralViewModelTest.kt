/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_referral_impl.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_referral_impl.domain.ReferralInteractor
import jp.co.soramitsu.feature_referral_impl.domain.model.Referral
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.getOrAwaitValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyInt
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class ReferralViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var interactor: ReferralInteractor

    @Mock
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var router: MainRouter

    @Mock
    private lateinit var progress: WithProgress

    @Mock
    private lateinit var resourceManager: ResourceManager

    private lateinit var referralViewModel: ReferralViewModel

    private val xorAsset = TestAssets.xorAsset()
    private val xorToken = TestAssets.xorAsset().token
    private val referrals = listOf(
        Referral("address", BigDecimal.ONE),
    )

    private val assetFlow = MutableSharedFlow<Asset>()
    private suspend fun assetFlowEmit(asset: Asset) = assetFlow.emit(asset)

    private val myReferrerFlow = MutableSharedFlow<String>()
    private suspend fun myReferrerFlowEmit(referrer: String) = myReferrerFlow.emit(referrer)

    private val referrerBalanceFlow = MutableSharedFlow<BigDecimal?>()
    private suspend fun referrerBalanceFlowEmit(b: BigDecimal) = referrerBalanceFlow.emit(b)

    @Before
    fun setUp() = runTest {
        given(walletInteractor.getFeeToken()).willReturn(xorToken)
        given(interactor.getInvitationLink()).willReturn("polkaswap/link")
        given(interactor.calcBondFee()).willReturn(BigDecimal("0.0035"))
        given(interactor.getReferrals()).willReturn(flow { emit(referrals) })
        given(walletInteractor.subscribeAssetOfCurAccount(SubstrateOptionsProvider.feeAssetId)).willReturn(
            assetFlow
        )
        given(interactor.observeMyReferrer()).willReturn(myReferrerFlow)
        given(interactor.observeReferrerBalance()).willReturn(referrerBalanceFlow)
        given(interactor.observeReferrals()).willReturn(flow { emit("") })
        given(interactor.getSetReferrerFee()).willReturn(BigDecimal("0.0035"))
        given(resourceManager.getString(anyInt())).willReturn("Some resource")
        assetFlowEmit(xorAsset)
        referralViewModel = ReferralViewModel(
            interactor,
            walletInteractor,
            NumbersFormatter(),
            router,
            progress,
            resourceManager
        )
        advanceUntilIdle()
    }

    @Test
    fun `initial screen`() = runTest {
        referrerBalanceFlowEmit(BigDecimal.ZERO)
        myReferrerFlowEmit("my referrer")
        advanceUntilIdle()
        val liveData = referralViewModel.referralScreenState.getOrAwaitValue(time = 4)
        assertTrue(liveData.screen is ReferralProgramStateScreen.Initial)
        assertEquals("0.0035 XOR", liveData.common.referrerFee)
        assertEquals("my referrer", liveData.common.referrer)
        verify(progress).showProgress()
        verify(progress).hideProgress()
        verify(interactor).updateReferrals()
    }

    @Test
    fun `initial screen bond xor no balance`() = runTest {
        referrerBalanceFlowEmit(BigDecimal.ZERO)
        myReferrerFlowEmit("my referrer")
        advanceUntilIdle()
        referralViewModel.onSheetOpen(DetailedBottomSheet.BOND)

        val liveData = referralViewModel.referralScreenState.getOrAwaitValue()
        assertTrue(liveData.screen is ReferralProgramStateScreen.Initial)
        assertEquals("0.0035 XOR", liveData.common.referrerFee)
        assertEquals("my referrer", liveData.common.referrer)
        assertEquals(1, liveData.bondState.invitationsCount)
        assertEquals("0.0035 XOR", liveData.bondState.invitationsAmount)
        assertEquals("0 XOR", liveData.bondState.balance)
        assertEquals(false, liveData.common.progress)
        assertEquals(false, liveData.common.activate)
    }

    @Test
    fun `initial screen bond xor`() = runTest {
        referrerBalanceFlowEmit(BigDecimal.ZERO)
        advanceUntilIdle()
        referralViewModel.onSheetOpen(DetailedBottomSheet.BOND)
        var state = referralViewModel.referralScreenState.getOrAwaitValue()
        assertTrue(state.screen is ReferralProgramStateScreen.Initial)
        assertEquals("0.0035 XOR", state.common.referrerFee)
        assertEquals(null, state.common.referrer)
        assertEquals(1, state.bondState.invitationsCount)
        assertEquals("0.0035 XOR", state.bondState.invitationsAmount)
        assertEquals("0 XOR", state.bondState.balance)
        assertEquals(false, state.common.progress)
        assertEquals(false, state.common.activate)

        assetFlowEmit(TestAssets.xorAsset(BigDecimal.TEN))
        state = referralViewModel.referralScreenState.getOrAwaitValue()
        assertEquals("10 XOR", state.bondState.balance)
        referralViewModel.onBondPlus()
        advanceUntilIdle()
        state = referralViewModel.referralScreenState.getOrAwaitValue()
        assertEquals(2, state.bondState.invitationsCount)
        assertEquals("0.007 XOR", state.bondState.invitationsAmount)
        assertEquals(false, state.common.progress)
        assertEquals(true, state.common.activate)

        given(interactor.observeBond(BigDecimal("0.0070"))).willReturn(true)
        referrerBalanceFlowEmit(BigDecimal("0.007"))
        referralViewModel.onBondButtonClick()
        advanceUntilIdle()
        state = referralViewModel.referralScreenState.getOrAwaitValue()
        assertEquals(false, state.common.activate)
        assertEquals(false, state.common.progress)
        assertTrue(state.screen is ReferralProgramStateScreen.ReferralProgramData)
        val screen = state.screen as ReferralProgramStateScreen.ReferralProgramData
        assertEquals(2, screen.invitations)
        assertEquals("polkaswap/link", screen.link)
        assertEquals("0.007 XOR", screen.bonded)
        assertEquals(1, screen.referrals?.rewards?.size)
        val event = referralViewModel.extrinsicEvent.getOrAwaitValue()
        assertEquals(true, event)
    }

    @Test
    fun `initial screen set referrer`() = runTest {
        referrerBalanceFlowEmit(BigDecimal.ZERO)
        advanceUntilIdle()

        var liveData = referralViewModel.referralScreenState.getOrAwaitValue()
        assertTrue(liveData.screen is ReferralProgramStateScreen.Initial)
        assertNull(liveData.common.referrer)
        referralViewModel.onSheetOpen(DetailedBottomSheet.REQUEST_REFERRER)
        liveData = referralViewModel.referralScreenState.getOrAwaitValue()
        assertEquals(false, liveData.common.activate)

        val link = "cnTheEarthSun"
        given(interactor.isLinkOk(link)).willReturn(true to link)
        referralViewModel.onLinkChange(link)
        advanceUntilIdle()
        liveData = referralViewModel.referralScreenState.getOrAwaitValue()
        assertEquals(true, liveData.common.activate)
        assertEquals(false, liveData.common.progress)

        given(interactor.observeSetReferrer(link)).willReturn(true)
        referralViewModel.onActivateLinkClick(link)
        liveData = referralViewModel.referralScreenState.getOrAwaitValue()
        assertEquals(false, liveData.common.progress)
        val hide = referralViewModel.hideSheet.first()
        assertEquals(true, hide)
        liveData = referralViewModel.referralScreenState.getOrAwaitValue()
        assertEquals(link, liveData.common.referrer)
        assertEquals(false, liveData.common.activate)
    }
}