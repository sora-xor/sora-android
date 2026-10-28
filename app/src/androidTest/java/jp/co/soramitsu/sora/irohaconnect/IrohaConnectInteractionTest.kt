package jp.co.soramitsu.sora.irohaconnect

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.irohaconnect.*
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IrohaConnectInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val actions = mutableListOf<String>()
    private fun review() = IrohaConnectPairingReview("Test app", "https://example.test", "Taira Testnet", "taira",
        IrohaConnectPermissions(listOf("sign_raw", "sign_transaction"), emptyList(), listOf("test.scope")), "abcd1234ef567890", Long.MAX_VALUE)
    private fun signing(readable: String? = "Approve this exact message", kind: IrohaConnectSigningKind = IrohaConnectSigningKind.RAW) =
        IrohaConnectSigningReview("request-1", kind, "test.scope", 26, "deadbeef", Long.MAX_VALUE, readable)
    private fun state(phase: IrohaConnectUiPhase) = IrohaConnectUiState(phase, "Main wallet", "canonical-address", "Taira Testnet", appName = "Test app")
    private fun show(current: androidx.compose.runtime.State<IrohaConnectUiState>) = compose.setContent { SoraAppTheme {
        IrohaConnectScreen(current.value, { actions += "pair" }, { actions += "decline" }, { actions += "sign" }, { actions += "reject" }, { actions += "close" })
    } }
    private fun node(id: Int) = compose.onNodeWithText(compose.activity.getString(id))

    @Test fun pairingExplainsAccountAndScopeBeforeExplicitApproval() {
        show(mutableStateOf(state(IrohaConnectUiPhase.PAIRING).copy(pairing = review())))
        for (text in listOf("https://example.test", "Taira Testnet", "Main wallet\ncanonical-address", "test.scope")) compose.onNodeWithText(text).assertExists()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
        compose.onNodeWithTag("approve_pairing").performScrollTo().performClick()
        node(R.string.wallet_connect_action_decline).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("pair", "decline"), actions) }
    }
    @Test fun sessionFingerprintIsOptionalAndCanBeHiddenAgain() {
        show(mutableStateOf(state(IrohaConnectUiPhase.PAIRING).copy(pairing = review())))
        compose.onNodeWithText("abcd 1234 ef56 7890").assertDoesNotExist()
        node(R.string.wallet_connect_session_details).performScrollTo().performClick()
        compose.onNodeWithText("abcd 1234 ef56 7890").assertExists()
        node(R.string.wallet_connect_hide_session).performScrollTo().performClick()
        compose.onNodeWithText("abcd 1234 ef56 7890").assertDoesNotExist()
    }
    @Test fun missingWebsiteIsExplicitAndAbsentScopeIsNotInvented() {
        show(mutableStateOf(state(IrohaConnectUiPhase.PAIRING).copy(pairing = review().copy(appUrl = null, permissions = null))))
        node(R.string.wallet_connect_missing_website).assertExists()
        node(R.string.wallet_connect_scopes).assertDoesNotExist()
        node(R.string.wallet_connect_view_address).assertExists()
    }
    @Test fun readableSigningShowsExactMessageAndRequiresExplicitAction() {
        show(mutableStateOf(state(IrohaConnectUiPhase.SIGNING).copy(signing = signing())))
        compose.onNodeWithText("Approve this exact message").assertExists()
        compose.onNodeWithText("test.scope").assertExists()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
        compose.onNodeWithTag("approve_signing").performScrollTo().performClick()
        compose.onNodeWithTag("reject_signing").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("sign", "reject"), actions) }
    }
    @Test fun opaqueTransactionHasNoSigningActionButCanBeRejected() {
        show(mutableStateOf(state(IrohaConnectUiPhase.SIGNING).copy(signing = signing(null, IrohaConnectSigningKind.TRANSACTION))))
        node(R.string.wallet_connect_unsupported_transaction).assertExists()
        compose.onNodeWithTag("approve_signing").assertDoesNotExist()
        compose.onNodeWithTag("reject_signing").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("reject"), actions) }
    }
    @Test fun opaqueMessageHasNoSigningActionAndTechnicalDetailsResetForANewRequest() {
        val current = mutableStateOf(state(IrohaConnectUiPhase.SIGNING).copy(signing = signing(null)))
        show(current)
        node(R.string.wallet_connect_unsupported_message).assertExists()
        compose.onNodeWithTag("approve_signing").assertDoesNotExist()
        node(R.string.wallet_connect_technical).performScrollTo().performClick()
        compose.onNodeWithText("deadbeef").assertExists()
        node(R.string.wallet_connect_hide_technical).performScrollTo().performClick()
        compose.onNodeWithText("deadbeef").assertDoesNotExist()
        node(R.string.wallet_connect_technical).performScrollTo().performClick()
        compose.runOnIdle { current.value = current.value.copy(signing = signing(null).copy(requestToken = "request-2")) }
        compose.onNodeWithText("deadbeef").assertDoesNotExist()
    }
    @Test fun terminalStatesShowTheirReasonAndOnlyOfferDone() {
        val current = mutableStateOf(state(IrohaConnectUiPhase.ERROR).copy(error = "The wallet changed"))
        show(current)
        compose.onNodeWithText("The wallet changed").assertExists()
        node(R.string.wallet_connect_stopped).assertExists()
        compose.onNodeWithTag("terminal_action").performClick()
        compose.runOnIdle { current.value = state(IrohaConnectUiPhase.CLOSED).copy(message = "Application finished") }
        compose.onNodeWithText("Application finished").assertExists()
        node(R.string.wallet_connect_closed).assertExists()
        compose.onNodeWithTag("terminal_action").performClick()
        compose.runOnIdle { assertEquals(listOf("close", "close"), actions) }
    }
    @Test fun connectionAndAuthorizationStatesNeverOfferAutomaticSigning() {
        val current = mutableStateOf(state(IrohaConnectUiPhase.LOADING))
        show(current)
        for (phase in listOf(IrohaConnectUiPhase.LOADING, IrohaConnectUiPhase.CONNECTING, IrohaConnectUiPhase.WAITING_FOR_APP, IrohaConnectUiPhase.AUTHORIZING, IrohaConnectUiPhase.CONNECTED)) {
            compose.runOnIdle { current.value = state(phase) }
            compose.onNodeWithTag("approve_pairing").assertDoesNotExist()
            compose.onNodeWithTag("approve_signing").assertDoesNotExist()
            compose.onNodeWithTag("close_session").performScrollTo().performClick()
        }
        compose.runOnIdle { assertEquals(List(5) { "close" }, actions) }
    }
}
