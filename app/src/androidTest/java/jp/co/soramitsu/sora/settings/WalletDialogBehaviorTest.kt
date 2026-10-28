package jp.co.soramitsu.sora.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.TransactionResultBadgeOverlay
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.presentation.transaction.TransactionResultBadge
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.ModalQrSelectionDialog
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletDialogBehaviorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun qrSelectionRoutesGalleryCameraAndDismissSeparately() {
        val events = mutableListOf<String>()
        compose.setContent { SoraAppTheme { ModalQrSelectionDialog({ events += "gallery" }, { events += "camera" }, { events += "dismiss" }) } }
        compose.onNodeWithText(compose.activity.getString(R.string.qr_upload)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.common_scan)).performClick()
        compose.runOnIdle { assertEquals(listOf("gallery", "camera"), events) }
        androidx.test.espresso.Espresso.pressBack()
        compose.runOnIdle { assertEquals(listOf("gallery", "camera", "dismiss"), events) }
    }
    @Test fun transactionOutcomeBadgesKeepSubmittedDistinctFromFailure() {
        val current = mutableStateOf(TransactionResultBadge.CONFIRMED)
        compose.setContent { SoraAppTheme { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { TransactionResultBadgeOverlay(current.value) } } }
        for (badge in TransactionResultBadge.entries) {
            compose.runOnIdle { current.value = badge }
            compose.onNodeWithText(compose.activity.getString(badge.text)).assertIsDisplayed()
        }
        assertTrue(TransactionResultBadge.SUBMITTED.positive)
        assertFalse(TransactionResultBadge.NOT_SENT.positive)
    }
    @Test fun alertDismissalClearsBothLiteralAndResourceMessages() {
        val dialog = mutableStateOf(jp.co.soramitsu.common.base.AlertDialogData("Notice", "Fixture message"))
        compose.setContent { SoraAppTheme { jp.co.soramitsu.common.presentation.compose.components.AlertDialogContent(dialog) } }
        compose.onNodeWithText("Notice").assertIsDisplayed()
        compose.onNodeWithText("Fixture message").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.common_ok)).performClick()
        compose.runOnIdle { assertEquals(jp.co.soramitsu.common.base.AlertDialogData(), dialog.value) }
        compose.onNodeWithText("Notice").assertDoesNotExist()
        compose.runOnIdle { dialog.value = jp.co.soramitsu.common.base.AlertDialogData(R.string.common_error_general_title, R.string.activity_error_title) }
        compose.onNodeWithText(compose.activity.getString(R.string.activity_error_title)).assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.runOnIdle { assertEquals(jp.co.soramitsu.common.base.AlertDialogData(), dialog.value) }
    }
    @Test fun confirmationRequiresTheExplicitConfirmActionAndClosesBeforeItsCallback() {
        val dialog = mutableStateOf<jp.co.soramitsu.common.base.ConfirmDialogData?>(null)
        val events = mutableListOf<String>()
        fun question() = jp.co.soramitsu.common.base.ConfirmDialogData("Review action", "Proceed with fixture?", "Proceed", "Keep wallet", onDismiss = {
            assertNull(dialog.value); events += "dismiss"
        }, onConfirm = { assertNull(dialog.value); events += "confirm" })
        compose.setContent { SoraAppTheme { jp.co.soramitsu.common.presentation.compose.components.ConfirmDialogContent(dialog) } }
        compose.onNodeWithText("Proceed").assertDoesNotExist()
        compose.runOnIdle { dialog.value = question() }
        compose.onNodeWithText("Review action").assertIsDisplayed()
        compose.onNodeWithText("Keep wallet").performClick()
        compose.runOnIdle { assertEquals(listOf("dismiss"), events); dialog.value = question() }
        androidx.test.espresso.Espresso.pressBack()
        compose.runOnIdle { assertEquals(listOf("dismiss", "dismiss"), events); dialog.value = question() }
        compose.onNodeWithText("Proceed").performClick()
        compose.runOnIdle { assertEquals(listOf("dismiss", "dismiss", "confirm"), events); assertNull(dialog.value) }
        compose.onNodeWithText("Proceed").assertDoesNotExist()
    }

    @Test fun transactionDetailHintExplainsTheValueAndCanBeDismissedBothWays() {
        compose.setContent { SoraAppTheme {
            jp.co.soramitsu.common.presentation.compose.components.DetailsItem(text = "Minimum received", value1 = "1.25 XOR", hint = "The final receipt cannot be lower than this quoted amount.")
        } }
        compose.onNodeWithText("The final receipt cannot be lower than this quoted amount.").assertDoesNotExist()
        compose.onNodeWithText("Minimum received").performClick()
        compose.onNodeWithText("The final receipt cannot be lower than this quoted amount.").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.common_ok)).performClick()
        compose.onNodeWithText("The final receipt cannot be lower than this quoted amount.").assertDoesNotExist()
        compose.onNodeWithText("Minimum received").performClick()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("The final receipt cannot be lower than this quoted amount.").assertDoesNotExist()
        compose.onNodeWithText("1.25 XOR").assertIsDisplayed()
    }

    @Test fun walletErrorDetailsStayHiddenUntilRequestedAndRetryHasItsOwnAction() {
        val current = mutableStateOf("NEXUS_RECIPIENT_INVALID")
        var retries = 0
        compose.setContent { SoraAppTheme {
            jp.co.soramitsu.common.presentation.compose.components.WalletErrorMessage(current.value, onRetry = { retries++ })
        } }
        val cases = listOf("NEXUS_RECIPIENT_INVALID" to R.string.wallet_issue_address,
            "NEXUS_INVALID_AMOUNT" to R.string.wallet_issue_amount, "NEXUS_INSUFFICIENT_XOR" to R.string.wallet_issue_balance,
            "NEXUS_QUOTE_EXPIRED" to R.string.wallet_issue_quote, "NEXUS_NETWORK_DISABLED" to R.string.wallet_issue_unavailable,
            "NEXUS_NETWORK_IO" to R.string.wallet_issue_connection)
        for ((code, title) in cases) {
            compose.runOnIdle { current.value = code }
            compose.onNodeWithText(compose.activity.getString(title)).assertExists()
            compose.onNodeWithText(code).assertDoesNotExist()
            compose.onNodeWithText(compose.activity.getString(R.string.wallet_technical_details)).performClick()
            compose.onNodeWithText(code).assertExists()
            compose.onNodeWithText(compose.activity.getString(R.string.wallet_hide_details)).performClick()
            compose.onNodeWithText(code).assertDoesNotExist()
            compose.onNodeWithText(compose.activity.getString(R.string.wallet_check_again)).performClick()
        }
        compose.runOnIdle { assertEquals(cases.size, retries) }
    }
    @Test fun legacyProgressDialogExposesAnIndeterminateProgressIndicator() {
        compose.runOnUiThread {
            val dialog = jp.co.soramitsu.common.presentation.view.SoraProgressDialog(compose.activity)
            try {
                dialog.show()
                assertTrue(dialog.isShowing)
                val view = dialog.findViewById<android.view.View>(android.R.id.content)
                assertNotNull(view)
                fun progress(node: android.view.View): android.widget.ProgressBar? = when (node) {
                    is android.widget.ProgressBar -> node
                    is android.view.ViewGroup -> (0 until node.childCount).firstNotNullOfOrNull { progress(node.getChildAt(it)) }
                    else -> null
                }
                assertTrue(requireNotNull(progress(view)).isIndeterminate)
            } finally { dialog.dismiss() }
            assertFalse(dialog.isShowing)
        }
    }

    @Test fun composeProgressDialogExposesAnIndeterminateProgressIndicator() {
        compose.setContent { SoraAppTheme { jp.co.soramitsu.common.presentation.view.SoraProgressDialog() } }
        compose.onNode(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
            androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate)).assertExists()
    }

}
