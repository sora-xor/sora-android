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

package jp.co.soramitsu.feature_ecosystem_impl.explore

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.demeter.domain.DemeterFarmingBasicPool
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_ecosystem_impl.domain.EcoSystemMapper
import jp.co.soramitsu.feature_ecosystem_impl.domain.EcoSystemToken
import jp.co.soramitsu.feature_ecosystem_impl.domain.EcoSystemTokens
import jp.co.soramitsu.feature_ecosystem_impl.domain.EcoSystemTokensInteractor
import jp.co.soramitsu.feature_ecosystem_impl.presentation.explore.ExploreViewModel
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.test_data.PolkaswapTestData.BASIC_POOL_DATA
import jp.co.soramitsu.test_data.TestTokens
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

@ExperimentalCoroutinesApi
class ExploreViewModelTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @MockK
    private lateinit var polkaswapRouter: PolkaswapRouter

    @MockK
    private lateinit var resourceManager: ResourceManager

    @MockK
    private lateinit var demeterFarmingInteractor: DemeterFarmingInteractor

    @MockK
    private lateinit var poolsInteractor: PoolsInteractor

    @MockK
    private lateinit var ecoSystemMapper: EcoSystemMapper

    @MockK
    private lateinit var ecoSystemTokensInteractor: EcoSystemTokensInteractor

    @MockK
    private lateinit var numbersFormatter: NumbersFormatter

    @MockK
    private lateinit var assetsRouter: AssetsRouter

    private lateinit var exploreViewModel: ExploreViewModel

    @Before
    fun setUp() = runTest {
        every { assetsRouter.showAssetDetails(any()) } returns Unit
        every { resourceManager.getString(any()) } returns ""
        coEvery { ecoSystemTokensInteractor.getTokensWithTvl() } returns EcoSystemTokens(
            tokens = listOf(
                EcoSystemToken(
                    token = TestTokens.xorToken,
                    liquidityFiat = 12.4,
                ),
                EcoSystemToken(
                    token = TestTokens.valToken,
                    liquidityFiat = 122.4,
                ),
                EcoSystemToken(
                    token = TestTokens.kxorToken,
                    liquidityFiat = 2.4,
                ),
                EcoSystemToken(
                    token = TestTokens.pswapToken,
                    liquidityFiat = 22.3,
                ),
            ),
        )
        coEvery { poolsInteractor.getBasicPools() } returns listOf(BASIC_POOL_DATA)
        coEvery { demeterFarmingInteractor.getFarmedBasicPools() } returns listOf(
            DemeterFarmingBasicPool(
                tokenBase = TestTokens.pswapToken,
                tokenTarget = TestTokens.xorToken,
                tokenReward = TestTokens.valToken,
                apr = 1.2,
                tvl = 123445.toBigDecimal(),
                fee = 4.5,
            )
        )
        exploreViewModel = ExploreViewModel(
            resourceManager,
            demeterFarmingInteractor,
            poolsInteractor,
            ecoSystemTokensInteractor,
            EcoSystemMapper(NumbersFormatter()),
            polkaswapRouter,
            assetsRouter,
            numbersFormatter
        )
    }

    @Test
    fun test() = runTest {
        exploreViewModel.onTokenClicked("0x00")
        verify { assetsRouter.showAssetDetails("0x00") }
    }

    @Test
    fun `explore search tokens test 01`() = runTest {
        advanceUntilIdle()
        exploreViewModel.onToolbarSearch("kxo")
        advanceUntilIdle()
        val ts = exploreViewModel.state.value.ecoSystemTokensState
        assertNotNull(ts)
        assertEquals(1, ts!!.topTokens.size)
    }
}
