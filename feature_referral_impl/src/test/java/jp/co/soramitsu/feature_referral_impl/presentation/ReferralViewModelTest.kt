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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import io.mockk.mockkStatic
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
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
import java.math.BigDecimal

@ExperimentalCoroutinesApi
class ReferralViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var interactor: ReferralInteractor

    private val mockedUri = mockk<Uri>()

    @MockK
    private lateinit var assetsInteractor: AssetsInteractor

    @MockK
    private lateinit var walletInteractor: WalletInteractor

    @MockK
    private lateinit var assetsRouter: AssetsRouter

    @MockK
    private lateinit var router: MainRouter

    @MockK
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
        coEvery { walletInteractor.getFeeToken() } returns xorToken
        coEvery { interactor.getInvitationLink() } returns "polkaswap/link"
        coEvery { interactor.calcBondFee() } returns BigDecimal("0.07")
        coEvery { interactor.updateReferrals() } returns Unit
        every { assetsInteractor.subscribeAssetOfCurAccount(SubstrateOptionsProvider.feeAssetId) } returns assetFlow
        coEvery {
            assetsInteractor.isNotEnoughXorLeftAfterTransaction(
                any(),
                any(),
            )
        } returns false

        every { interactor.observeMyReferrer() } returns myReferrerFlow
        every { interactor.observeReferrerBalance() } returns referrerBalanceFlow
        coEvery { interactor.getSetReferrerFee() } returns BigDecimal("0.07")
        every { resourceManager.getString(R.string.referral_no_available_invitations) } returns "No available invitations"
        every { resourceManager.getString(R.string.referral_referral_link) } returns "Referrer’s link or address"
        every { resourceManager.getString(R.string.referral_invitaion_link_title) } returns "Referrer’s link or address"
        every { resourceManager.getString(R.string.referral_invitation_link) } returns "Referrer’s link or address"
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
        coEvery { interactor.getReferrals() } returns flow { emit(referrals) }
        every { interactor.getReferrals() } returns emptyFlow()
        coEvery { interactor.observeReferrals() } returns flow { emit("") }
        setupViewModel()
        advanceUntilIdle()
        referrerBalanceFlowEmit(BigDecimal.ZERO)
        myReferrerFlowEmit("my referrer")
        advanceUntilIdle()
        val actualToolbarState = referralViewModel.toolbarState.getOrAwaitValue()
        assertTrue(actualToolbarState.type is SoramitsuToolbarType.Small)
        assertEquals(R.string.referral_toolbar_title, actualToolbarState.basic.title)
        val actualScreenState = referralViewModel.referralScreenState.value
        assertEquals("my referrer", actualScreenState.common.referrer)
        coVerify { interactor.updateReferrals() }
    }

    @Test
    fun `no data welcome screen`() = runTest {
        every { interactor.getReferrals() } returns emptyFlow()
        every { interactor.observeReferrals() } returns emptyFlow()
        setupViewModel()
        advanceUntilIdle()
        val toolbar = referralViewModel.toolbarState.getOrAwaitValue()
        assertEquals(R.string.referral_toolbar_title, toolbar.basic.title)
        assertTrue(toolbar.type is SoramitsuToolbarType.Small)
        referrerBalanceFlowEmit(BigDecimal.ZERO)
        advanceUntilIdle()
        val state = referralViewModel.referralScreenState.value
        assertNull(state.common.referrer)
        var navEvent = referralViewModel.navEvent.getOrAwaitValue()
        assertEquals(ReferralFeatureRoutes.WELCOME_PAGE, navEvent.first)
        coEvery { interactor.isLinkOrAddressOk("") } returns (false to "")
        referralViewModel.openReferrerInput()
        advanceUntilIdle()
        assertEquals(false, referralViewModel.referralScreenState.value.common.activate)
        assertEquals(false, referralViewModel.referralScreenState.value.common.progress)
        coEvery { interactor.isLinkOrAddressOk("cnVko") } returns (true to "cnVko")
        referralViewModel.onReferrerInputChange(TextFieldValue("cnVko"))
        advanceUntilIdle()
        assertEquals(true, referralViewModel.referralScreenState.value.common.activate)
        assertEquals("cnVko", referralViewModel.referralScreenState.value.referrerInputState.value.text)
        coEvery { interactor.observeSetReferrer("cnVko") } returns "txhash"
        every { assetsRouter.showTxDetails(any(), any()) } returns Unit
        referralViewModel.onActivateLinkClick()
        advanceUntilIdle()
        navEvent = referralViewModel.navEvent.getOrAwaitValue()
        assertEquals(ReferralFeatureRoutes.WELCOME_PAGE, navEvent.first)
    }

    @Test
    fun `onDestinationChanged() called`() = runTest {
        coEvery { interactor.getReferrals() } returns flow { emit(referrals) }
        coEvery { interactor.observeReferrals() } returns flow { emit("") }
        setupViewModel()
        advanceUntilIdle()
        referralViewModel.onCurrentDestinationChanged(ReferralFeatureRoutes.WELCOME_PAGE)
        val actualToolbarState2 = referralViewModel.toolbarState.getOrAwaitValue()
        assertTrue(actualToolbarState2.type is SoramitsuToolbarType.Small)
        assertEquals(R.string.referral_toolbar_title, actualToolbarState2.basic.title)
    }

    @Test
    fun `WHEN invitations count is changed EXPECT transaction reminder is checked`() = runTest {
        every { interactor.getReferrals() } returns flow { emit(referrals) }
        every { interactor.observeReferrals() } returns emptyFlow()
        every { assetsInteractor.subscribeAssetOfCurAccount(SubstrateOptionsProvider.feeAssetId) } returns flow {
            emit(
                TestAssets.xorAsset()
            )
        }
        setupViewModel()
        advanceUntilIdle()
        referralViewModel.onBondPlus()
        advanceUntilIdle()
        coVerify(atLeast = 1) {
            assetsInteractor.isNotEnoughXorLeftAfterTransaction(
                any(),
                any(),
            )
        }
        referralViewModel.onBondMinus()
        advanceUntilIdle()
        coVerify(atLeast = 1) {
            assetsInteractor.isNotEnoughXorLeftAfterTransaction(
                any(),
                any(),
            )
        }
    }
}