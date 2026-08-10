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
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.androidfoundation.testing.getOrAwaitValue
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.config.BuildConfigWrapper
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.CardHub
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.common.domain.DarkThemeManager
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_referral_api.ReferralRouter
import jp.co.soramitsu.feature_sora_card_api.domain.SoraCardAvailabilityInfo
import jp.co.soramitsu.feature_sora_card_api.domain.SoraCardInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioBalance
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioRepository
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusTransactionCoordinator
import jp.co.soramitsu.feature_wallet_impl.domain.CardsHubInteractorImpl
import jp.co.soramitsu.feature_wallet_impl.presentation.cardshub.CardsHubViewModel
import jp.co.soramitsu.oauth.base.sdk.contract.IbanInfo
import jp.co.soramitsu.oauth.base.sdk.contract.IbanStatus
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardFlow
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import jp.co.soramitsu.test_data.PolkaswapTestData.POOL_DATA
import jp.co.soramitsu.test_data.SoraCardTestData.soraCardBasicStatusTest
import jp.co.soramitsu.test_data.TestAssets
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @MockK
    private lateinit var darkThemeManager: DarkThemeManager

    @MockK
    private lateinit var nexusPortfolioRepository: NexusPortfolioRepository

    @MockK
    private lateinit var nexusTransactionCoordinator: NexusTransactionCoordinator

    private lateinit var cardsHubViewModel: CardsHubViewModel

    private val account = SoraAccount("address", "name")
    private val secondAccount = SoraAccount("second-address", "second-name")
    private lateinit var selectedAccountFlow: MutableStateFlow<SoraAccount>
    private lateinit var firstXorFlow: MutableStateFlow<Asset?>
    private lateinit var secondXorFlow: MutableStateFlow<Asset?>
    private lateinit var nexusPortfolioFlow: MutableStateFlow<List<NexusPortfolioBalance>>

    @OptIn(ExperimentalStdlibApi::class)
    @Before
    fun setUp() = runTest {
        mockkObject(BuildConfigWrapper)
        every { BuildConfigWrapper.getSoraCardBackEndUrl() }.returns("soracard backend")
        every { connectionManager.isConnected } returns true
        nexusPortfolioFlow = MutableStateFlow(emptyList())
        every { nexusPortfolioRepository.observeCurrentWallet() } returns nexusPortfolioFlow
        mockkObject(OptionsProvider)
        every { OptionsProvider.header } returns "test android client"
        selectedAccountFlow = MutableStateFlow(account)
        firstXorFlow = MutableStateFlow(
            TestAssets.xorAsset(BigDecimal("1.25")).copy(favorite = false)
        )
        secondXorFlow = MutableStateFlow(null)
        every { assetsInteractor.flowCurSoraAccount() } returns selectedAccountFlow
        every {
            assetsInteractor.subscribeAssetOfAccount(
                account,
                SubstrateOptionsProvider.feeAssetId,
            )
        } returns firstXorFlow
        every {
            assetsInteractor.subscribeAssetOfAccount(
                secondAccount,
                SubstrateOptionsProvider.feeAssetId,
            )
        } returns secondXorFlow
        every { assetsInteractor.subscribeAssetsFavoriteOfAccount(account) } returns
            flow {
                // XOR is deliberately not favorite: the unified network row
                // must be sourced from the account-bound authoritative asset.
                emit(listOf(TestAssets.valAsset()))
            }
        every { poolsInteractor.subscribePoolsCacheOfAccount(account) } returns
            flow {
                emit(listOf(POOL_DATA))
            }
        coEvery { cardsHubInteractorImpl.updateCardVisibilityOnCardHub(any(), any()) } returns Unit
        coEvery { demeterFarmingInteractor.getFarmedPools() } returns emptyList()
        every { demeterFarmingInteractor.subscribeFarms(any()) } returns flow { emit("") }
        every { darkThemeManager.darkModeStatusFlow } returns MutableStateFlow(true)
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
        } returns MutableStateFlow(
            soraCardBasicStatusTest.copy(
                initialized = true,
                availabilityInfo = SoraCardAvailabilityInfo(),
                ibanInfo = IbanInfo("iban", IbanStatus.ACTIVE, "123", "empty")
            )
        )
        every { assetsRouter.showBuyCrypto(any()) } returns Unit
        every { assetsRouter.showAssetDetails(any()) } returns Unit
        every { router.openQrCodeFlow() } returns Unit
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
            darkThemeManager,
            nexusPortfolioRepository,
            nexusTransactionCoordinator,
        )
    }

    @Test
    fun `connection buy`() = runTest {
        cardsHubViewModel.onBuyCrypto()
        val live = cardsHubViewModel.launchSoraCardSignIn.getOrAwaitValue()
        assertTrue(live.flow is SoraCardFlow.SoraCardGateHubFlow)
    }

    @Test
    fun `SORA2 portfolio keeps XOR row when XOR is not favorite`() = runTest {
        advanceUntilIdle()

        val sora2 = cardsHubViewModel.sora2Portfolio.value
        assertNotNull(sora2)
        assertEquals(account.substrateAddress, sora2?.address)
        assertEquals(
            firstXorFlow.value?.balance?.transferable
                ?.stripTrailingZeros()
                ?.toPlainString(),
            sora2?.xorQuantity,
        )
    }

    @Test
    fun `SORA2 account switch never pairs address with another wallet balance`() = runTest {
        advanceUntilIdle()
        assertEquals(
            account.substrateAddress to "1.25",
            cardsHubViewModel.sora2Portfolio.value?.let {
                it.address to it.xorQuantity
            },
        )

        selectedAccountFlow.value = secondAccount
        advanceUntilIdle()

        assertEquals(
            secondAccount.substrateAddress,
            cardsHubViewModel.sora2Portfolio.value?.address,
        )
        assertNull(cardsHubViewModel.sora2Portfolio.value?.xorQuantity)

        secondXorFlow.value =
            TestAssets.xorAsset(BigDecimal("9.5")).copy(favorite = false)
        advanceUntilIdle()

        assertEquals(
            secondAccount.substrateAddress to "9.5",
            cardsHubViewModel.sora2Portfolio.value?.let {
                it.address to it.xorQuantity
            },
        )
    }

    @Test
    fun `account switch dismisses previous wallet Nexus confirmation`() = runTest {
        val previousWalletBalance = NexusPortfolioBalance(
            walletId = account.substrateAddress,
            networkId = WalletNetworkId.MINAMOTO,
            networkName = "Minamoto",
            isTestnet = false,
            address = "previous-wallet-minamoto-address",
            xorQuantity = "1",
            assetDefinitionId = CANONICAL_XOR_DEFINITION_ID,
            explorerBaseUrl = "https://minamoto-explorer.sora.org",
            sendAvailable = true,
            pendingTransactions = emptyList(),
            confirmedTransfers = emptyList(),
            historyErrorCode = null,
            errorCode = null,
        )
        nexusPortfolioFlow.value = listOf(previousWalletBalance)
        advanceUntilIdle()

        cardsHubViewModel.openNexusSend(previousWalletBalance)
        assertTrue(cardsHubViewModel.nexusSend.value.visible)

        selectedAccountFlow.value = secondAccount
        advanceUntilIdle()

        assertFalse(cardsHubViewModel.nexusSend.value.visible)
        assertNull(cardsHubViewModel.nexusSend.value.network)
    }

    @Test
    fun `Nexus send entry rejects a row from another wallet`() = runTest {
        advanceUntilIdle()
        val otherWalletBalance = NexusPortfolioBalance(
            walletId = secondAccount.substrateAddress,
            networkId = WalletNetworkId.TAIRA,
            networkName = "Taira Testnet",
            isTestnet = true,
            address = "other-wallet-taira-address",
            xorQuantity = "1",
            assetDefinitionId = CANONICAL_XOR_DEFINITION_ID,
            explorerBaseUrl = "https://taira-explorer.sora.org",
            sendAvailable = true,
            pendingTransactions = emptyList(),
            confirmedTransfers = emptyList(),
            historyErrorCode = null,
            errorCode = null,
        )

        cardsHubViewModel.openNexusSend(otherWalletBalance)

        assertFalse(cardsHubViewModel.nexusSend.value.visible)
        assertNull(cardsHubViewModel.nexusSend.value.network)
    }

    @Test
    fun `Nexus preparation uses immutable row wallet while cards state lags`() = runTest {
        advanceUntilIdle()
        selectedAccountFlow.value = secondAccount
        advanceUntilIdle()
        val secondWalletBalance = nexusBalance(
            walletId = secondAccount.substrateAddress,
            networkId = WalletNetworkId.MINAMOTO,
        )
        nexusPortfolioFlow.value = listOf(secondWalletBalance)
        coEvery { nexusTransactionCoordinator.prepare(any()) } throws
            IllegalStateException("EXPECTED_STOP")
        advanceUntilIdle()

        cardsHubViewModel.openNexusSend(secondWalletBalance)
        cardsHubViewModel.setNexusRecipient("second-wallet-recipient")
        cardsHubViewModel.setNexusAmount("1")
        cardsHubViewModel.prepareNexusSend()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            nexusTransactionCoordinator.prepare(
                match {
                    it.walletId == secondAccount.substrateAddress &&
                        it.networkId == WalletNetworkId.MINAMOTO
                }
            )
        }
        assertEquals("EXPECTED_STOP", cardsHubViewModel.nexusSend.value.errorCode)
    }

    @Test
    fun `account switch cancels in-flight Nexus preparation without stale UI write`() = runTest {
        advanceUntilIdle()
        val balance = nexusBalance(
            walletId = account.substrateAddress,
            networkId = WalletNetworkId.MINAMOTO,
        )
        nexusPortfolioFlow.value = listOf(balance)
        val preparationStarted = CompletableDeferred<Unit>()
        val preparationCancelled = CompletableDeferred<Unit>()
        coEvery { nexusTransactionCoordinator.prepare(any()) } coAnswers {
            preparationStarted.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                preparationCancelled.complete(Unit)
            }
        }
        advanceUntilIdle()

        cardsHubViewModel.openNexusSend(balance)
        cardsHubViewModel.setNexusRecipient("recipient")
        cardsHubViewModel.setNexusAmount("1")
        cardsHubViewModel.prepareNexusSend()
        preparationStarted.await()

        selectedAccountFlow.value = secondAccount
        advanceUntilIdle()

        assertTrue(preparationCancelled.isCompleted)
        assertFalse(cardsHubViewModel.nexusSend.value.visible)
        assertNull(cardsHubViewModel.nexusSend.value.network)
        assertNull(cardsHubViewModel.nexusSend.value.prepared)
        assertNull(cardsHubViewModel.nexusSend.value.errorCode)
    }

    @Test
    fun `removing Taira row invalidates receive and send actions`() = runTest {
        advanceUntilIdle()
        val taira = nexusBalance(
            walletId = account.substrateAddress,
            networkId = WalletNetworkId.TAIRA,
        )
        nexusPortfolioFlow.value = listOf(taira)
        advanceUntilIdle()

        assertTrue(cardsHubViewModel.isCurrentNexusBalance(taira))
        cardsHubViewModel.openNexusSend(taira)
        assertTrue(cardsHubViewModel.nexusSend.value.visible)

        // This is the observable result when Test Networks is disabled: the
        // repository removes Taira immediately, before any action can run.
        nexusPortfolioFlow.value = emptyList()
        advanceUntilIdle()

        assertFalse(cardsHubViewModel.isCurrentNexusBalance(taira))
        assertFalse(cardsHubViewModel.nexusSend.value.visible)
        cardsHubViewModel.openNexusSend(taira)
        assertFalse(cardsHubViewModel.nexusSend.value.visible)
    }

    @Test
    fun `SORA2 actions reject a stale wallet row at tap time`() = runTest {
        advanceUntilIdle()
        selectedAccountFlow.value = secondAccount
        advanceUntilIdle()

        assertFalse(cardsHubViewModel.isCurrentSora2Wallet(account.substrateAddress))
        cardsHubViewModel.openSora2Receive(account.substrateAddress)
        cardsHubViewModel.openSora2Xor(account.substrateAddress)
        verify(exactly = 0) { router.openQrCodeFlow() }
        verify(exactly = 0) {
            assetsRouter.showAssetDetails(SubstrateOptionsProvider.feeAssetId)
        }

        cardsHubViewModel.openSora2Receive(secondAccount.substrateAddress)
        cardsHubViewModel.openSora2Xor(secondAccount.substrateAddress)
        verify(exactly = 1) { router.openQrCodeFlow() }
        verify(exactly = 1) {
            assetsRouter.showAssetDetails(SubstrateOptionsProvider.feeAssetId)
        }
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
        verify { mainRouter.showSoraCardDetails() }
    }

    private fun nexusBalance(
        walletId: String,
        networkId: WalletNetworkId,
    ) = NexusPortfolioBalance(
        walletId = walletId,
        networkId = networkId,
        networkName = if (networkId == WalletNetworkId.TAIRA) "Taira Testnet" else "Minamoto",
        isTestnet = networkId == WalletNetworkId.TAIRA,
        address = "$walletId-${networkId.wireId}-address",
        xorQuantity = "1",
        assetDefinitionId = CANONICAL_XOR_DEFINITION_ID,
        explorerBaseUrl = "https://example.invalid",
        sendAvailable = true,
        pendingTransactions = emptyList(),
        confirmedTransfers = emptyList(),
        historyErrorCode = null,
        errorCode = null,
    )

    private companion object {
        const val CANONICAL_XOR_DEFINITION_ID = "6TEAJqbb8oEPmLncoNiMRbLEK6tw"
    }
}
