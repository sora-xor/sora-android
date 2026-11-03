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

package jp.co.soramitsu.feature_assets_impl.presentation.fullassetlist

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import java.io.IOException
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_impl.presentation.screens.fullassetlist.FullAssetListViewModel
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

/**
 * The classic Wallet home's "Expand" opens the full asset list: visible assets worth at least one
 * unit of fiat on top, the rest below, and a search that also finds the hidden ones.
 */
@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class FullAssetListViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var assetsInteractor: AssetsInteractor

    @Mock
    private lateinit var router: AssetsRouter

    private val nf = NumbersFormatter()

    private val pricedXor = TestAssets.xorAsset(BigDecimal.TEN)
        .copy(token = TestTokens.xorToken.copy(fiatPrice = 2.0, fiatSymbol = "$"))
    private val valAsset = TestAssets.valAsset(BigDecimal.ONE)
    private val pswapAsset = TestAssets.pswapAsset(BigDecimal.ONE)

    private suspend fun viewModel(
        visible: Flow<List<Asset>> = flowOf(listOf(pricedXor, valAsset)),
        all: List<Asset> = listOf(pricedXor, valAsset, pswapAsset),
    ): FullAssetListViewModel {
        given(assetsInteractor.getWhitelistAssets()).willReturn(all)
        given(assetsInteractor.subscribeAssetsVisibleOfCurAccount()).willReturn(visible)
        return FullAssetListViewModel(assetsInteractor, router, nf)
    }

    @Test
    fun `visible assets split at one unit of fiat and hidden ones stay out`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.searchMode)
        assertEquals(listOf(pricedXor.token.id), state.topList.map { it.tokenId })
        assertEquals(listOf(valAsset.token.id), state.bottomList.map { it.tokenId })
        assertTrue(state.fiatSum, state.fiatSum.contains("20"))
        val toolbar = viewModel.toolbarState.value!!.basic
        assertTrue(toolbar.searchEnabled)
        assertEquals("", toolbar.searchValue)
    }

    @Test
    fun `search finds hidden assets too and sums both lists`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onToolbarSearch("pswap")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.searchMode)
        assertTrue(state.topList.isEmpty())
        assertEquals(listOf(pswapAsset.token.id), state.bottomList.map { it.tokenId })
        assertEquals("pswap", viewModel.toolbarState.value!!.basic.searchValue)

        viewModel.onToolbarSearch("")
        advanceUntilIdle()
        assertFalse(viewModel.state.value.searchMode)
        assertEquals(listOf(pricedXor.token.id), viewModel.state.value.topList.map { it.tokenId })
    }

    @Test
    fun `a search matching a visible asset lists it on top`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onToolbarSearch("XOR")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.searchMode)
        assertEquals(listOf(pricedXor.token.id), state.topList.map { it.tokenId })
        assertTrue(state.bottomList.isEmpty())
        assertTrue(state.fiatSum, state.fiatSum.contains("20"))
    }

    @Test
    fun `edit opens the asset settings and a row opens its details`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction()
        viewModel.onAssetClick(valAsset.token.id)

        verify(router).showFullAssetsSettings()
        verify(router).showAssetDetails(valAsset.token.id)
    }

    @Test
    fun `a failing balance stream keeps the empty list`() = runTest {
        val viewModel = viewModel(visible = flow { throw IOException("database closed") })
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.topList.isEmpty())
        assertTrue(state.bottomList.isEmpty())
    }
}
