package jp.co.soramitsu.feature_assets_impl.presentation.assetsettings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import io.mockk.*
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.domain.*
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_impl.presentation.screens.fullassetsettings.FullAssetSettingsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class AssetSettingsInteractionModelTest {
    @get:Rule val main = MainCoroutineRule()
    @get:Rule val instant = InstantTaskExecutorRule()
    private val interactor = mockk<AssetsInteractor>(relaxed = true)
    private val router = mockk<AssetsRouter>(relaxed = true)
    private val store = ViewModelStore()
    private fun asset(id: String) = Asset(Token(id, id.uppercase(), id.uppercase(), 18, true, null, 2.0, 0.0, "$"),
        false, 0, createAssetBalance().copy(transferable = BigDecimal("2"), total = BigDecimal("2")), true)
    private val catalog = listOf(asset("alpha"), asset("beta"), asset("gamma"))
    @Before fun setup() { coEvery { interactor.getWhitelistAssets() } returns catalog }
    @After fun cleanup() { store.clear() }
    private fun TestScope.screen() = FullAssetSettingsViewModel(interactor, router, NumbersFormatter()).also { store.put("settings", it); runCurrent() }

    @Test fun `search preserves displayed balances and addresses the selected token`() = runTest {
        val vm = screen(); vm.onToolbarSearch("BETA")
        assertEquals("beta", vm.settingsState.value!!.single().token.id)
        assertEquals("2 BETA", vm.settingsState.value!!.single().assetAmount)
        assertFalse(vm.dragList.value!!)
        vm.onToolbarSearch("missing"); assertTrue(vm.settingsStateEmpty.value); assertEquals("", vm.fiatSum.value)
        vm.onToolbarSearch(""); assertEquals(3, vm.settingsState.value!!.size); assertTrue(vm.dragList.value!!)
    }
    @Test fun `rapid favorite toggles use the current row state`() = runTest {
        val vm = screen(); val row = vm.settingsState.value!!.first()
        vm.onFavoriteClick(row); vm.onFavoriteClick(row); runCurrent()
        assertFalse(vm.settingsState.value!!.first().favorite)
        coVerify(exactly = 1) { interactor.tokenFavoriteOn(listOf("alpha")) }
        coVerify(exactly = 1) { interactor.tokenFavoriteOff(listOf("alpha")) }
    }
    @Test fun `outdated drag indices leave token order unchanged`() = runTest {
        val vm = screen()
        for ((from, to) in listOf(-1 to 0, 0 to -1, 5 to 0, 0 to 5)) assertFalse(vm.assetPositionChanged(from, to))
        assertEquals(listOf("alpha", "beta", "gamma"), vm.settingsState.value!!.map { it.token.id })
        coVerify(exactly = 0) { interactor.updateAssetPositions(any()) }
    }
    @Test fun `filtered list cannot reorder tokens hidden by the search`() = runTest {
        val vm = screen(); vm.onToolbarSearch("GAMMA")
        assertFalse(vm.assetPositionChanged(0, 1)); runCurrent()
        vm.onToolbarSearch("")
        assertEquals(listOf("alpha", "beta", "gamma"), vm.settingsState.value!!.map { it.token.id })
        coVerify(exactly = 0) { interactor.updateAssetPositions(any()) }
    }
    @Test fun `valid drag persists exact token positions`() = runTest {
        val vm = screen(); assertTrue(vm.assetPositionChanged(0, 2)); runCurrent()
        assertEquals(listOf("beta", "gamma", "alpha"), vm.settingsState.value!!.map { it.token.id })
        coVerify { interactor.updateAssetPositions(mapOf("beta" to 0, "gamma" to 1, "alpha" to 2)) }
    }
    @Test fun `visibility response updates its token even after a concurrent reorder`() = runTest {
        val saved = CompletableDeferred<Unit>()
        coEvery { interactor.toggleVisibilityOfToken("alpha", false) } coAnswers { saved.await() }
        val vm = screen(); vm.onVisibilityClick(vm.settingsState.value!!.first()); runCurrent()
        vm.assetPositionChanged(0, 2); runCurrent(); saved.complete(Unit); runCurrent()
        assertFalse(vm.settingsState.value!!.single { it.token.id == "alpha" }.visible)
        assertTrue(vm.settingsState.value!!.single { it.token.id == "beta" }.visible)
    }
    @Test fun `favorite changes can be saved when the catalog omits default tokens`() = runTest {
        coEvery { interactor.getWhitelistAssets() } returns catalog.map { it.copy(favorite = true) }
        val vm = screen(); vm.onFavoriteClick(vm.settingsState.value!!.first()); runCurrent()
        vm.onAction(); runCurrent()
        coVerify { interactor.updateAssetPositions(mapOf("beta" to 0, "gamma" to 1, "alpha" to 2)) }
        verify(exactly = 1) { router.popBackStackFragment() }
    }
    @Test fun `unknown row callbacks never mutate another token`() = runTest {
        val vm = screen(); val unknown = vm.settingsState.value!!.first().copy(token = asset("unknown").token)
        vm.onFavoriteClick(unknown); vm.onVisibilityClick(unknown); runCurrent()
        coVerify(exactly = 0) { interactor.tokenFavoriteOn(any()) }
        coVerify(exactly = 0) { interactor.toggleVisibilityOfToken(any(), any()) }
    }
}
