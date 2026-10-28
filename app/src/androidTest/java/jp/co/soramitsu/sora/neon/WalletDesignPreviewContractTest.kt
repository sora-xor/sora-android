package jp.co.soramitsu.sora.neon

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.tooling.PreviewActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Real Android Studio preview intent contract for checked-in wallet design fixtures.
 * These fixtures contain only sample values and no repositories, signers or network operations.
 * Explicit semantic expectations detect missing content and incorrect preview bindings.
 */
@RunWith(Parameterized::class)
class WalletDesignPreviewContractTest(private val preview: Fixture) {
    data class Fixture(val target: String, val expected: List<String>) {
        override fun toString() = target.substringAfterLast('.')
    }
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun previewRendersItsDeclaredWalletStateAndSupportsLocalEditing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<PreviewActivity>(Intent(context, PreviewActivity::class.java).putExtra("composable", preview.target)).use {
            compose.waitForIdle()
            for (expected in preview.expected) {
                val label = if (expected.startsWith("@string/")) context.getString(context.resources.getIdentifier(expected.removePrefix("@string/"), "string", context.packageName)) else expected
                assertTrue("${preview.target} must expose '$label'", compose.onAllNodesWithText(label, substring = true, ignoreCase = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
            }
            if ((preview.target.endsWith("PreviewLoadableContentCard_LOADING") || preview.target.endsWith("EditFarmScreenKt.PreviewEditFarms"))) {
                compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo)).assertExists()
                compose.onNodeWithText("Loaded").assertDoesNotExist()
            }
            if (preview.target.endsWith("BackupScreenKt.PreviewBackup")) {
                compose.onAllNodesWithText("abcde", useUnmergedTree = true).assertCountEquals(0)
                compose.onAllNodesWithText("Tap to reveal").onFirst().performClick()
                compose.onAllNodesWithText("abcde", useUnmergedTree = true).assertCountEquals(12)
            }
            if (preview.target.contains("AssetAmountKt.PreviewAssetAmount")) {
                compose.onNodeWithText("click").performClick()
                compose.onAllNodesWithText("12345.67890988765", useUnmergedTree = true).assertCountEquals(2)
                compose.onNodeWithText("click").performClick()
                compose.onAllNodesWithText("12346.67890988765", useUnmergedTree = true).assertCountEquals(2)
            }
        }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: {0}")
        fun fixtures(): List<Array<Fixture>> = listOf(
            arrayOf(Fixture("jp.co.soramitsu.feature_multiaccount_impl.presentation.import_account_list.import_account_password.ImportAccountPasswordScreenKt.PreviewEnterPassphraseScreen", listOf("aa", "add", "@string/enter_password_description"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_multiaccount_impl.presentation.import_account_list.import_account_success.ImportAccountSuccessScreenKt.PreviewEnterPassphraseScreen", listOf("aa", "add", "@string/succesfully_imported_account_title"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.LiquidityAddScreenKt.PreviewLiquidityRemoveScreen", listOf("XOR", "0.34"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.LiquidityRemoveScreenKt.PreviewLiquidityRemoveScreen", listOf("XOR", "0.3"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.LiquidityAddConfirmScreenKt.PreviewLiquidityRemoveConfirmScreen", listOf("Output is estimated", "1.999 XOR"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.LiquidityRemoveConfirmScreenKt.PreviewLiquidityRemoveConfirmScreen", listOf("Output is estimated", "1.999 XOR"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_assets_impl.presentation.screens.assetdetails.AssetDetailsScreenKt.PreviewAssetDetailsScreen01", listOf("XOR", "12314324.13"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_assets_impl.presentation.screens.assetdetails.AssetDetailsScreenKt.PreviewAssetDetailsScreen02", listOf("XOR", "12314324.13"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_select_node_impl.presentation.select.ui.SelectNodeScreenKt.PreviewSelectNode", listOf("wss://mof2.sora.org", "wss://node-a.example"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_blockexplorer_api.presentation.screen.EventTransferSentKt.PreviewEventTransferSent", listOf("1123141.1235 XOR"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_ecosystem_impl.presentation.farmdetails.FarmDetailsScreenKt.PreviewAllPoolsInternal", listOf("XOR-VAL Farm", "123,294.53"))),
            arrayOf(Fixture("jp.co.soramitsu.common.presentation.compose.components.AssetAmountKt.PreviewAssetAmountInput01", listOf("click"))),
            arrayOf(Fixture("jp.co.soramitsu.common.presentation.compose.components.AssetAmountKt.PreviewAssetAmountInput02", listOf("click"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_list.AccountListScreenKt.PreviewList01", listOf("name 1", "name 2", "name 3"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_list.AccountListScreenKt.PreviewList02", listOf("name 1", "name 2", "name 3"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_referral_impl.presentation.ReferralProgramPageKt.PreviewWelcomePageScreen", listOf("Available Invitations", "Invitations Link"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_wallet_impl.presentation.contacts.ContactsScreenKt.PreviewContactsScreen", listOf("Search or add a new address", "cnVko"))),
            arrayOf(Fixture("jp.co.soramitsu.common_wallet.presentation.compose.components.PoolsListKt.PreviewPoolsList", listOf("XOR", "VAL", "PSWAP"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details.AccountDetailsScreenBasicKt.PreviewAccountDetailsScreenBasic01", listOf("Name", "bla"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details.AccountDetailsScreenBasicKt.PreviewAccountDetailsScreenBasic02", listOf("Name", "bla"))),
            arrayOf(Fixture("jp.co.soramitsu.common.presentation.compose.components.PercentContainerKt.PreviewPercentContainer", listOf("text1"))),
            arrayOf(Fixture("jp.co.soramitsu.common.presentation.compose.components.OptionSwitchKt.PreviewOptions", listOf("label 1", "label 2", "label 3"))),
            arrayOf(Fixture("jp.co.soramitsu.common.presentation.compose.uikit.organisms.PagerTextIndicatorKt.PreviewSlidingPagerIndicator", listOf("1234512345", "3"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_assets_impl.presentation.components.compose.send.SendScreenKt.PreviewSendScreen", listOf("cnVko", "XOR"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.PoolDetailsScreenKt.PreviewPoolDetailsScreen", listOf("XOR", "VAL", "34.999"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_main_impl.presentation.language.SelectLanguageFragmentKt.PreviewLanguagesList", listOf("lang 1", "lang 2", "lang 3"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_main_impl.presentation.profile.information.InformationScreenKt.PreviewInformation", listOf("App version 2.3.4.5"))),
            arrayOf(Fixture("jp.co.soramitsu.common.presentation.compose.components.OptionKt.PreviewOptions", listOf("label 1", "label 2", "label 3 desc"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_referral_impl.presentation.ReferralBondUnbondXorKt.PreviewReferralBondXor01", listOf("0.098 XOR", "123.56743 XOR"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_referral_impl.presentation.ReferralBondUnbondXorKt.PreviewReferralBondXor02", listOf("0.098 XOR", "123.56743 XOR"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.SwapConfirmScreenKt.Preview", listOf("Estimated", "Button"))),
            arrayOf(Fixture("jp.co.soramitsu.common.presentation.compose.components.DetailsItemKt.PreviewSwapDetailsItem", listOf("Min received", "1.234 XOR", "0.4 %"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_assets_impl.presentation.components.compose.send.SendConfirmScreenKt.PreviewSendConfirmScreen", listOf("0.007 XOR", "cnVko"))),
            arrayOf(Fixture("jp.co.soramitsu.common.presentation.compose.uikit.organisms.LoadableContentCardKt.PreviewLoadableContentCard_READY_TO_RENDER", listOf("Loaded"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.SwapMarketSlippageSelectorKt.Preview", listOf("Smart", "0.15%"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.TxDetailsDemeterStakeKt.PreviewTxDetailsAddLiquidity", listOf("123.23 XOR", "87987.23 VAL"))),
            arrayOf(Fixture("jp.co.soramitsu.common_wallet.presentation.compose.BasicDemeterListItemKt.PreviewBasicFarmListItem", listOf("XOR-ETH", "Reward: 78.975 XSTUSD"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_referral_impl.presentation.ReferralAvailableInvitationsKt.PreviewReferralAvailableInvitationsCard", listOf("Available invitations", "0.0008 XOR"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.SwapAmountSquareKt.Preview", listOf("123.144 XOR", "234.34"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup.MnemonicWordsColumnKt.PreviewMnemonic", listOf("97", "103"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup.MnemonicWordsColumnKt.PreviewMnemonicRtl", listOf("97", "103"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup.BackupScreenKt.PreviewBackup", listOf("Tap to reveal"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_ecosystem_impl.presentation.claimdemeter.ClaimDemeterScreenKt.PreviewEditFarms", listOf("100 DAI", "0.007 XOR"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.TxDetailsLiquidityKt.PreviewTxDetailsAddLiquidity", listOf("123.23 XOR", "87987.23 VAL"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_assets_impl.presentation.components.compose.receive.ReceiveScreenKt.PreviewReceiveScreen", listOf("name", "cnVko"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetdetails.AssetDetailsRecentActivityCardKt.PreviewAssetDetailsRecentActivityCard", listOf("123.123132 XOR"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_select_node_impl.presentation.details.NodeDetailsScreenKt.PreviewNodeDetailsScreen", listOf("Node name", "Node address"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup.json.BackupJsonScreenKt.PreviewJson", listOf("set password", "confirm password"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.TxDetailsSwapKt.PreviewTxSwapDetails", listOf("123.23 XOR", "87987.23 VAL"))),
            arrayOf(Fixture("jp.co.soramitsu.common.view.WarningTextCardKt.PreviewWarningTextCard", listOf("Warning", "Abracabra"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_referral_impl.presentation.ReferrerInputKt.PreviewReferrerInput", listOf("Activate"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.TxDetailsReferralAndTransferKt.PreviewReferrerTxCard", listOf("123.123 XOR"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_wallet_impl.presentation.cardshub.CardsHubScreenKt.PreviewCommonHubCard", listOf("text1", "text2"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_referral_impl.presentation.YourReferrerCardKt.PreviewYourReferrerCard", listOf("Available invitations"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetdetails.AssetDetailsBalanceCardKt.PreviewAssetDetailsBalanceCard", listOf("54.2353434 XOR", "12.3 XOR"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_referral_impl.presentation.YourReferralsCardKt.PreviewYourReferralsCard", listOf("1.212 XOR", "7.465 XOR"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_referral_impl.presentation.ReferrerFilledKt.PreviewReferrerFilled", listOf("address", "Close"))),
            arrayOf(Fixture("jp.co.soramitsu.common.presentation.compose.components.BasicBannerCardKt.PreviewBasicBannerCard2", listOf("Title", "Description", "Button"))),
            arrayOf(Fixture("jp.co.soramitsu.common.presentation.compose.components.BasicBannerCardKt.PreviewBasicBannerCard12", listOf("Some title", "Long description of banner", "Just button title"))),
            arrayOf(Fixture("jp.co.soramitsu.common.presentation.compose.components.BasicBannerCardKt.PreviewBasicBannerCard1", listOf("Some title of banner card", "Long description of banner card", "Just button title"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.BasicTxDetailsKt.PreviewBasicTxDetails", listOf("imageContent", "amountContent"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_multiaccount_impl.presentation.mnemonic_confirmation.MnemonicConfirmationScreenKt.PreviewMnemonicConfirmationScreen", listOf("First", "Second", "Thrird"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetdetails.AssetDetailsTokenPriceCardKt.PreviewAssetDetailsTokenPriceCard", listOf("SORA", "XOR", "12.34"))),
            arrayOf(Fixture("jp.co.soramitsu.common.presentation.compose.uikit.molecules.ListTileKt.PreviewListTile", listOf("Title", "Body text"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_main_impl.presentation.profile.debugmenu.DebugMenuScreenKt.PreviewOptions", listOf("User ID", "Device Name"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetdetails.AssetIdCardKt.PreviewCard", listOf("0x0200050000000000000000000000000000000000000000000000000000000000"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.SwapMainScreenKt.PreviewSwapMainScreen", listOf("btn", "XOR", "0.2"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection.ExportProtectionScreenKt.ExportProtectionScreenPreview", listOf("@string/export_protection_seed_description", "@string/export_protection_seed_1"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_wallet_impl.presentation.editcardshub.EditCardsHubScreenKt.PreviewEditCardsHubScreen", listOf("@string/pooled_assets"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_ecosystem_impl.presentation.editfarm.EditFarmScreenKt.PreviewEditFarms", listOf())),
            arrayOf(Fixture("jp.co.soramitsu.feature_main_impl.presentation.profile.appsettings.AppSettingsScreenKt.PreviewAppSettingsScreen", listOf("@string/appearance_title", "@string/test_networks"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details.AccountDetailsScreenKt.PreviewBackup01", listOf("@string/common_passphrase_title"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details.AccountDetailsScreenKt.PreviewBackup02", listOf("@string/common_passphrase_title"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_ecosystem_impl.presentation.allpools.AllPoolsScreenKt.PreviewAllPoolsInternal", listOf("@string/discovery_polkaswap_pools"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_ecosystem_impl.presentation.allcurrencies.AllCurrenciesScreenKt.PreviewAllCurrenciesInternal", listOf("XOR"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_multiaccount_impl.presentation.tutorial.TutorialScreenKt.PreviewTutorialScreen", listOf("@string/create_account_title", "@string/recovery_title"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_multiaccount_impl.presentation.backup_password.BackupPasswordScreenKt.PreviewCreateAccountScreen", listOf("@string/backup_password_title_2", "@string/create_backup_password_button_text"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.SwapSlippageScreenKt.PreviewSwapSlippageScreen01", listOf("0.5"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.SwapSlippageScreenKt.PreviewSwapSlippageScreen02", listOf("0.5"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_main_impl.presentation.profile.login.LoginSecurityScreenKt.PreviewLoginSecurityScreen", listOf("@string/change_pin", "@string/profile_biometry_title"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.SwapMarketsScreenKt.PreviewSwapMarketsScreen", listOf("Smart"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_multiaccount_impl.presentation.import_account_list.ImportAccountListScreenKt.PreviewEnterPassphraseScreen", listOf("@string/create_new_account_title"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_multiaccount_impl.presentation.enter_passphrase.EnterPassphraseScreenKt.PreviewEnterPassphraseScreen", listOf("@string/common_passphrase_title", "@string/common_continue"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_multiaccount_impl.presentation.create_account.CreateAccountScreenKt.PreviewCreateAccountScreen", listOf("@string/onboarding_create_account_description", "@string/common_continue"))),
            arrayOf(Fixture("jp.co.soramitsu.common.presentation.compose.components.PolkaswapDisclaimerKt.PreviewPolkaswapDisclaimer01", listOf("@string/polkaswap_info_title", "@string/common_close"))),
            arrayOf(Fixture("jp.co.soramitsu.common.presentation.compose.components.PolkaswapDisclaimerKt.PreviewPolkaswapDisclaimer02", listOf("@string/polkaswap_info_title", "@string/common_close"))),
            arrayOf(Fixture("jp.co.soramitsu.common.presentation.compose.components.AssetSelectionKt.Preview", listOf("XOR"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_wallet_impl.presentation.cardshub.BackupCardKt.PreviewBackupCard", listOf("@string/wallet_backup", "@string/backup_now"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_main_impl.presentation.inappupdate.InAppUpdateScreenKt.PreviewInAppUpdateScreen", listOf("@string/restart_required", "@string/common_cancel"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_wallet_impl.presentation.contacts.ModalQrSelectionDialogKt.PreviewModalQrSelectionDialog", listOf("@string/qr_upload", "@string/common_scan"))),
            arrayOf(Fixture("jp.co.soramitsu.common.presentation.compose.components.PercentBarKt.PreviewPercentageBar", listOf("25%", "50%", "75%", "100%"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_blockexplorer_api.presentation.screen.TxHistoryErrorScreensKt.PreviewTxHistoryErrorScreen", listOf("@string/activity_error_title", "@string/common_refresh"))),
            arrayOf(Fixture("jp.co.soramitsu.common.presentation.compose.uikit.organisms.LoadableContentCardKt.PreviewLoadableContentCard_LOADING", listOf())),
            arrayOf(Fixture("jp.co.soramitsu.common.presentation.compose.uikit.organisms.LoadableContentCardKt.PreviewLoadableContentCard_ERROR", listOf("@string/common_error_general_title"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_assets_impl.presentation.components.compose.receiverequest.QRCodeMainScreenKt.PreviewQrCodeMainScreen", listOf("@string/common_receive", "@string/common_request"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_assets_impl.presentation.components.compose.receiverequest.RequestTokenScreenKt.PreviewRequestTokenScreen", listOf("@string/common_create_qr_request", "XOR"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_referral_impl.presentation.screens.ReferralWelcomePageKt.PreviewWelcomePageScreen", listOf("@string/referral_title", "@string/referral_start_inviting"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_main_impl.presentation.profile.ProfileScreenKt.PreviewProfile", listOf("@string/settings_crypto_accounts", "@string/polkamarkt_title"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_blockexplorer_api.presentation.screen.EventDemeterKt.PreviewEventTransferReceived", listOf("@string/demeter_claimed_reward", "@string/demeter_staked_liquidity"))),
            arrayOf(Fixture("jp.co.soramitsu.common.presentation.compose.components.NetworkFeeUnavailableNoticeKt.PreviewNetworkFeeUnavailableNotice", listOf("@string/network_fee_unreachable", "@string/common_try_again"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_assets_impl.presentation.components.compose.receiverequest.RequestTokenConfirmScreenKt.PreviewRequestTokenConfirmScreen", listOf("@string/common_share", "XOR"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_assets_impl.presentation.components.compose.receiverequest.ReceiveTokenScreenKt.PreviewReceiveTokenByQrScreen", listOf("@string/common_share", "@string/commom_scan_qr"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_assets_impl.presentation.components.compose.scan.QrCodeScannerScreenKt.PreviewQrCodeScannerScreen", listOf("@string/common_upload_from_library", "@string/scan_qr_show_my_qr"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_ecosystem_impl.presentation.alldemeter.AllDemeterScreenKt.PreviewAllPoolsInternal", listOf("@string/explore_demeter_title"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_blockexplorer_api.presentation.screen.EventTransferReceivedKt.PreviewEventTransferReceived", listOf("@string/common_received"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_blockexplorer_api.presentation.screen.EventSwapKt.PreviewEventSwap", listOf("@string/polkaswap_swapped"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_blockexplorer_api.presentation.screen.EventReferralKt.PreviewEventReferral", listOf("@string/activity_referral_title"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_blockexplorer_api.presentation.screen.EventEthTransferKt.PreviewEventEthTransfer", listOf("@string/common_bridged"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_blockexplorer_api.presentation.screen.EventAdarIncomeKt.PreviewEventTransferReceived", listOf("@string/received_from_adar"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_assets_api.presentation.selectsearchtoken.SelectSearchTokenKt.PreviewSelectSearchTokenCard1", listOf("XOR"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_assets_api.presentation.selectsearchtoken.SelectSearchTokenKt.PreviewSelectSearchTokenCard2", listOf("@string/search_empty_state"))),
            arrayOf(Fixture("jp.co.soramitsu.common_wallet.presentation.compose.components.AssetItemKt.PreviewAssetItemEnumerated", listOf("123", "XOR"))),
            arrayOf(Fixture("jp.co.soramitsu.common_wallet.presentation.compose.BasicPoolListItemKt.PreviewBasicPoolListItem", listOf("XOR"))),
            arrayOf(Fixture("jp.co.soramitsu.feature_wallet_impl.presentation.cardshub.ReferralCardKt.PreviewReferralCard", listOf("@string/referral_title", "@string/referral_start_inviting"))),
        )
    }
}
