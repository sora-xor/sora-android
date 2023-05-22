/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.test_data.PolkaswapTestData.POOL_DATA
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.domain.CardsHubInteractorImpl
import jp.co.soramitsu.feature_wallet_impl.domain.QrCodeDecoder
import jp.co.soramitsu.feature_wallet_impl.presentation.cardshub.CardsHubViewModel
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
import java.math.BigDecimal
import jp.co.soramitsu.oauth.common.domain.KycRepository
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import jp.co.soramitsu.test_data.SoraCardTestData
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Ignore

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
    private lateinit var qrCodeDecoder: QrCodeDecoder

    @Mock
    private lateinit var kycRepository: KycRepository

    @Mock
    private lateinit var connectionManager: ConnectionManager

    private val mockedUri = Mockito.mock(Uri::class.java)

    private lateinit var cardsHubViewModel: CardsHubViewModel

    private val account = SoraAccount("address", "name")

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
        //given(connectionManager.isConnected).willReturn(true)
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
            qrCodeDecoder,
            kycRepository,
            connectionManager,
        )
    }

    @Test
    fun `decode qr`() = runTest {
        val content = "qr_content"
        given(walletInteractor.processQr(content)).willReturn(
            Triple(
                "recipient",
                "asset",
                BigDecimal.ZERO
            )
        )
        cardsHubViewModel.qrResultProcess(content)
        advanceUntilIdle()
        verify(router).showValTransferAmount("recipient", "asset")
    }

    @Test
    fun `decode qr uri`() = runTest {
        given(qrCodeDecoder.decodeQrFromUri(mockedUri)).willReturn("content")
        given(walletInteractor.processQr("content")).willReturn(
            Triple(
                "recipient",
                "asset",
                BigDecimal.ZERO
            )
        )
        cardsHubViewModel.decodeTextFromBitmapQr(mockedUri)
        advanceUntilIdle()
        verify(router).showContactsFilled("asset", "recipient")
        verify(router).showValTransferAmount("recipient", "asset")
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