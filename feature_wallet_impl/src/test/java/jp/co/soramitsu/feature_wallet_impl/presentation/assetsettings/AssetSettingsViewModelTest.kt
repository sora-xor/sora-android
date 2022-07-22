/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.assetsettings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetBalance
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.AssetSettingsViewModel
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.display.AssetConfigurableModel
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.getOrAwaitValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal
import java.util.Collections

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class AssetSettingsViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var router: WalletRouter

    @Mock
    private lateinit var resourceManager: ResourceManager

    private lateinit var viewModel: AssetSettingsViewModel

    @Before
    fun setUp() = runTest {
    }

    private suspend fun setUpStartList(
        vis: List<Boolean> = listOf(
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            true
        )
    ): List<Asset> {
        val assets = startList(vis)
        given(walletInteractor.getWhitelistAssets()).willReturn(assets)
        viewModel =
            AssetSettingsViewModel(walletInteractor, NumbersFormatter(), resourceManager, router)
        return assets
    }

    @Test
    fun `init check`() = runTest {
        setUpStartList()
        viewModel.assetsListLiveData.observeForever {
            assertEquals(8, it.size)
        }
    }

    @Test
    fun `check visibility on after change position`() = runTest {
        val list = setUpStartList(listOf(true, true, true, true, false, false, false, false))
        val mappedModels = mapModels(list)
        viewModel.checkChanged(mappedModels[5], true)
        advanceUntilIdle()
        verify(walletInteractor).displayAssets(listOf("token2_id"))
        viewModel.backClicked()
        advanceUntilIdle()
        val map = list.map { it.token.id }.let {
            Collections.swap(it, 4, 5)
            it
        }.mapIndexed { index, s -> s to index }.toMap()
        verify(walletInteractor).updateAssetPositions(map)
    }

    @Test
    fun `check visibility on after change position 2`() = runTest {
        val list = setUpStartList(listOf(true, true, false, true, false, false, false, false))
        val mappedModels = mapModels(list)
        viewModel.checkChanged(mappedModels[6], true)
        advanceUntilIdle()
        verify(walletInteractor).displayAssets(listOf("token3_id"))
        viewModel.checkChanged(mappedModels[2], true)
        advanceUntilIdle()
        verify(walletInteractor).displayAssets(listOf("0x0200050000000000000000000000000000000000000000000000000000000000"))
        viewModel.backClicked()
        advanceUntilIdle()
        val map = list.map { it.token.id }.let {
            Collections.swap(it, 5, 6)
            Collections.swap(it, 4, 5)
            it
        }.mapIndexed { index, s -> s to index }.toMap()
        verify(walletInteractor).updateAssetPositions(map)
    }

    @Test
    fun `check visibility on after change position 3`() = runTest {
        val list = setUpStartList(listOf(true, true, true, true, true, false, false, false))
        val mappedModels = mapModels(list)
        viewModel.checkChanged(mappedModels[6], true)
        advanceUntilIdle()
        verify(walletInteractor).displayAssets(listOf("token3_id"))
        viewModel.checkChanged(mappedModels[5], true)
        advanceUntilIdle()
        verify(walletInteractor).displayAssets(listOf("token2_id"))
        viewModel.checkChanged(mappedModels[4], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("token_id"))
        viewModel.backClicked()
        advanceUntilIdle()
        val map = list.map { it.token.id }.let {
            Collections.swap(it, 4, 5)
            Collections.swap(it, 5, 6)
            it
        }.mapIndexed { index, s -> s to index }.toMap()
        verify(walletInteractor).updateAssetPositions(map)
    }

    @Test
    fun `check visibility on after change position 4`() = runTest {
        val list = setUpStartList(listOf(true, true, true, true, false, true, false, true))
        val mappedModels = mapModels(list)
        viewModel.checkChanged(mappedModels[6], true)
        advanceUntilIdle()
        verify(walletInteractor).displayAssets(listOf("token3_id"))
        viewModel.checkChanged(mappedModels[5], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("token2_id"))
        viewModel.checkChanged(mappedModels[4], true)
        advanceUntilIdle()
        verify(walletInteractor).displayAssets(listOf("token_id"))
        viewModel.checkChanged(mappedModels[7], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("token4_id"))
        viewModel.backClicked()
        advanceUntilIdle()
        val map = list.map { it.token.id }.let {
            Collections.swap(it, 5, 6)
            it
        }.mapIndexed { index, s -> s to index }.toMap()
        verify(walletInteractor).updateAssetPositions(map)
    }

    @Test
    fun `check visibility on after change position 5`() = runTest {
        val list = setUpStartList(listOf(true, true, true, true, true, true, true, false))
        advanceUntilIdle()
        val mappedModels = mapModels(list)
        viewModel.checkChanged(mappedModels[4], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("token_id"))
        viewModel.checkChanged(mappedModels[7], true)
        advanceUntilIdle()
        verify(walletInteractor).displayAssets(listOf("token4_id"))
        viewModel.backClicked()
        advanceUntilIdle()
        val map = list.map { it.token.id }.let {
            Collections.swap(it, 4, 5)
            Collections.swap(it, 5, 6)
            Collections.swap(it, 6, 7)
            it
        }.mapIndexed { index, s -> s to index }.toMap()
        verify(walletInteractor).updateAssetPositions(map)
    }

    @Test
    fun `check visibility on after change position 6`() = runTest {
        val list = setUpStartList(listOf(true, true, true, true, false, false, false, false))
        advanceUntilIdle()
        val mappedModels = mapModels(list)
        viewModel.checkChanged(mappedModels[4], true)
        advanceUntilIdle()
        verify(walletInteractor).displayAssets(listOf("token_id"))
        viewModel.checkChanged(mappedModels[5], true)
        advanceUntilIdle()
        verify(walletInteractor).displayAssets(listOf("token2_id"))
        viewModel.checkChanged(mappedModels[6], true)
        advanceUntilIdle()
        verify(walletInteractor).displayAssets(listOf("token3_id"))
        viewModel.checkChanged(mappedModels[7], true)
        advanceUntilIdle()
        verify(walletInteractor).displayAssets(listOf("token4_id"))
        viewModel.backClicked()
        advanceUntilIdle()
        val map = list.map { it.token.id }.mapIndexed { index, s -> s to index }.toMap()
        verify(walletInteractor).updateAssetPositions(map)
    }

    @Test
    fun `check visibility after change position`() = runTest {
        val list = setUpStartList()
        advanceUntilIdle()
        val mappedModels = mapModels(list)
        viewModel.checkChanged(mappedModels[5], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("token2_id"))
        viewModel.backClicked()
        advanceUntilIdle()
        val map = list.map { it.token.id }.let {
            Collections.swap(it, 5, 6)
            Collections.swap(it, 6, 7)
            it
        }.mapIndexed { index, s -> s to index }.toMap()
        verify(walletInteractor).updateAssetPositions(map)
    }

    @Test
    fun `check visibility after change position 2`() = runTest {
        val list = setUpStartList()
        advanceUntilIdle()
        val mappedModels = mapModels(list)
        viewModel.checkChanged(mappedModels[5], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("token2_id"))
        viewModel.checkChanged(mappedModels[6], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("token3_id"))
        viewModel.backClicked()
        advanceUntilIdle()
        val map = list.map { it.token.id }.let {
            Collections.swap(it, 6, 7)
            Collections.swap(it, 5, 6)
            it
        }.mapIndexed { index, s -> s to index }.toMap()
        verify(walletInteractor).updateAssetPositions(map)
    }

    @Test
    fun `check visibility after change position 3`() = runTest {
        val list = setUpStartList()
        advanceUntilIdle()
        val mappedModels = mapModels(list)
        viewModel.checkChanged(mappedModels[4], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("token_id"))
        viewModel.checkChanged(mappedModels[5], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("token2_id"))
        viewModel.checkChanged(mappedModels[6], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("token3_id"))
        viewModel.backClicked()
        advanceUntilIdle()
        val map = list.map { it.token.id }.let {
            Collections.swap(it, 6, 7)
            Collections.swap(it, 5, 6)
            Collections.swap(it, 4, 5)
            it
        }.mapIndexed { index, s -> s to index }.toMap()
        verify(walletInteractor).updateAssetPositions(map)
    }

    @Test
    fun `check visibility after change position 3 all`() = runTest {
        val list = setUpStartList()
        val mappedModels = mapModels(list)
        viewModel.checkChanged(mappedModels[4], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("token_id"))
        viewModel.checkChanged(mappedModels[5], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("token2_id"))
        viewModel.checkChanged(mappedModels[6], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("token3_id"))
        viewModel.checkChanged(mappedModels[7], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("token4_id"))
        viewModel.backClicked()
        advanceUntilIdle()
        val map = list.map { it.token.id }.mapIndexed { index, s -> s to index }.toMap()
        verify(walletInteractor).updateAssetPositions(map)
    }

    @Test
    fun `check visibility after change position 4 all`() = runTest {
        val list = setUpStartList()
        val mappedModels = mapModels(list)
        viewModel.checkChanged(mappedModels[0], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("0x0200000000000000000000000000000000000000000000000000000000000000"))
        viewModel.checkChanged(mappedModels[1], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("0x0200040000000000000000000000000000000000000000000000000000000000"))
        viewModel.checkChanged(mappedModels[2], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("0x0200050000000000000000000000000000000000000000000000000000000000"))
        viewModel.checkChanged(mappedModels[3], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("0x0200080000000000000000000000000000000000000000000000000000000000"))
        viewModel.checkChanged(mappedModels[4], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("token_id"))
        viewModel.checkChanged(mappedModels[5], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("token2_id"))
        viewModel.checkChanged(mappedModels[6], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("token3_id"))
        viewModel.checkChanged(mappedModels[7], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("token4_id"))
        viewModel.backClicked()
        advanceUntilIdle()
        val map = list.map { it.token.id }.mapIndexed { index, s -> s to index }.toMap()
        verify(walletInteractor).updateAssetPositions(map)
    }

    @Test
    fun `check visibility after change position 3 order`() = runTest {
        val list = setUpStartList()
        advanceUntilIdle()
        val mappedModels = mapModels(list)
        viewModel.checkChanged(mappedModels[6], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("token3_id"))
        viewModel.checkChanged(mappedModels[4], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("token_id"))
        viewModel.checkChanged(mappedModels[5], false)
        advanceUntilIdle()
        verify(walletInteractor).hideAssets(listOf("token2_id"))
        viewModel.backClicked()
        advanceUntilIdle()
        val map = list.map { it.token.id }.let {
            Collections.swap(it, 6, 7)
            Collections.swap(it, 5, 6)
            Collections.swap(it, 4, 5)
            it
        }.mapIndexed { index, s -> s to index }.toMap()
        verify(walletInteractor).updateAssetPositions(map)
    }

    @Test
    fun `change position`() = runTest {
        val list = setUpStartList()
        advanceUntilIdle()
        val v = viewModel.assetPositionChanged(0, 1)
        advanceUntilIdle()
        assertEquals(false, v)

        val res = viewModel.assetPositionChanged(6, 7)
        advanceUntilIdle()
        val ld = viewModel.assetPositions.getOrAwaitValue()
        assertEquals(6, ld.first)
        assertEquals(7, ld.second)
        assertEquals(true, res)
        val map = list.map { it.token.id }.let {
            Collections.swap(it, 6, 7)
            it
        }.mapIndexed { index, s -> s to index }.toMap()
        verify(walletInteractor).updateAssetPositions(map)
    }

    @Test
    fun `click back`() = runTest {
        setUpStartList()
        viewModel.backClicked()
        advanceUntilIdle()
        verify(router).popBackStackFragment()
    }

    @Test
    fun `click done`() = runTest {
        setUpStartList()
        viewModel.backClicked()
        advanceUntilIdle()
        verify(router).popBackStackFragment()
    }

    @Test
    fun `check changed`() = runTest {
        val list = setUpStartList()
        val mappedModels = mapModels(list)
        viewModel.checkChanged(mappedModels[0], true)
        advanceUntilIdle()
        viewModel.backClicked()
        advanceUntilIdle()
        verify(router).popBackStackFragment()
    }

    @Test
    fun `check changed add remove`() = runTest {
        val list = setUpStartList()
        val mappedModels = mapModels(list)
        viewModel.checkChanged(mappedModels[0], true)
        advanceUntilIdle()
        viewModel.checkChanged(mappedModels[1], false)
        advanceUntilIdle()
        viewModel.backClicked()
        advanceUntilIdle()
        verify(router).popBackStackFragment()
    }

    private fun startList(visibility: List<Boolean>) = listOf(
        Asset(
            Token(
                "0x0200000000000000000000000000000000000000000000000000000000000000",
                "sora",
                "xor",
                18,
                false,
                0
            ),
            visibility[0],
            1,
            AssetBalance(
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE
            ),
        ),
        Asset(
            Token(
                "0x0200040000000000000000000000000000000000000000000000000000000000",
                "sora validator",
                "val",
                18,
                true,
                0
            ),
            visibility[1],
            2,
            AssetBalance(
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE
            ),
        ),
        Asset(
            Token(
                "0x0200050000000000000000000000000000000000000000000000000000000000",
                "polkaswap",
                "pswap",
                18,
                true,
                0
            ),
            visibility[2],
            3,
            AssetBalance(
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE
            ),
        ),
        Asset(
            Token(
                "0x0200080000000000000000000000000000000000000000000000000000000000",
                "sora synt",
                "xstusd",
                18,
                true,
                0
            ),
            visibility[3],
            4,
            AssetBalance(
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE
            ),
        ),
        Asset(
            Token("token_id", "token name", "token symbol", 18, true, 0),
            visibility[4],
            5,
            AssetBalance(
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE
            ),
        ),
        Asset(
            Token("token2_id", "token2 name", "token2 symbol", 18, true, 0),
            visibility[5],
            6,
            AssetBalance(
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE
            )
        ),
        Asset(
            Token("token3_id", "token3 name", "token3 symbol", 18, true, 0),
            visibility[6],
            7,
            AssetBalance(
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE
            )
        ),
        Asset(
            Token("token4_id", "token4 name", "token4 symbol", 18, true, 0),
            visibility[7],
            8,
            AssetBalance(
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE
            )
        )
    )

    private fun mapModels(list: List<Asset>) = list.map {
        AssetConfigurableModel(
            it.token.id,
            it.token.name,
            it.token.symbol,
            it.token.icon,
            it.token.isHidable,
            "0.6",
            it.isDisplaying
        )
    }
}