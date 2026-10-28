package jp.co.soramitsu.sora.navigation

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.args.*
import jp.co.soramitsu.common.presentation.compose.webview.title
import jp.co.soramitsu.common.presentation.compose.webview.url
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_impl.presentation.util.action
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection.ExportProtectionViewModel
import jp.co.soramitsu.feature_multiaccount_impl.util.type
import jp.co.soramitsu.feature_select_node_impl.presentation.*
import jp.co.soramitsu.sora.R
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.math.BigDecimal

/** Uses Android's actual Bundle and parcelable implementations at the routing boundary. */
@RunWith(AndroidJUnit4::class)
class NavigationArgumentsContractTest {
    private val controller = mockk<NavController>(relaxed = true)
    private val subject = Navigator().also { it.attachNavController(controller) }
    private val destination = slot<Int>()
    private val bundle = slot<Bundle>()
    @Before fun setup() {
        every { controller.navigate(capture(destination), capture(bundle)) } just Runs
    }
    private fun args(id: Int): Bundle { assertEquals(id, destination.captured); return bundle.captured }
    private fun pair(id: Int) { val args = args(id); assertEquals("xor", args.getString("arg_token_1")); assertEquals("val", args.getString("arg_token_2")) }
    private fun triple(id: Int) { pair(id); assertEquals("pswap", bundle.captured.getString("arg_token_3")) }
    @Test fun pinEntryPreservesTheRequestedAction() {
        subject.showPin(PinCodeAction.LOGOUT); assertEquals(PinCodeAction.LOGOUT, args(R.id.pincodeFragment).action)
    }
    @Test fun logoutPinIsScopedToExactlyOneAccount() {
        subject.showPinForLogout("owner"); val args = args(R.id.pincodeFragment)
        assertEquals(PinCodeAction.LOGOUT, args.action); assertEquals(listOf("owner"), args.addresses)
    }
    @Test fun backupPinCopiesAnImmutableAccountList() {
        subject.showPinForBackup(PinCodeAction.LOGOUT, listOf("first", "second"))
        assertEquals(listOf("first", "second"), args(R.id.pincodeFragment).addresses)
    }
    @Test fun poolDetailsPreservesOrderedPair() { subject.showPoolDetails("xor" to "val"); pair(R.id.poolDetailsFragment) }
    @Test fun farmDetailsPreservesRewardToken() { subject.showFarmDetails(Triple("xor", "val", "pswap")); triple(R.id.farmDetailsFragment) }
    @Test fun farmEditingPreservesRewardToken() { subject.showEditFarm(Triple("xor", "val", "pswap")); triple(R.id.editFarmFragment) }
    @Test fun claimingRewardsPreservesExactDecimalAmount() {
        subject.showClaimDemeter(Triple("xor", "val", "pswap"), BigDecimal("0.123456789012345678")); triple(R.id.claimDemeterFragment)
        assertEquals("0.123456789012345678", bundle.captured.getString("arg_amount"))
    }
    @Test fun assetDetailsAndHistoryUseTheirExactToken() {
        subject.showAssetDetails("asset-id"); assertEquals("asset-id", args(R.id.assetDetailsFragment).getString("assetId"))
        subject.showTxList("asset-id"); assertEquals("asset-id", args(R.id.txListFragment).tokenId)
    }
    @Test fun contactPickerPreservesTokenAndOptionalRecipient() {
        subject.showContacts("asset-id"); assertEquals("asset-id", args(R.id.contactsFragment).tokenId)
        subject.showContactsFilled("asset-id", "recipient"); assertEquals("recipient", args(R.id.contactsFragment).address)
        assertEquals("asset-id", bundle.captured.tokenId)
    }
    @Test fun transferEntryKeepsRecipientAssetAndExactAmount() {
        subject.showValTransferAmount("recipient", "asset-id", "0.000000000000000001")
        val args = args(R.id.transferAmountFragment)
        assertEquals("recipient", args.getString("arg_recipient_id")); assertEquals("asset-id", args.getString("arg_asset_id"))
        assertEquals("0.000000000000000001", args.getString("arg_initial_amount"))
        subject.showValTransferAmount("recipient", "asset-id", null)
        assertFalse(bundle.captured.containsKey("arg_initial_amount"))
    }
    @Test fun addingLiquidityKeepsOptionalSecondAssetAbsent() {
        subject.showAddLiquidity("xor", null); assertEquals("xor", args(R.id.addLiquidityFragment).getString("arg_token_1"))
        assertFalse(bundle.captured.containsKey("arg_token_2"))
        subject.showAddLiquidity("xor", "val"); pair(R.id.addLiquidityFragment)
    }
    @Test fun liquidityRemovalKeepsOrderedPair() { subject.showRemoveLiquidity("xor" to "val"); pair(R.id.liquidityRemoveFragment) }
    @Test fun scannerEntryPreservesExplicitDestinationChoice() {
        subject.openQrCodeFlow(true); assertTrue(args(R.id.qrCodeFlow).getBoolean("navigate_to_scanner_activity"))
        subject.openQrCodeFlow(false); assertFalse(bundle.captured.getBoolean("navigate_to_scanner_activity"))
    }
    @Test fun swapKeepsTokenOrderingAndSupportsUnselectedTokens() {
        subject.showSwap("xor", "val"); val args = args(R.id.swapFragment)
        assertEquals("xor", args.tokenFromId); assertEquals("val", args.tokenToId)
        subject.showSwap(null, null); assertEquals("", bundle.captured.tokenFromId); assertEquals("", bundle.captured.tokenToId)
    }
    @Test fun phraseAndSeedProtectionRemainDistinctAndAccountScoped() {
        subject.showExportPassphraseProtection("owner"); val phrase = args(R.id.exportProtectionFragment)
        assertEquals("owner", phrase.address); assertEquals(ExportProtectionViewModel.Type.PASSPHRASE, phrase.type)
        subject.showExportSeedProtection("other"); val seed = args(R.id.exportProtectionFragment)
        assertEquals("other", seed.address); assertEquals(ExportProtectionViewModel.Type.SEED, seed.type)
    }
    @Test fun encryptedExportAcceptsImmutableAddressLists() {
        subject.showExportJSONProtection(listOf("first", "second"))
        val args = args(R.id.exportProtectionFragment)
        assertEquals(listOf("first", "second"), args.addresses); assertEquals(ExportProtectionViewModel.Type.JSON, args.type)
        assertEquals("", args.address)
    }
    @Test fun encryptedExportCopiesTheListBeforeCallerChangesIt() {
        val addresses = arrayListOf("owner"); subject.showExportJSONProtection(addresses); addresses.clear()
        assertEquals(listOf("owner"), args(R.id.exportProtectionFragment).addresses)
    }
    @Test fun backupRoutesKeepTheRequestedAccountAndSecretKind() {
        subject.showBackupPassphrase("owner"); assertEquals(ExportProtectionViewModel.Type.PASSPHRASE, args(R.id.backupFragment).type)
        assertEquals("owner", bundle.captured.address)
        subject.showBackupSeed("other"); assertEquals(ExportProtectionViewModel.Type.SEED, args(R.id.backupFragment).type)
        assertEquals("other", bundle.captured.address)
        subject.showBackupJson(listOf("first", "second")); assertEquals(listOf("first", "second"), args(R.id.backupJsonFragment).addresses)
    }
    @Test fun accountOptionsKeepTheExactSelectedAddress() { subject.showAccountDetails("owner"); assertEquals("owner", args(R.id.accountDetailsFragment).address) }
    @Test fun webPagePreservesTitleAndFullURL() {
        subject.showWebView("Title", "https://example.test/path?a=1#part"); val args = args(R.id.webViewFragment)
        assertEquals("Title", args.title); assertEquals("https://example.test/path?a=1#part", args.url)
    }
    @Test fun editingCustomNodePreservesNameAndEndpoint() {
        subject.showEditNode("My node", "wss://node.example.test:443/rpc"); val args = args(R.id.nodeDetailsFragment)
        assertEquals("My node", args.nodeName); assertEquals("wss://node.example.test:443/rpc", args.nodeAddress)
    }
    @Test fun staticRoutesOpenTheirOwnDestinations() {
        val routes = listOf<Pair<() -> Unit, Int>>(
            subject::showFlexibleUpdateScreen to R.id.updateFlexibleFragment,
            subject::showSelectLanguage to R.id.selectLanguageFragment,
            subject::showPoolSettings to R.id.fullPoolListFragment,
            subject::showFullPoolsSettings to R.id.fullPoolListSettingsFragment,
            subject::showFullAssetsSettings to R.id.fullAssetsSettingsFragment,
            subject::openEditCardsHub to R.id.editCardsHub,
            subject::showInformation to R.id.informationFragment,
            subject::showDebugMenu to R.id.debugMenuFragment,
            subject::showLoginSecurity to R.id.loginSecurityFragment,
            subject::showPolkamarkt to R.id.polkamarktFragment,
            subject::showAppSettings to R.id.appSettingsFragment,
            subject::showProfile to R.id.profile_nav_graph,
            subject::showReferrals to R.id.referral_nav_graph,
            subject::showSelectNode to R.id.select_node_nav_graph,
            subject::showAddCustomNode to R.id.nodeDetailsFragment,
        )
        routes.forEach { (action, id) -> action(); verify(exactly = 1) { controller.navigate(id) } }
    }
    @Test fun returningFromAccountOptionsFallsBackToHomeOnlyWhenListIsAbsent() {
        every { controller.popBackStack(R.id.accountListFragment, false) } returns false
        subject.popBackStackToAccountList(); verify(exactly = 1) { controller.popBackStack(R.id.cardsHubFragment, false) }
        clearMocks(controller, answers = false)
        every { controller.popBackStack(R.id.accountListFragment, false) } returns true
        subject.popBackStackToAccountList(); verify(exactly = 0) { controller.popBackStack(R.id.cardsHubFragment, false) }
    }
    @Test fun leavingExportReturnsToInProgressRemovalWithoutPoppingOtherScreens() {
        val previous = mockk<NavBackStackEntry>(); val destination = mockk<NavDestination>()
        every { previous.destination } returns destination; every { destination.id } returns R.id.pincodeFragment
        every { controller.previousBackStackEntry } returns previous
        subject.leaveBackupJson(); verify(exactly = 1) { controller.popBackStack() }
        verify(exactly = 0) { controller.popBackStack(any<Int>(), any()) }
    }
    @Test fun leavingOrdinaryExportReturnsToAccountList() {
        every { controller.previousBackStackEntry } returns null
        subject.leaveBackupJson(); verify(exactly = 1) { controller.popBackStack(R.id.accountListFragment, false) }
    }
    @Test fun tokenSelectionReturnsParcelableTokensToPreviousScreen() {
        val handle = SavedStateHandle(); val previous = mockk<NavBackStackEntry>()
        every { previous.savedStateHandle } returns handle; every { controller.previousBackStackEntry } returns previous
        val token = Token("xor", "XOR", "SORA", 18, false, null, 1.0, 0.0, "$")
        subject.returnToAddLiquidity(token, null)
        val result = handle.get<Bundle>(BUNDLE_KEY)!!
        assertEquals(token, result.tokenFromNullable); assertNull(result.tokenToNullable)
        verify(exactly = 1) { controller.popBackStack() }
    }
    @Test fun completedPinCheckReturnsAnExplicitSuccessFlag() {
        val handle = SavedStateHandle(); val previous = mockk<NavBackStackEntry>()
        every { previous.savedStateHandle } returns handle; every { controller.previousBackStackEntry } returns previous
        subject.returnFromPinCodeCheck(); assertTrue(handle.get<Bundle>(BUNDLE_KEY)!!.pinCodeChecked)
        verify(exactly = 1) { controller.popBackStack() }
    }
    @Test fun lateDetachCannotRemoveTheNewController() {
        val old = mockk<NavController>(relaxed = true); subject.detachNavController(old)
        subject.showAppSettings(); verify(exactly = 1) { controller.navigate(R.id.appSettingsFragment) }
        subject.detachNavController(controller); subject.showAppSettings()
        verify(exactly = 1) { controller.navigate(R.id.appSettingsFragment) }
    }
    @Test fun currentDestinationFlagsRecognizeProtectedBackupScreens() {
        val destination = mockk<NavDestination>(); every { controller.currentDestination } returns destination
        for (id in listOf(R.id.backupFragment, R.id.backupJsonFragment, R.id.pincodeFragment, R.id.claimFragment, R.id.cardsHubFragment)) {
            every { destination.id } returns id
            assertEquals(id == R.id.backupFragment || id == R.id.backupJsonFragment, subject.currentDestinationIsPinCheckNeeded())
            assertEquals(id == R.id.pincodeFragment, subject.currentDestinationIsPincode())
            assertEquals(id == R.id.claimFragment, subject.currentDestinationIsClaimFragment())
        }
        every { controller.currentDestination } returns null
        assertFalse(subject.currentDestinationIsPinCheckNeeded()); assertFalse(subject.currentDestinationIsPincode()); assertFalse(subject.currentDestinationIsClaimFragment())
    }
    @Test fun explicitBackActionsTargetOnlyTheirNamedScreens() {
        subject.returnToHubFragment(); subject.popBackStackToAccountDetails(); subject.popBackStack(); subject.popBackStackFragment(); subject.showAccountList()
        verify { controller.popBackStack(R.id.cardsHubFragment, false) }; verify { controller.popBackStack(R.id.accountDetailsFragment, false) }
        verify(exactly = 2) { controller.popBackStack() }; verify { controller.navigate(R.id.accountListFragment, null) }
    }
}
