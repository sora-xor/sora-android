/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.domain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TransactionHistoryRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Market
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
@ExperimentalCoroutinesApi
class PolkaswapInteractorTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var walletRepository: WalletRepository

    @Mock
    private lateinit var polkaswapRepository: PolkaswapRepository

    @Mock
    private lateinit var credentialsRepository: CredentialsRepository

    @Mock
    private lateinit var transactionHistoryRepository: TransactionHistoryRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var coroutineManager: CoroutineManager

    private lateinit var interactor: PolkaswapInteractor

    @Before
    fun setUp() {
        interactor = PolkaswapInteractorImpl(
            credentialsRepository,
            userRepository,
            transactionHistoryRepository,
            coroutineManager,
            polkaswapRepository,
            walletRepository
        )
    }

    @Test
    fun `set swap market`() = runTest {
        val expected = listOf(Market.SMART, Market.TBC, Market.XYK)
        val actual = mutableListOf<Market>()

        interactor.observeSelectedMarket().map {
            actual.add(it)
        }.launchIn(TestCoroutineScope())

        interactor.setSwapMarket(Market.SMART)
        interactor.setSwapMarket(Market.TBC)
        interactor.setSwapMarket(Market.XYK)

        assertEquals(expected, actual)
    }
}