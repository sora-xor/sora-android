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

package jp.co.soramitsu.feature_polkaswap_impl.domain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common_wallet.domain.model.WithDesired
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapExtrinsicRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapSubscriptionRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.test_data.TestTokens
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner::class)
@ExperimentalCoroutinesApi
class PoolsInteractorTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var polkaswapRepository: PolkaswapRepository

    @Mock
    private lateinit var polkaswapSubscriptionRepository: PolkaswapSubscriptionRepository

    @Mock
    private lateinit var polkaswapExtrinsicRepository: PolkaswapExtrinsicRepository

    @Mock
    private lateinit var credentialsRepository: CredentialsRepository

    @Mock
    private lateinit var transactionHistoryRepository: TransactionHistoryRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var builder: TransactionBuilder

    @Mock
    private lateinit var assetsRepository: AssetsRepository

    private lateinit var interactor: PoolsInteractor

    private val soraAccount = SoraAccount("cnVko", "VkoName")

    @Before
    fun setUp() = runTest {
        whenever(userRepository.getCurSoraAccount()).thenReturn(soraAccount)
        interactor = PoolsInteractorImpl(
            credentialsRepository,
            userRepository,
            transactionHistoryRepository,
            polkaswapRepository,
            polkaswapSubscriptionRepository,
            polkaswapExtrinsicRepository,
            assetsRepository,
            builder,
        )
    }

    @Test
    fun `get calcLiquidityDetails`() = runTest {
        val myXor = BigDecimal(10)
        val myVal = BigDecimal(30)
        val fee = BigDecimal(0.07)
        whenever(
            polkaswapExtrinsicRepository.calcAddLiquidityNetworkFee(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        ).thenReturn(fee)
        val details = interactor.calcLiquidityDetails(
            TestTokens.xorToken,
            TestTokens.valToken,
            BigDecimal("21227.095354697994258051"),
            BigDecimal("10722.971040461595180078"),
            BigDecimal("7107.424927600170297498254692882532515"),
            myXor,
            myVal,
            WithDesired.INPUT,
            1.0,
            pairEnabled = true,
            pairPresented = true,
        )
        assertEquals(fee, details.networkFee)
        assertEquals(BigDecimal("5.051548910147228600"), details.targetAmount)
        assertEquals(BigDecimal("1.979590849830759653"), details.perFirst)
        assertEquals(BigDecimal("0.505154891014722860"), details.perSecond)
        assertEquals(BigDecimal("66.298112417815485326"), details.shareOfPool)
    }
}
