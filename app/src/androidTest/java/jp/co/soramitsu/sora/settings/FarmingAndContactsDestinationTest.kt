@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.settings

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.MutableLiveData
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.*
import jp.co.soramitsu.common.presentation.args.tokenId
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_ecosystem_impl.presentation.farmdetails.*
import jp.co.soramitsu.feature_ecosystem_impl.presentation.farmdetails.model.*
import jp.co.soramitsu.feature_ecosystem_impl.presentation.editfarm.*
import jp.co.soramitsu.feature_ecosystem_impl.presentation.editfarm.model.*
import jp.co.soramitsu.feature_ecosystem_impl.presentation.claimdemeter.*
import jp.co.soramitsu.feature_ecosystem_impl.presentation.claimdemeter.model.ClaimScreenState
import jp.co.soramitsu.feature_wallet_impl.presentation.claim.*
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.*
import jp.co.soramitsu.ui_core.component.input.InputTextState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FarmingAndContactsDestinationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val ids = Triple("xor", "val", "pswap")
    private fun show(fragment: SoraBaseFragment<*>, vm: Any) {
        fragment.javaClass.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }.set(fragment, lazyOf(vm))
        compose.setContent { SoraAppTheme { val nav = rememberNavController(); val scroll = rememberScrollState()
            NavHost(nav, startDestination = theOnlyRoute) { with(fragment) { content(scroll, nav) } }
        } }
    }
    private fun click(id: Int) = compose.onNodeWithText(compose.activity.getString(id), ignoreCase = true).performClick()
    @Test fun farmDetailsPassesClaimStakeAndSupplyActionsFromTheCurrentState() {
        val state = MutableStateFlow(FarmDetailsState(ids, "XOR / VAL", "Pool value $100", "10%", "", "", "PSWAP", "", "0.1 XOR",
            poolShareStackedText = "25%", poolShareStacked = .25f, hasRewardsAvailable = true, userRewardsAmount = "2 PSWAP", hasSupplyInPool = true,
            poolIds = "xor" to "val", poolTitle = "XOR / VAL", apyText = "12%"))
        val vm = mockk<FarmDetailsViewModel>(relaxed = true) { every { this@mockk.state } returns state }
        show(FarmDetailsFragment(), vm)
        compose.onNodeWithText("2 PSWAP").assertExists()
        click(R.string.claim_rewards); click(R.string.edit_farm)
        compose.runOnIdle { state.value = state.value.copy(hasSupplyInPool = false) }
        compose.onNodeWithText(compose.activity.getString(R.string.common_supply_liquidity_title), ignoreCase = true).performClick()
        compose.runOnIdle { verify { vm.onClaim(); vm.onSupplyStacking(); vm.onSupplyLiquidity() } }
    }
    @Test fun editFarmPreservesThePercentageAndStopsConfirmingWhenDisabled() {
        val state = MutableStateFlow(EditFarmScreenState(ids, "25%", .25f, "2 XOR", "3 XOR", "0.1 XOR", "0.01 XOR", isButtonActive = true, isCardLoading = false))
        val vm = mockk<EditFarmViewModel>(relaxed = true) { every { this@mockk.state } returns state }
        show(EditFarmFragment(), vm)
        compose.onNodeWithText("25%").assertExists()
        click(R.string.common_max); click(R.string.common_confirm)
        compose.runOnIdle { verify { vm.onSliderChange(1.0); vm.onConfirm() }; state.value = state.value.copy(isButtonActive = false) }
        compose.onNodeWithText(compose.activity.getString(R.string.common_confirm), ignoreCase = true).assertIsNotEnabled()
    }
    @Test fun rewardClaimKeepsTheExactAmountAndFeeUntilExplicitConfirmation() {
        val state = MutableStateFlow(ClaimScreenState(ids, "", "1.2345 PSWAP", "$2.50", "0.01 XOR", isButtonActive = true, isLoading = false))
        val vm = mockk<ClaimDemeterViewModel>(relaxed = true) { every { this@mockk.state } returns state }
        show(ClaimDemeterFragment(), vm)
        compose.onNodeWithText("1.2345 PSWAP").assertExists(); compose.onNodeWithText("0.01 XOR").assertExists()
        click(R.string.common_claim)
        compose.runOnIdle { verify(exactly = 1) { vm.onClaim() }; state.value = state.value.copy(isButtonActive = false) }
        compose.onNodeWithText(compose.activity.getString(R.string.common_claim), ignoreCase = true).assertIsNotEnabled()
    }
    @Test fun legacyClaimDestinationReflectsSubmissionProgress() {
        val state = MutableLiveData(ClaimState(false))
        val vm = mockk<ClaimViewModel>(relaxed = true) { every { claimScreenState } returns state }
        show(ClaimFragment(), vm)
        click(R.string.common_confirm)
        compose.runOnIdle { verify(exactly = 1) { vm.nextButtonClicked() }; state.value = ClaimState(true) }
        compose.onNodeWithText(compose.activity.getString(R.string.claim_subtitle_confirmed)).assertExists()
    }
    @Test fun contactsDestinationCarriesTheSelectedTokenAlongWithTheExactRecipient() {
        val recipient = "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm"
        val state = ContactsState(listOf(ContactsListItem(recipient, ColorDrawable(Color.BLUE))), InputTextState(label = "Recipient"), R.string.address_not_found_1, false)
        val vm = mockk<ContactsViewModel>(relaxed = true) { every { this@mockk.state } returns state }
        val fragment = ContactsFragment().apply { arguments = Bundle().apply { tokenId = "fixture-token" } }
        show(fragment, vm)
        compose.onNode(hasSetTextAction()).performTextReplacement("Alice")
        compose.onNodeWithTag("jp.co.soramitsu.sora.develop:id/SearchItemN0").performClick()
        compose.runOnIdle { verify { vm.search(match { it.text == "Alice" }); vm.onContactClick(recipient, "fixture-token") } }
    }
}
