package jp.co.soramitsu.feature_assets_impl.presentation.qr

import androidx.lifecycle.ViewModelStore
import io.mockk.*
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.selectsearchtoken.*
import jp.co.soramitsu.test_data.TestAssets
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal

/** Token selection uses the current account snapshot and intersects query with allowed IDs. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TokenSelectionFlowTest {
    @get:Rule val main = MainCoroutineRule()
    private val interactor = mockk<AssetsInteractor>()
    private val store = ViewModelStore()
    private val xor = TestAssets.xorAsset(BigDecimal.TEN).let { it.copy(token = it.token.copy(id = "xor-id", name = "SORA", symbol = "XOR")) }
    private val valAsset = TestAssets.valAsset(BigDecimal("5")).let { it.copy(token = it.token.copy(id = "val-id", name = "Validator", symbol = "VAL")) }
    private val assets = MutableStateFlow(listOf(xor, valAsset))
    @Before fun setup() { every { interactor.subscribeAssetsActiveOfCurAccount() } returns assets }
    @After fun cleanup() { store.clear() }
    private fun vm() = SelectSearchTokenViewModel(interactor, NumbersFormatter()).also { store.put("vm", it) }
    private fun ids(vm: SelectSearchTokenViewModel) = vm.state.value.list.map { it.tokenId }
    @Test fun firstSnapshotKeepsAssetOrderAndTransferableBalances() = runTest {
        val vm = vm(); runCurrent(); assertEquals(listOf("xor-id", "val-id"), ids(vm))
        assertEquals(listOf("10 XOR", "5 VAL"), vm.state.value.list.map { it.assetAmount })
    }
    @Test fun nameSearchIsCaseInsensitive() = runTest {
        val vm = vm(); runCurrent(); vm.onFilterChange(SearchTokenFilter("VALIDATOR", emptyList()))
        assertEquals(listOf("val-id"), ids(vm))
    }
    @Test fun symbolSearchIsCaseInsensitive() = runTest {
        val vm = vm(); runCurrent(); vm.onFilterChange(SearchTokenFilter("xOr", emptyList()))
        assertEquals(listOf("xor-id"), ids(vm))
    }
    @Test fun exactIdentifierCanBeSearched() = runTest {
        val vm = vm(); runCurrent(); vm.onFilterChange(SearchTokenFilter("VAL-ID", emptyList()))
        assertEquals(listOf("val-id"), ids(vm))
    }
    @Test fun allowedIdentifiersExcludeOtherAssets() = runTest {
        val vm = vm(); runCurrent(); vm.onFilterChange(SearchTokenFilter("", listOf("val-id")))
        assertEquals(listOf("val-id"), ids(vm))
    }
    @Test fun queryCannotReintroduceAnExcludedToken() = runTest {
        val vm = vm(); runCurrent(); vm.onFilterChange(SearchTokenFilter("XOR", listOf("val-id")))
        assertTrue(ids(vm).isEmpty())
    }
    @Test fun unknownAllowedIdentifiersDoNotFallBackToAllAssets() = runTest {
        val vm = vm(); runCurrent(); vm.onFilterChange(SearchTokenFilter("", listOf("unavailable")))
        assertTrue(ids(vm).isEmpty())
    }
    @Test fun repeatedAllowedIdentifiersDoNotDuplicateRows() = runTest {
        val vm = vm(); runCurrent(); vm.onFilterChange(SearchTokenFilter("", listOf("val-id", "val-id")))
        assertEquals(listOf("val-id"), ids(vm))
    }
    @Test fun querySetBeforeInitialSnapshotIsPreserved() = runTest {
        val vm = vm(); vm.onFilterChange(SearchTokenFilter("val", emptyList())); runCurrent()
        assertEquals(listOf("val-id"), ids(vm))
    }
    @Test fun accountSnapshotChangeRemovesPreviousWalletAssets() = runTest {
        val vm = vm(); runCurrent(); assets.value = listOf(valAsset); runCurrent(); assertEquals(listOf("val-id"), ids(vm))
    }
    @Test fun balanceUpdateKeepsTheCurrentFilterAndRefreshesAmount() = runTest {
        val vm = vm(); runCurrent(); vm.onFilterChange(SearchTokenFilter("val", emptyList()))
        assets.value = listOf(xor, valAsset.copy(balance = valAsset.balance.copy(transferable = BigDecimal("2")))); runCurrent()
        assertEquals(listOf("val-id"), ids(vm)); assertEquals("2 VAL", vm.state.value.list.single().assetAmount)
    }
    @Test fun blankQueryClearsTextSearchButPreservesAllowedIdentifiers() = runTest {
        val vm = vm(); runCurrent(); vm.onFilterChange(SearchTokenFilter("missing", listOf("xor-id")))
        vm.onFilterChange(SearchTokenFilter("  ", listOf("xor-id"))); assertEquals(listOf("xor-id"), ids(vm))
    }
    @Test fun clearingScreenStopsFurtherAccountUpdates() = runTest {
        val vm = vm(); runCurrent(); val previous = vm.state.value; store.clear(); assets.value = emptyList(); runCurrent()
        assertSame(previous, vm.state.value)
    }
}
