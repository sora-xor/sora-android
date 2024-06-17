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

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.config.BuildConfigWrapper
import jp.co.soramitsu.common.domain.CardHub
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_referral_api.ReferralRouter
import jp.co.soramitsu.feature_sora_card_api.domain.SoraCardInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.domain.CardsHubInteractorImpl
import jp.co.soramitsu.feature_wallet_impl.presentation.cardshub.CardsHubViewModel
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import jp.co.soramitsu.test_data.PolkaswapTestData.POOL_DATA
import jp.co.soramitsu.test_data.SoraCardTestData.soraCardBasicStatusTest
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

@ExperimentalCoroutinesApi
class CardsHubViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var assetsInteractor: AssetsInteractor

    @MockK
    private lateinit var poolsInteractor: PoolsInteractor

    @MockK
    private lateinit var cardsHubInteractorImpl: CardsHubInteractorImpl

    @MockK
    private lateinit var soraCardInteractor: SoraCardInteractor

    private val numbersFormatter = NumbersFormatter()

    @MockK
    private lateinit var progress: WithProgress

    @MockK
    private lateinit var resourceManager: ResourceManager

    @MockK
    private lateinit var assetsRouter: AssetsRouter

    @MockK
    private lateinit var referralRouter: ReferralRouter

    @MockK
    private lateinit var router: WalletRouter

    @MockK
    private lateinit var mainRouter: MainRouter

    @MockK
    private lateinit var polkaswapRouter: PolkaswapRouter

    @MockK
    private lateinit var coroutineManager: CoroutineManager

    @MockK
    private lateinit var connectionManager: ConnectionManager

    @MockK
    private lateinit var demeterFarmingInteractor: DemeterFarmingInteractor

    private lateinit var cardsHubViewModel: CardsHubViewModel

    private val account = SoraAccount("address", "name")

    @OptIn(ExperimentalStdlibApi::class)
    @Before
    fun setUp() = runTest {
        mockkObject(BuildConfigWrapper)
        every { BuildConfigWrapper.getSoraCardBackEndUrl() }.returns("soracard backend")
        every { connectionManager.isConnected } returns true
        mockkObject(OptionsProvider)
        every { OptionsProvider.header } returns "test android client"
        every { assetsInteractor.subscribeAssetsFavoriteOfAccount(account) } returns
            flow {
                emit(listOf(TestAssets.xorAsset(), TestAssets.valAsset()))
            }
        every { poolsInteractor.subscribePoolsCacheOfAccount(account) } returns
            flow {
                emit(listOf(POOL_DATA))
            }
        coEvery { cardsHubInteractorImpl.updateCardVisibilityOnCardHub(any(), any()) } returns Unit
        coEvery { demeterFarmingInteractor.getFarmedPools() } returns emptyList()
        every { demeterFarmingInteractor.subscribeFarms(any()) } returns flow { emit("") }
        every { cardsHubInteractorImpl.subscribeVisibleCardsHubList() } returns
            flow {
                emit(
                    account to listOf(
                        CardHub(
                            CardHubType.ASSETS,
                            true,
                            0,
                            false
                        ),
                        CardHub(
                            CardHubType.POOLS,
                            true,
                            1,
                            false
                        ),
                        CardHub(
                            CardHubType.GET_SORA_CARD,
                            visibility = true,
                            sortOrder = 2,
                            collapsed = false
                        )
                    )
                )
            }
        every { coroutineManager.io } returns this.coroutineContext[CoroutineDispatcher]!!
        every {
            soraCardInteractor.basicStatus
        } returns MutableStateFlow(soraCardBasicStatusTest.copy(initialized = true))
        every { assetsRouter.showBuyCrypto(any()) } returns Unit
        every { mainRouter.showGetSoraCard(any()) } just Runs
        every { mainRouter.showSoraCardDetails() } just Runs
        every {
            resourceManager.getString(R.string.sora_card_verification_in_progress)
        } returns "in progress"
        every { resourceManager.getString(R.string.sora_card_verification_successful) } returns "success"
        every { resourceManager.getString(R.string.sora_card_verification_rejected) } returns "rejected"
        every { resourceManager.getString(R.string.sora_card_verification_failed) } returns "failed"
        cardsHubViewModel = CardsHubViewModel(
            assetsInteractor,
            poolsInteractor,
            cardsHubInteractorImpl,
            demeterFarmingInteractor,
            numbersFormatter,
            resourceManager,
            router,
            mainRouter,
            assetsRouter,
            referralRouter,
            polkaswapRouter,
            connectionManager,
            soraCardInteractor,
            coroutineManager,
        )
    }

    @Test
    fun `connection buy`() = runTest {
        cardsHubViewModel.onBuyCrypto()
        verify { assetsRouter.showBuyCrypto(any()) }
    }

    @Test
    fun `call remove get sora card EXPECT change card visibility`() = runTest {
        cardsHubViewModel.onRemoveSoraCard()
        advanceUntilIdle()
        coVerify {
            cardsHubInteractorImpl.updateCardVisibilityOnCardHub(
                CardHubType.GET_SORA_CARD.hubName,
                visible = false,
            )
        }
    }

    @Test
    fun `call remove buy xor token card EXPECT change card visibility`() = runTest {
        cardsHubViewModel.onRemoveBuyXorToken()
        advanceUntilIdle()
        coVerify {
            cardsHubInteractorImpl.updateCardVisibilityOnCardHub(
                CardHubType.BUY_XOR_TOKEN.hubName,
                visible = false,
            )
        }
    }

    @Test
    fun `call onCardStateClicked EXPECT induce launchSoraCard event`() = runTest {
        advanceUntilIdle()
        cardsHubViewModel.onCardStateClicked()
        advanceUntilIdle()
        verify { mainRouter.showGetSoraCard(any(), any()) }
    }
}
