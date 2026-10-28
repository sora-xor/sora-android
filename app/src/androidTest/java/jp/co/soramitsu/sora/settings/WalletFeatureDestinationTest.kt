@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.settings

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.MutableLiveData
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.base.*
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common_wallet.presentation.compose.states.PoolsListState
import jp.co.soramitsu.feature_assets_impl.presentation.screens.assetdetails.*
import jp.co.soramitsu.feature_assets_impl.presentation.states.*
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_list.*
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup.*
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup.json.*
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.*
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection.*
import jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.pooldetails.*
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.PoolDetailsState
import jp.co.soramitsu.ui_core.component.input.InputTextState
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletFeatureDestinationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(fragment: SoraBaseFragment<*>, vm: Any) {
        fragment.javaClass.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }.set(fragment, lazyOf(vm))
        compose.setContent { SoraAppTheme {
            val nav = rememberNavController(); val scroll = rememberScrollState()
            NavHost(nav, startDestination = theOnlyRoute) { with(fragment) { content(scroll, nav) } }
        } }
    }
    private fun click(id: Int) = compose.onNodeWithText(compose.activity.getString(id)).performClick()

    @Test fun assetDetailsDestinationRoutesExactBalanceAndAssetActions() {
        val state = AssetCardState(false, emptyAssetCardState.copy(tokenId = "fixture-token", tokenName = "Fixture asset", tokenSymbol = "FIX", transferableBalance = "4 FIX", transferableBalanceFiat = "$8", isTransferableBalanceAvailable = true, hasTokens = true))
        val vm = mockk<AssetDetailsViewModel>(relaxed = true) { every { this@mockk.state } returns state }
        show(AssetDetailsFragment(), vm)
        click(R.string.common_send); click(R.string.common_receive); click(R.string.polkaswap_swap_title)
        compose.onNodeWithText("4 FIX").performScrollTo().performClick()
        compose.onNodeWithText("fixture-token").performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.sendClicked(); vm.receiveClicked(); vm.swapClicked(); vm.onBalanceClick(); vm.onAssetIdClick() } }
    }

    @Test fun poolDetailsDestinationRoutesSupplyAndRemovalAndUpdatesEligibility() {
        val state = mutableStateOf(PoolDetailsState("", "", "", "PSWAP", "XOR", "VAL", "12%", "2.5 XOR", "5 VAL", "TVL $100", true, true, "10%", emptyList(), emptyList(), false))
        val vm = mockk<PoolDetailsViewModel>(relaxed = true) { every { detailsState } answers { state.value } }
        show(PoolDetailsFragment(), vm)
        click(R.string.common_supply); click(R.string.common_remove)
        compose.runOnIdle { verify(exactly = 1) { vm.onSupply(); vm.onRemove() }; state.value = state.value.copy(addEnabled = false, removeEnabled = false) }
        compose.onNodeWithText(compose.activity.getString(R.string.common_remove)).assertIsNotEnabled()
        compose.onNodeWithText(compose.activity.getString(R.string.common_supply)).assertIsNotEnabled()
    }

    @Test fun backupDestinationRevealsSeedBeforeAllowingCopy() {
        val state = MutableLiveData(BackupScreenState(seedString = "01".repeat(32)))
        val vm = mockk<BackupViewModel>(relaxed = true) { every { backupScreenState } returns state }
        show(BackupFragment(), vm)
        compose.onNodeWithText(compose.activity.getString(R.string.copy_to_clipboard)).assertIsNotEnabled()
        compose.onNodeWithTag("BackupSeedReveal").performClick()
        compose.onNodeWithTag("BackupSeed").assertTextEquals("01".repeat(32))
        click(R.string.copy_to_clipboard)
        compose.runOnIdle { verify(exactly = 1) { vm.backupPressed() } }
    }

    @Test fun backupJsonDestinationForwardsPasswordsAndValidatedDownload() {
        val state = MutableLiveData(BackupJsonScreenState(InputTextState(label = "Password"), InputTextState(label = "Confirmation"), buttonEnabledState = true))
        val vm = mockk<BackupJsonViewModel>(relaxed = true) { every { backupJsonScreenState } returns state }
        show(BackupJsonFragment(), vm)
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("public-fixture")
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("public-confirmation")
        compose.onAllNodes(hasSetTextAction())[1].performImeAction()
        compose.onNodeWithText(compose.activity.getString(R.string.export_json_download_json).uppercase()).performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.passwordInputChanged(match { it.text == "public-fixture" }); vm.confirmationInputChanged(match { it.text == "public-confirmation" }); vm.downloadJsonClicked() } }
    }

    @Test fun exportProtectionDestinationForwardsAcknowledgementAndContinue() {
        val state = MutableLiveData(ExportProtectionScreenState(R.string.common_passphrase_title, R.string.backup_password_requirments,
            listOf(ExportProtectionSelectableModel(false, R.string.create_backup_password_warning_text)), true))
        val vm = mockk<ExportProtectionViewModel>(relaxed = true) { every { exportProtectionScreenState } returns state }
        show(ExportProtectionFragment(), vm)
        click(R.string.create_backup_password_warning_text)
        compose.onNodeWithText(compose.activity.getString(R.string.common_continue).uppercase()).performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onItemClicked(0); vm.continueClicked() } }
    }

    @Test fun accountListDestinationRoutesSelectionAndAddAndShowsSwitchProgress() {
        val account = SoraAccount("fixture-address", "Fixture wallet")
        val state = MutableLiveData(AccountListScreenState(accountList = listOf(ExportAccountData(false, false, ColorDrawable(Color.BLUE), account)), isActionMode = false))
        val switching = MutableLiveData(false)
        val vm = mockk<AccountListViewModel>(relaxed = true) { every { accountListScreenState } returns state; every { isSwitchingAccount } returns switching }
        show(AccountListFragment(), vm)
        compose.onNodeWithText("Fixture wallet").performClick()
        compose.onNodeWithTag("jp.co.soramitsu.sora.develop:id/FloatingButton").performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onAccountClicked(account.substrateAddress); vm.onAddAccountClicked() }; switching.value = true }
        compose.onNodeWithText(compose.activity.getString(R.string.account_switching)).assertIsDisplayed()
        compose.runOnIdle { switching.value = false; state.value = state.value!!.copy(isActionMode = true) }
        compose.onNodeWithTag("jp.co.soramitsu.sora.develop:id/FloatingButton").assertDoesNotExist()
    }
}
