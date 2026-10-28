/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.CardHub
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.nexus.WalletNetworkAction
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common_wallet.presentation.compose.states.FavoritePoolsCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.ReferralState
import jp.co.soramitsu.common_wallet.presentation.compose.states.TitledAmountCardState
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_referral_api.ReferralRouter
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioBalance
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioRepository
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusTransactionCoordinator
import jp.co.soramitsu.feature_wallet_impl.domain.CardsHubInteractorImpl
import jp.co.soramitsu.feature_wallet_impl.presentation.cardshub.CardsHubViewModel
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.test_data.PolkaswapTestData.POOL_DATA
import jp.co.soramitsu.test_data.TestAssets
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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

    private val numbersFormatter = NumbersFormatter()

    @MockK
    private lateinit var progress: WithProgress

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
    private lateinit var demeterFarmingInteractor: DemeterFarmingInteractor

    @MockK
    private lateinit var nexusPortfolioRepository: NexusPortfolioRepository

    @MockK
    private lateinit var nexusTransactionCoordinator: NexusTransactionCoordinator

    private lateinit var cardsHubViewModel: CardsHubViewModel
    private lateinit var poolsReadFailure: MutableStateFlow<Throwable?>

    private val account = SoraAccount("address", "name")
    private val secondAccount = SoraAccount("second-address", "second-name")
    private lateinit var selectedAccountFlow: MutableStateFlow<SoraAccount>
    private lateinit var firstXorFlow: MutableStateFlow<Asset?>
    private lateinit var secondXorFlow: MutableStateFlow<Asset?>
    private lateinit var nexusPortfolioFlow: MutableStateFlow<List<NexusPortfolioBalance>>
    private lateinit var cardsHubFlow: MutableStateFlow<Pair<SoraAccount, List<CardHub>>>

    @OptIn(ExperimentalStdlibApi::class)
    @Before
    fun setUp() = runTest {
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
        poolsReadFailure = MutableStateFlow(null)
        every { poolsInteractor.subscribePoolsCacheOfAccount(account) } returns
            poolsReadFailure.map { failure ->
                failure?.let { throw it }
                listOf(POOL_DATA)
            }
        coEvery { cardsHubInteractorImpl.updateCardVisibilityOnCardHub(any(), any()) } returns Unit
        // Three accounts on this phone, one of them read-only.
        coEvery { cardsHubInteractorImpl.accountSigningCounts() } returns (3 to 2)
        coEvery { demeterFarmingInteractor.getFarmedPools() } returns emptyList()
        every { demeterFarmingInteractor.subscribeFarms(any()) } returns flow { emit("") }
        cardsHubFlow = MutableStateFlow(
            account to listOf(
                CardHub(CardHubType.ASSETS, visibility = true, sortOrder = 0, collapsed = false),
                CardHub(CardHubType.POOLS, visibility = true, sortOrder = 1, collapsed = false),
                CardHub(CardHubType.REFERRAL_SYSTEM, visibility = true, sortOrder = 2, collapsed = false),
            ),
        )
        every { cardsHubInteractorImpl.subscribeVisibleCardsHubList() } returns cardsHubFlow
        every { coroutineManager.io } returns this.coroutineContext[CoroutineDispatcher]!!
        cardsHubViewModel = CardsHubViewModel(
            assetsInteractor,
            poolsInteractor,
            cardsHubInteractorImpl,
            demeterFarmingInteractor,
            numbersFormatter,
            router,
            mainRouter,
            referralRouter,
            polkaswapRouter,
            coroutineManager,
            nexusPortfolioRepository,
            nexusTransactionCoordinator,
        )
    }

    @Test
    fun `the upgrade sheet is given how many accounts can really sign`() = runTest {
        advanceUntilIdle()
        assertEquals(3, cardsHubViewModel.state.value.accountCount)
        assertEquals(2, cardsHubViewModel.state.value.signingAccountCount)
    }

    @Test
    fun `the home builds no Assets card, only the cards around the portfolio`() = runTest {
        advanceUntilIdle()
        val cards = cardsHubViewModel.state.value.cards
        assertEquals(2, cards.size)
        assertTrue((cards[0] as TitledAmountCardState).state is FavoritePoolsCardState)
        assertEquals(ReferralState, cards[1])
    }

    @Test
    fun `hiding every optional card clears the old cards even with a saved Assets preference`() = runTest {
        advanceUntilIdle()
        assertTrue(cardsHubViewModel.state.value.cards.isNotEmpty())

        cardsHubFlow.value = account to cardsHubFlow.value.second.map {
            it.copy(visibility = it.cardType == CardHubType.ASSETS)
        }
        advanceUntilIdle()

        assertTrue(cardsHubViewModel.state.value.cards.isEmpty())
        assertEquals(account.substrateAddress, cardsHubViewModel.state.value.accountAddress)
    }

    @Test
    fun `failed downstream holdings read clears previous cards without crashing`() = runTest {
        advanceUntilIdle()
        assertTrue(cardsHubViewModel.state.value.cards.isNotEmpty())
        io.mockk.mockkObject(jp.co.soramitsu.common.logger.FirebaseWrapper)
        every { jp.co.soramitsu.common.logger.FirebaseWrapper.recordException(any()) } just Runs
        try {
            poolsReadFailure.value = IllegalStateException("PI_INDEXER_GRAPHQL_ERROR")
            advanceUntilIdle()
            assertTrue(cardsHubViewModel.state.value.cards.isEmpty())
        } finally { io.mockk.unmockkObject(jp.co.soramitsu.common.logger.FirebaseWrapper) }
    }

    @Test
    fun `cancelled downstream holdings read is not converted to unavailable state`() = runTest {
        advanceUntilIdle()
        val before = cardsHubViewModel.state.value
        io.mockk.mockkObject(jp.co.soramitsu.common.logger.FirebaseWrapper)
        every { jp.co.soramitsu.common.logger.FirebaseWrapper.recordException(any()) } just Runs
        try {
            poolsReadFailure.value = kotlinx.coroutines.CancellationException("stop display subscription")
            advanceUntilIdle()
            assertEquals(before, cardsHubViewModel.state.value)
            io.mockk.verify(exactly = 0) { jp.co.soramitsu.common.logger.FirebaseWrapper.recordException(any()) }
        } finally { io.mockk.unmockkObject(jp.co.soramitsu.common.logger.FirebaseWrapper) }
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
    fun `network choices list only the selected wallet main network first`() = runTest {
        advanceUntilIdle()
        val taira = nexusBalance(account.substrateAddress, WalletNetworkId.TAIRA)
        val nexus = nexusBalance(account.substrateAddress, WalletNetworkId.MINAMOTO)
            .copy(networkLive = false, xorQuantity = null, sendAvailable = false)
        val otherWallet = nexusBalance(secondAccount.substrateAddress, WalletNetworkId.TAIRA)
        nexusPortfolioFlow.value = listOf(taira, otherWallet, nexus)
        advanceUntilIdle()

        val receive = cardsHubViewModel.networkChoices(WalletNetworkAction.RECEIVE)
        assertEquals(
            listOf(WalletNetworkId.SORA2, WalletNetworkId.MINAMOTO, WalletNetworkId.TAIRA),
            receive.map { it.id },
        )
        assertEquals(account.substrateAddress, receive.first().address)
        assertEquals(taira.address, receive.last().address)
        assertTrue(receive.all { it.isAvailable })

        val send = cardsHubViewModel.networkChoices(WalletNetworkAction.SEND)
        assertEquals(listOf(true, false, true), send.map { it.isAvailable })

        // Another wallet's rows never become choices.
        selectedAccountFlow.value = secondAccount
        advanceUntilIdle()
        assertTrue(cardsHubViewModel.networkChoices(WalletNetworkAction.RECEIVE).none { it.address == taira.address })
    }

    @Test
    fun `call remove referral card EXPECT change card visibility`() = runTest {
        cardsHubViewModel.onRemoveReferralCard()
        advanceUntilIdle()
        coVerify {
            cardsHubInteractorImpl.updateCardVisibilityOnCardHub(
                CardHubType.REFERRAL_SYSTEM.hubName,
                visible = false,
            )
        }
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
