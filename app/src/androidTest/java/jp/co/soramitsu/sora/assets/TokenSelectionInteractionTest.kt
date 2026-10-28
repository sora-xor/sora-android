package jp.co.soramitsu.sora.assets

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetItemCardState
import jp.co.soramitsu.feature_assets_api.presentation.selectsearchtoken.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** The real token picker binds allowed IDs, live rows and selection callbacks. */
@RunWith(AndroidJUnit4::class)
class TokenSelectionInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun row(id: String) = AssetItemCardState("", "token-$id", "Asset $id", id.uppercase(), "10 ${id.uppercase()}", "$10", "")
    private val screenState = MutableStateFlow(SelectSearchAssetState(listOf(row("xor"), row("val"))))
    private val query = mutableStateOf(emptySearchTokenFilter)
    private val vm = mockk<SelectSearchTokenViewModel>(relaxed = true) { every { state } returns screenState }
    private val selected = mutableListOf<String>()
    private fun show() { compose.setContent { SoraAppTheme { Box(Modifier.size(320.dp, 480.dp)) {
        SelectSearchTokenScreen(rememberScrollState(), query.value, vm) { selected += it }
    } } } }
    private fun emptyMessage() = compose.onNodeWithText(compose.activity.getString(R.string.search_empty_state))
    @Test fun selectingARowReturnsItsIdentifierOnlyAfterTap() {
        show(); compose.runOnIdle { assertTrue(selected.isEmpty()) }; compose.onNodeWithText("Asset val").performClick()
        compose.runOnIdle { assertEquals(listOf("token-val"), selected) }
    }
    @Test fun filterChangesForwardBothQueryAndAllowedIdentifiers() {
        show(); val filter = SearchTokenFilter("VA", listOf("token-val")); compose.runOnIdle { query.value = filter }
        compose.runOnIdle { verify(exactly = 1) { vm.onFilterChange(filter) } }
    }
    @Test fun replacingAssetsRemovesOldRowsAndSelectsCurrentIdentity() {
        show(); compose.runOnIdle { screenState.value = SelectSearchAssetState(listOf(row("dai"))) }
        compose.onNodeWithText("Asset val").assertDoesNotExist(); compose.onNodeWithText("Asset dai").performClick()
        compose.runOnIdle { assertEquals(listOf("token-dai"), selected) }
    }
    @Test fun emptySearchHasNoStaleSelectableAsset() {
        show(); compose.runOnIdle { screenState.value = SelectSearchAssetState(emptyList()) }
        emptyMessage().assertIsDisplayed(); compose.onNodeWithText("Asset xor").assertDoesNotExist()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }
    @Test fun newMatchesReplaceTheEmptySearchExplanation() {
        screenState.value = SelectSearchAssetState(emptyList()); show()
        compose.runOnIdle { screenState.value = SelectSearchAssetState(listOf(row("val"))) }
        emptyMessage().assertDoesNotExist(); compose.onNodeWithText("Asset val").assertIsDisplayed()
    }
    @Test fun lastMatchingAssetCanBeScrolledIntoViewAndSelected() {
        screenState.value = SelectSearchAssetState((1..20).map { row("asset$it") }); show()
        compose.onNodeWithText("Asset asset20").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("token-asset20"), selected) }
    }
}
