/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_referral_impl.presentation

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.ui.text.input.TextFieldValue
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkStatic
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.common.R
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_referral_impl.domain.ReferralInteractor
import jp.co.soramitsu.feature_referral_impl.domain.model.Referral
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.getOrAwaitValue
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
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
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify as kVerify
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

    private val mockedUri = Mockito.mock(Uri::class.java)

    @Mock
    private lateinit var assetsInteractor: AssetsInteractor

    @Mock
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var assetsRouter: AssetsRouter

    @Mock
    private lateinit var router: MainRouter

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

    private lateinit var expectedToolbarState: SoramitsuToolbarState

    @Before
    fun setUp() = runTest {
        mockkStatic(Uri::parse)
        every { Uri.parse(any()) } returns mockedUri
        given(walletInteractor.getFeeToken()).willReturn(xorToken)
        given(interactor.getInvitationLink()).willReturn("polkaswap/link")
        given(interactor.calcBondFee()).willReturn(BigDecimal("0.07"))
        given(assetsInteractor.subscribeAssetOfCurAccount(SubstrateOptionsProvider.feeAssetId)).willReturn(
            assetFlow
        )

        given(interactor.observeMyReferrer()).willReturn(myReferrerFlow)
        given(interactor.observeReferrerBalance()).willReturn(referrerBalanceFlow)
        given(interactor.getSetReferrerFee()).willReturn(BigDecimal("0.07"))
        given(resourceManager.getString(R.string.referral_no_available_invitations)).willReturn("No available invitations")
        given(resourceManager.getString(R.string.referral_referral_link)).willReturn("Referrer’s link or address")
        assetFlowEmit(xorAsset)
    }

    private fun setupViewModel() {
        referralViewModel = ReferralViewModel(
            assetsInteractor,
            assetsRouter,
            interactor,
            walletInteractor,
            NumbersFormatter(),
            router,
            resourceManager,
        )

        expectedToolbarState = SoramitsuToolbarState(
            type = SoramitsuToolbarType.Small(),
            basic = BasicToolbarState(
                title = "Referral program",
                navIcon = null,
            ),
        )
    }

    @Test
    fun `initial screen`() = runTest {
        given(interactor.getReferrals()).willReturn(flow { emit(referrals) })
        given(interactor.observeReferrals()).willReturn(flow { emit("") })
        setupViewModel()
        advanceUntilIdle()
        referrerBalanceFlowEmit(BigDecimal.ZERO)
        myReferrerFlowEmit("my referrer")
        advanceUntilIdle()
        val actualToolbarState = referralViewModel.toolbarState.getOrAwaitValue()
        assertTrue(actualToolbarState.type is SoramitsuToolbarType.Small)
        assertEquals(R.string.referral_toolbar_title, actualToolbarState.basic.title)
        val actualScreenState = referralViewModel.referralScreenState
        assertEquals("my referrer", actualScreenState.common.referrer)
        verify(interactor).updateReferrals()
    }

    @Test
    fun `no data welcome screen`() = runTest {
        given(interactor.getReferrals()).willReturn(emptyFlow())
        given(interactor.observeReferrals()).willReturn(emptyFlow())
        setupViewModel()
        advanceUntilIdle()
        val toolbar = referralViewModel.toolbarState.getOrAwaitValue()
        assertEquals(R.string.referral_toolbar_title, toolbar.basic.title)
        assertTrue(toolbar.type is SoramitsuToolbarType.Small)
        referrerBalanceFlowEmit(BigDecimal.ZERO)
        advanceUntilIdle()
        val state = referralViewModel.referralScreenState
        assertNull(state.common.referrer)
        var navEvent = referralViewModel.navEvent.getOrAwaitValue()
        assertEquals(ReferralFeatureRoutes.WELCOME_PAGE, navEvent.first)
        given(interactor.isLinkOrAddressOk("")).willReturn(false to "")
        referralViewModel.openReferrerInput()
        advanceUntilIdle()
        assertEquals(false, referralViewModel.referralScreenState.common.activate)
        assertEquals(false, referralViewModel.referralScreenState.common.progress)
        given(interactor.isLinkOrAddressOk("cnVko")).willReturn(true to "cnVko")
        referralViewModel.onReferrerInputChange(TextFieldValue("cnVko"))
        advanceUntilIdle()
        assertEquals(true, referralViewModel.referralScreenState.common.activate)
        assertEquals("cnVko", referralViewModel.referralScreenState.referrerInputState.value.text)
        given(interactor.observeSetReferrer("cnVko")).willReturn("txhash")
        referralViewModel.onActivateLinkClick()
        advanceUntilIdle()
        navEvent = referralViewModel.navEvent.getOrAwaitValue()
        assertEquals(ReferralFeatureRoutes.WELCOME_PAGE, navEvent.first)
    }

    @Test
    fun `onDestinationChanged() called`() = runTest {
        given(interactor.getReferrals()).willReturn(flow { emit(referrals) })
        given(interactor.observeReferrals()).willReturn(flow { emit("") })
        setupViewModel()
        advanceUntilIdle()
        referralViewModel.onCurrentDestinationChanged(ReferralFeatureRoutes.WELCOME_PAGE)
        val actualToolbarState2 = referralViewModel.toolbarState.getOrAwaitValue()
        assertTrue(actualToolbarState2.type is SoramitsuToolbarType.Small)
        assertEquals(R.string.referral_toolbar_title, actualToolbarState2.basic.title)
    }

    @Test
    fun `WHEN invitations count is changed EXPECT transaction reminder is checked`() =
        runTest {
            given(
                interactor.getReferrals()
            ).willReturn(
                flow {
                    emit(referrals)
                }
            )

            given(
                interactor.observeReferrals()
            ).willReturn(emptyFlow())

            given(
                assetsInteractor.subscribeAssetOfCurAccount(
                    SubstrateOptionsProvider.feeAssetId
                )
            ).willReturn(
                flow {
                    emit(TestAssets.xorAsset())
                }
            )

            setupViewModel()

            advanceUntilIdle()

            referralViewModel.onBondPlus()

            advanceUntilIdle()

            kVerify(
                mock = assetsInteractor,
                mode = atLeastOnce()
            ).isEnoughXorLeftAfterTransaction(
                primaryToken = any(),
                primaryTokenAmount = any(),
                secondaryToken = eq(null),
                secondaryTokenAmount = eq(null),
                networkFeeInXor = any()
            )

            referralViewModel.onBondMinus()

            advanceUntilIdle()

            kVerify(
                mock = assetsInteractor,
                mode = atLeastOnce()
            ).isEnoughXorLeftAfterTransaction(
                primaryToken = any(),
                primaryTokenAmount = any(),
                secondaryToken = eq(null),
                secondaryTokenAmount = eq(null),
                networkFeeInXor = any()
            )
        }
}