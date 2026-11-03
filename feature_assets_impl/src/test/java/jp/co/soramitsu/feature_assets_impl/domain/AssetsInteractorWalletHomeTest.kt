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

package jp.co.soramitsu.feature_assets_impl.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common_wallet.data.XorAssetBalance
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * What the classic Wallet home and its asset screens ask of the interactor: the selected
 * account's asset lists, the Edit view's changes and the balance reads, each for the account
 * that is selected now.
 */
@ExperimentalCoroutinesApi
class AssetsInteractorWalletHomeTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var assetsRepository: AssetsRepository

    @MockK
    private lateinit var transactionHistoryRepository: TransactionHistoryRepository

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var credentialsRepository: CredentialsRepository

    @MockK
    private lateinit var builder: TransactionBuilder

    @MockK
    private lateinit var coroutineManager: CoroutineManager

    private lateinit var interactor: AssetsInteractor

    // The public BIP39 test phrase's SORA address.
    private val address = "cnTon6cV8Ze8ATHT1tZdHregCc3wBc8vfg7P5o2TtwdUoRf4m"
    private val soraAccount = SoraAccount(address, "Compare")
    private val xorId = TestTokens.xorToken.id
    private val valId = TestTokens.valToken.id

    @Before
    fun setUp() {
        coEvery { userRepository.getCurSoraAccount() } returns soraAccount
        every { userRepository.flowCurSoraAccount() } returns flowOf(soraAccount)
        interactor = AssetsInteractorImpl(
            assetsRepository = assetsRepository,
            credentialsRepository = credentialsRepository,
            coroutineManager = coroutineManager,
            transactionBuilder = builder,
            transactionHistoryRepository = transactionHistoryRepository,
            userRepository = userRepository,
        )
    }

    @Test
    fun `the home's asset lists follow the selected account`() = runTest {
        val visible = listOf(TestAssets.xorAsset(), TestAssets.valAsset())
        every { assetsRepository.subscribeAssetsVisible(address) } returns flowOf(visible)
        every { assetsRepository.subscribeAssetsFavorite(address) } returns flowOf(visible.take(1))
        every { assetsRepository.subscribeAsset(address, xorId) } returns flowOf(visible.first())

        assertEquals(visible, interactor.subscribeAssetsVisibleOfCurAccount().first())
        assertEquals(visible.take(1), interactor.subscribeAssetsFavoriteOfAccount(soraAccount).first())
        assertEquals(visible.first(), interactor.subscribeAssetOfCurAccount(xorId).first())
    }

    @Test
    fun `edit view changes go to the selected account`() = runTest {
        coEvery { assetsRepository.toggleVisibilityOfToken(any(), any(), any()) } returns Unit
        coEvery { assetsRepository.hideAssets(any(), any()) } returns Unit
        coEvery { assetsRepository.displayAssets(any(), any()) } returns Unit
        coEvery { assetsRepository.updateAssetPositions(any(), any()) } returns Unit
        coEvery { assetsRepository.updateBalancesVisibleAssets(any()) } returns Unit

        interactor.toggleVisibilityOfToken(valId, false)
        interactor.tokenFavoriteOff(listOf(valId))
        interactor.tokenFavoriteOn(listOf(xorId))
        interactor.updateAssetPositions(mapOf(xorId to 0, valId to 1))
        interactor.updateBalanceVisibleAssets()

        coVerify { assetsRepository.toggleVisibilityOfToken(valId, false, soraAccount) }
        coVerify { assetsRepository.hideAssets(listOf(valId), soraAccount) }
        coVerify { assetsRepository.displayAssets(listOf(xorId), soraAccount) }
        coVerify { assetsRepository.updateAssetPositions(mapOf(xorId to 0, valId to 1), soraAccount) }
        coVerify { assetsRepository.updateBalancesVisibleAssets(address) }
    }

    @Test
    fun `reads are made for the selected account`() = runTest {
        val xor = TestAssets.xorAsset(BigDecimal.ONE)
        val balance = XorAssetBalance(
            BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        )
        coEvery { assetsRepository.getAssetsWhitelist(address) } returns listOf(xor)
        coEvery { assetsRepository.getXORBalance(address, 4) } returns balance
        coEvery { assetsRepository.isWhitelistedToken(xorId) } returns true
        coEvery { assetsRepository.tokensList() } returns listOf(TestTokens.xorToken)
        coEvery { assetsRepository.fetchBalance(address, listOf(xorId)) } returns
            listOf(BigInteger("1500000000000000000"))

        assertEquals("Compare", interactor.getAccountName())
        assertEquals(listOf(xor), interactor.getWhitelistAssets())
        assertEquals(balance, interactor.getXorBalance(4))
        assertTrue(interactor.isWhitelistedToken(xorId))
        assertEquals(listOf(TestTokens.xorToken), interactor.getTokensList())
        assertEquals(listOf(BigDecimal("1.5")), interactor.fetchBalance(address, listOf(xorId), 18))
        val publicKey = interactor.getPublicKeyHex(true)
        assertTrue(publicKey, publicKey.startsWith("0x") && publicKey.length == 66)
    }

    @Test
    fun `a missing asset is reported, not invented`() = runTest {
        coEvery { assetsRepository.getAsset("missing", address) } returns null

        val error = runCatching { interactor.getAssetOrThrow("missing") }.exceptionOrNull()

        assertTrue(error.toString(), error is NoSuchElementException)
    }

    @Test
    fun `whitelist balances refresh without a test balance outside Soralution`() = runTest {
        coEvery { assetsRepository.updateWhitelistBalances(address) } returns Unit

        interactor.updateWhitelistBalances()

        coVerify(exactly = 1) { assetsRepository.updateWhitelistBalances(address) }
        coVerify(exactly = 0) { assetsRepository.addFakeBalance(any(), any(), any()) }
    }
}
