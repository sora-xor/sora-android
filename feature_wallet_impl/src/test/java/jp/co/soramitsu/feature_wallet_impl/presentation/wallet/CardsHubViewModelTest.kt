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

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.CardHub
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.domain.CardsHubInteractorImpl
import jp.co.soramitsu.common_wallet.domain.QrCodeDecoder
import jp.co.soramitsu.feature_wallet_impl.presentation.cardshub.CardsHubViewModel
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import jp.co.soramitsu.test_data.PolkaswapTestData.POOL_DATA
import jp.co.soramitsu.test_data.SoraCardTestData
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.times
import java.math.BigDecimal

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class CardsHubViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var assetsInteractor: AssetsInteractor

    @Mock
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var poolsInteractor: PoolsInteractor

    @Mock
    private lateinit var cardsHubInteractorImpl: CardsHubInteractorImpl

    @Mock
    private lateinit var numbersFormatter: NumbersFormatter

    @Mock
    private lateinit var progress: WithProgress

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var assetsRouter: AssetsRouter

    @Mock
    private lateinit var router: WalletRouter

    @Mock
    private lateinit var mainRouter: MainRouter

    @Mock
    private lateinit var polkaswapRouter: PolkaswapRouter

    @Mock
    private lateinit var coroutineManager: CoroutineManager

    @Mock
    private lateinit var connectionManager: ConnectionManager

    private val mockedUri = Mockito.mock(Uri::class.java)

    private lateinit var cardsHubViewModel: CardsHubViewModel

    private val account = SoraAccount("address", "name")

    @OptIn(ExperimentalStdlibApi::class)
    @Before
    fun setUp() = runTest {
        mockkStatic(Uri::parse)
        every { Uri.parse(any()) } returns mockedUri
        mockkStatic(Token::iconUri)
        every { TestTokens.xorToken.iconUri() } returns mockedUri
        every { TestTokens.valToken.iconUri() } returns mockedUri
        every { TestTokens.pswapToken.iconUri() } returns mockedUri
        every { TestTokens.xstusdToken.iconUri() } returns mockedUri
        every { TestTokens.xstToken.iconUri() } returns mockedUri
        given(connectionManager.isConnected).willReturn(true)
        mockkObject(OptionsProvider)
        every { OptionsProvider.header } returns "test android client"
        given(assetsInteractor.subscribeAssetsFavoriteOfAccount(account)).willReturn(
            flow {
                emit(listOf(TestAssets.xorAsset(), TestAssets.valAsset()))
            }
        )
        given(poolsInteractor.subscribePoolsCacheOfAccount(account)).willReturn(
            flow {
                emit(listOf(POOL_DATA))
            }
        )
        given(cardsHubInteractorImpl.subscribeVisibleCardsHubList()).willReturn(
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
        )
        given(cardsHubInteractorImpl.subscribeSoraCardInfo()).willReturn(flowOf(SoraCardTestData.SORA_CARD_INFO))
        given(coroutineManager.io).willReturn(this.coroutineContext[CoroutineDispatcher]!!)
        given(walletInteractor.pollSoraCardStatusIfPending()).willReturn(
            flowOf("")
        )
        cardsHubViewModel = CardsHubViewModel(
            assetsInteractor,
            walletInteractor,
            poolsInteractor,
            cardsHubInteractorImpl,
            numbersFormatter,
            progress,
            resourceManager,
            router,
            mainRouter,
            assetsRouter,
            polkaswapRouter,
            connectionManager,
            coroutineManager,
        )
    }

    @Test
    fun `connection buy`() = runTest {
        cardsHubViewModel.onBuyCrypto()
        verify(assetsRouter, times(1)).showBuyCrypto()
    }

    @Test
    fun `call remove get sora card EXPECT change card visibility`() = runTest {
        cardsHubViewModel.onRemoveSoraCard()
        advanceUntilIdle()

        verify(cardsHubInteractorImpl).updateCardVisibilityOnCardHub(
            CardHubType.GET_SORA_CARD.hubName,
            visible = false
        )
    }

    @Test
    fun `call remove buy xor token card EXPECT change card visibility`() = runTest {
        cardsHubViewModel.onRemoveBuyXorToken()
        advanceUntilIdle()

        verify(cardsHubInteractorImpl).updateCardVisibilityOnCardHub(
            CardHubType.BUY_XOR_TOKEN.hubName,
            visible = false
        )
    }

    @Test
    fun `call updateSoraCardInfo EXPECT update data via interactor`() = runTest {
        cardsHubViewModel.updateSoraCardInfo(
            accessToken = "accessToken",
            refreshToken = "refreshToken",
            accessTokenExpirationTime = Long.MAX_VALUE,
            kycStatus = "Completed"
        )
        advanceUntilIdle()

        verify(walletInteractor).updateSoraCardInfo(
            accessToken = "accessToken",
            refreshToken = "refreshToken",
            accessTokenExpirationTime = Long.MAX_VALUE,
            kycStatus = "Completed"
        )
    }

    @Ignore
    @Test
    fun `call onCardStateClicked EXPECT induce launchSoraCard event`() {
        cardsHubViewModel.onCardStateClicked()

        assertEquals(SoraCardTestData.SORA_CARD_CONTRACT_DATA, cardsHubViewModel.launchSoraCardSignIn.value)
    }
}
