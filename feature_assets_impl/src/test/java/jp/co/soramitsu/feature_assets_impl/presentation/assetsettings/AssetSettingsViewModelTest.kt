/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.presentation.assetsettings

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetBalance
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_assets_impl.presentation.states.AssetSettingsState
import jp.co.soramitsu.feature_assets_impl.presentation.screens.fullassetsettings.FullAssetSettingsViewModel
import jp.co.soramitsu.test_data.TestTokens.pswapToken
import jp.co.soramitsu.test_data.TestTokens.valToken
import jp.co.soramitsu.test_data.TestTokens.xorToken
import jp.co.soramitsu.test_data.TestTokens.xstToken
import jp.co.soramitsu.test_data.TestTokens.xstusdToken
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
import org.mockito.Mockito
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
    private lateinit var assetsInteractor: AssetsInteractor

    @Mock
    private lateinit var router: AssetsRouter

    private val mockedUri = Mockito.mock(Uri::class.java)

    private val nf = NumbersFormatter()

    private lateinit var viewModel: FullAssetSettingsViewModel

    @Before
    fun setUp() = runTest {
        mockkStatic(Uri::parse)
        every { Uri.parse(any()) } returns mockedUri
        mockkStatic(Token::iconUri)
        mockkObject(AssetHolder)
        every { AssetHolder.knownCount() } returns 5
        every { xorToken.iconUri() } returns mockedUri
        every { valToken.iconUri() } returns mockedUri
        every { pswapToken.iconUri() } returns mockedUri
        every { xstusdToken.iconUri() } returns mockedUri
        every { xstToken.iconUri() } returns mockedUri
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
            true,
            true,
        )
    ): List<Asset> {
        val assets = startList(vis)
        given(assetsInteractor.getWhitelistAssets()).willReturn(assets)
        viewModel = FullAssetSettingsViewModel(assetsInteractor, router, nf)
        return assets
    }

    @Test
    fun `init check`() = runTest {
        setUpStartList()
        advanceUntilIdle()
        val state = viewModel.settingsState.getOrAwaitValue(4)
        assertEquals(9, state.size)
    }

    @Test
    fun `check visibility on after change position`() = runTest {
        val list = setUpStartList(listOf(true, true, true, true, true, false, false, false, false))
        advanceUntilIdle()
        val mappedModels = mapModels(list)
        advanceUntilIdle()
        viewModel.onFavoriteClick(mappedModels[6])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOn(listOf("token2_id"))
        viewModel.onCloseClick()
        advanceUntilIdle()
        val map = list.map { it.token.id }.let {
            Collections.swap(it, 5, 6)
            it
        }.mapIndexed { index, s -> s to index }.toMap()
        verify(assetsInteractor).updateAssetPositions(map)
    }

    @Test
    fun `check visibility on after change position 2`() = runTest {
        val list =
            setUpStartList(listOf(true, true, false, false, true, false, false, false, false))
        advanceUntilIdle()
        val mappedModels = mapModels(list)
        advanceUntilIdle()
        viewModel.onFavoriteClick(mappedModels[7])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOn(listOf("token3_id"))
        viewModel.onFavoriteClick(mappedModels[2])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOn(listOf("0x0200050000000000000000000000000000000000000000000000000000000000"))
        viewModel.onCloseClick()
        advanceUntilIdle()
        val map = list.map { it.token.id }.let {
            Collections.swap(it, 6, 7)
            Collections.swap(it, 5, 6)
            it
        }.mapIndexed { index, s -> s to index }.toMap()
        verify(assetsInteractor).updateAssetPositions(map)
    }

    @Test
    fun `check visibility on after change position 3`() = runTest {
        val list = setUpStartList(listOf(true, true, true, true, true, true, false, false, false))
        advanceUntilIdle()
        val mappedModels = mapModels(list)
        advanceUntilIdle()
        viewModel.onFavoriteClick(mappedModels[7])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOn(listOf("token3_id"))
        viewModel.onFavoriteClick(mappedModels[6])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOn(listOf("token2_id"))
        viewModel.onFavoriteClick(mappedModels[5])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("token_id"))
        viewModel.onCloseClick()
        advanceUntilIdle()
        val map = list.map { it.token.id }.let {
            Collections.swap(it, 5, 6)
            Collections.swap(it, 6, 7)
            it
        }.mapIndexed { index, s -> s to index }.toMap()
        verify(assetsInteractor).updateAssetPositions(map)
    }

    @Test
    fun `check visibility on after change position 4`() = runTest {
        val list = setUpStartList(listOf(true, true, true, true, true, false, true, false, true))
        advanceUntilIdle()
        val mappedModels = mapModels(list)
        advanceUntilIdle()
        viewModel.onFavoriteClick(mappedModels[7])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOn(listOf("token3_id"))
        viewModel.onFavoriteClick(mappedModels[6])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("token2_id"))
        viewModel.onFavoriteClick(mappedModels[5])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOn(listOf("token_id"))
        viewModel.onFavoriteClick(mappedModels[8])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("token4_id"))
        viewModel.onCloseClick()
        advanceUntilIdle()
        val map = list.map { it.token.id }.let {
            Collections.swap(it, 6, 7)
            it
        }.mapIndexed { index, s -> s to index }.toMap()
        verify(assetsInteractor).updateAssetPositions(map)
    }

    @Test
    fun `check visibility on after change position 5`() = runTest {
        val list = setUpStartList(listOf(true, true, true, true, true, true, true, true, false))
        advanceUntilIdle()
        val mappedModels = mapModels(list)
        viewModel.onFavoriteClick(mappedModels[5])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("token_id"))
        viewModel.onFavoriteClick(mappedModels[8])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOn(listOf("token4_id"))
        viewModel.onCloseClick()
        advanceUntilIdle()
        val map = list.map { it.token.id }.let {
            Collections.swap(it, 5, 6)
            Collections.swap(it, 6, 7)
            Collections.swap(it, 7, 8)
            it
        }.mapIndexed { index, s -> s to index }.toMap()
        verify(assetsInteractor).updateAssetPositions(map)
    }

    @Test
    fun `check visibility on after change position 6`() = runTest {
        val list = setUpStartList(listOf(true, true, true, true, true, false, false, false, false))
        advanceUntilIdle()
        val mappedModels = mapModels(list)
        viewModel.onFavoriteClick(mappedModels[5])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOn(listOf("token_id"))
        viewModel.onFavoriteClick(mappedModels[6])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOn(listOf("token2_id"))
        viewModel.onFavoriteClick(mappedModels[7])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOn(listOf("token3_id"))
        viewModel.onFavoriteClick(mappedModels[8])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOn(listOf("token4_id"))
        viewModel.onCloseClick()
        advanceUntilIdle()
        val map = list.map { it.token.id }.mapIndexed { index, s -> s to index }.toMap()
        verify(assetsInteractor).updateAssetPositions(map)
    }

    @Test
    fun `check visibility after change position`() = runTest {
        val list = setUpStartList()
        advanceUntilIdle()
        val mappedModels = mapModels(list)
        viewModel.onFavoriteClick(mappedModels[6])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("token2_id"))
        viewModel.onCloseClick()
        advanceUntilIdle()
        val map = list.map { it.token.id }.let {
            Collections.swap(it, 6, 7)
            Collections.swap(it, 7, 8)
            it
        }.mapIndexed { index, s -> s to index }.toMap()
        verify(assetsInteractor).updateAssetPositions(map)
    }

    @Test
    fun `check visibility after change position 2`() = runTest {
        val list = setUpStartList()
        advanceUntilIdle()
        val mappedModels = mapModels(list)
        viewModel.onFavoriteClick(mappedModels[6])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("token2_id"))
        viewModel.onFavoriteClick(mappedModels[7])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("token3_id"))
        viewModel.onCloseClick()
        advanceUntilIdle()
        val map = list.map { it.token.id }.let {
            Collections.swap(it, 7, 8)
            Collections.swap(it, 6, 7)
            it
        }.mapIndexed { index, s -> s to index }.toMap()
        verify(assetsInteractor).updateAssetPositions(map)
    }

    @Test
    fun `check visibility after change position 3`() = runTest {
        val list = setUpStartList()
        advanceUntilIdle()
        val mappedModels = mapModels(list)
        viewModel.onFavoriteClick(mappedModels[5])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("token_id"))
        viewModel.onFavoriteClick(mappedModels[6])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("token2_id"))
        viewModel.onFavoriteClick(mappedModels[7])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("token3_id"))
        viewModel.onCloseClick()
        advanceUntilIdle()
        val map = list.map { it.token.id }.let {
            Collections.swap(it, 7, 8)
            Collections.swap(it, 6, 7)
            Collections.swap(it, 5, 6)
            it
        }.mapIndexed { index, s -> s to index }.toMap()
        verify(assetsInteractor).updateAssetPositions(map)
    }

    @Test
    fun `check visibility after change position 3 all`() = runTest {
        val list = setUpStartList()
        val mappedModels = mapModels(list)
        advanceUntilIdle()
        viewModel.onFavoriteClick(mappedModels[5])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("token_id"))
        viewModel.onFavoriteClick(mappedModels[6])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("token2_id"))
        viewModel.onFavoriteClick(mappedModels[7])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("token3_id"))
        viewModel.onFavoriteClick(mappedModels[8])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("token4_id"))
        viewModel.onCloseClick()
        advanceUntilIdle()
        val map = list.map { it.token.id }.mapIndexed { index, s -> s to index }.toMap()
        verify(assetsInteractor).updateAssetPositions(map)
    }

    @Test
    fun `check visibility after change position 4 all`() = runTest {
        val list = setUpStartList()
        val mappedModels = mapModels(list)
        advanceUntilIdle()
        viewModel.onFavoriteClick(mappedModels[0])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("0x0200000000000000000000000000000000000000000000000000000000000000"))
        viewModel.onFavoriteClick(mappedModels[1])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("0x0200040000000000000000000000000000000000000000000000000000000000"))
        viewModel.onFavoriteClick(mappedModels[2])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("0x0200050000000000000000000000000000000000000000000000000000000000"))
        viewModel.onFavoriteClick(mappedModels[3])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("0x0200080000000000000000000000000000000000000000000000000000000000"))
        viewModel.onFavoriteClick(mappedModels[5])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("token_id"))
        viewModel.onFavoriteClick(mappedModels[6])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("token2_id"))
        viewModel.onFavoriteClick(mappedModels[7])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("token3_id"))
        viewModel.onFavoriteClick(mappedModels[8])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("token4_id"))
        viewModel.onCloseClick()
        advanceUntilIdle()
        val map = list.map { it.token.id }.mapIndexed { index, s -> s to index }.toMap()
        verify(assetsInteractor).updateAssetPositions(map)
    }

    @Test
    fun `check visibility after change position 3 order`() = runTest {
        val list = setUpStartList()
        advanceUntilIdle()
        val mappedModels = mapModels(list)
        viewModel.onFavoriteClick(mappedModels[7])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("token3_id"))
        viewModel.onFavoriteClick(mappedModels[5])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("token_id"))
        viewModel.onFavoriteClick(mappedModels[6])
        advanceUntilIdle()
        verify(assetsInteractor).tokenFavoriteOff(listOf("token2_id"))
        viewModel.onCloseClick()
        advanceUntilIdle()
        val map = list.map { it.token.id }.let {
            Collections.swap(it, 7, 8)
            Collections.swap(it, 6, 7)
            Collections.swap(it, 5, 6)
            it
        }.mapIndexed { index, s -> s to index }.toMap()
        verify(assetsInteractor).updateAssetPositions(map)
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
        verify(assetsInteractor).updateAssetPositions(map)
    }

    @Test
    fun `click back`() = runTest {
        setUpStartList()
        viewModel.onCloseClick()
        advanceUntilIdle()
        verify(router).popBackStackFragment()
    }

    @Test
    fun `click done`() = runTest {
        setUpStartList()
        viewModel.onCloseClick()
        advanceUntilIdle()
        verify(router).popBackStackFragment()
    }

    @Test
    fun `check changed`() = runTest {
        val list = setUpStartList()
        val mappedModels = mapModels(list)
        viewModel.onFavoriteClick(mappedModels[0])
        advanceUntilIdle()
        viewModel.onCloseClick()
        advanceUntilIdle()
        verify(router).popBackStackFragment()
    }

    @Test
    fun `check changed add remove`() = runTest {
        val list = setUpStartList()
        val mappedModels = mapModels(list)
        viewModel.onFavoriteClick(mappedModels[0])
        advanceUntilIdle()
        viewModel.onFavoriteClick(mappedModels[1])
        advanceUntilIdle()
        viewModel.onCloseClick()
        advanceUntilIdle()
        verify(router).popBackStackFragment()
    }

    private fun startList(visibility: List<Boolean>) = listOf(
        Asset(
            xorToken,
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
            true,
        ),
        Asset(
            valToken,
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
            true,
        ),
        Asset(
            pswapToken,
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
            true,
        ),
        Asset(
            xstusdToken,
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
            true
        ),
        Asset(
            xstToken,
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
            true,
        ),
        Asset(
            Token(
                "token_id",
                "token name",
                "token symbol",
                18,
                true,
                mockedUri,
                null,
                null,
                null
            ),
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
            ),
            true,
        ),
        Asset(
            Token(
                "token2_id",
                "token2 name",
                "token2 symbol",
                18,
                true,
                mockedUri,
                null,
                null,
                null
            ),
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
            ),
            true,
        ),
        Asset(
            Token(
                "token3_id",
                "token3 name",
                "token3 symbol",
                18,
                true,
                mockedUri,
                null,
                null,
                null
            ),
            visibility[7],
            8,
            AssetBalance(
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
            ),
            true,
        ),
        Asset(
            Token(
                "token4_id",
                "token4 name",
                "token4 symbol",
                18,
                true,
                mockedUri,
                null,
                null,
                null
            ),
            visibility[8],
            9,
            AssetBalance(
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE
            ),
            true,
        )
    )

    private fun mapModels(list: List<Asset>) = list.map {
        AssetSettingsState(
                it.token.id,
                mockedUri,
                it.token.name,
                it.token.printBalance(it.balance.transferable, nf, 8),
                it.token.symbol,
                it.favorite,
                it.visibility,
                it.token.isHidable,
                fiat = 0.0,
        )
    }
}