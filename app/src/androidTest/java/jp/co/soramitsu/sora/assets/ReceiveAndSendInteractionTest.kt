@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.assets

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LocalSelectedAccountReadOnly
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.ScreenStatus
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.receive.ReceiveScreen
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.receiverequest.*
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.send.SendConfirmScreen
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalFoundationApi::class)
class ReceiveAndSendInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val events = mutableListOf<String>()
    private val readOnly = mutableStateOf(false)
    private val address = "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm"
    private val qr = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
    private val avatar by lazy { BitmapDrawable(compose.activity.resources, qr) }
    private fun amount() = AssetAmountInputState(Token("xor", "SORA", "XOR", 18, true, null, 1.0, 0.0, "$"),
        "12 XOR", BigDecimal("1.234567890123456789"), "$1.23", true)
    private fun receive() = ReceiveTokenByQrScreenState(ScreenStatus.READY_TO_RENDER, qr, "Receiving wallet", avatar, address)
    private fun request() = RequestTokenScreenState(ScreenStatus.READY_TO_RENDER, "Receiving wallet", avatar, address, amount())
    private fun confirm() = RequestTokenConfirmScreenState(ScreenStatus.READY_TO_RENDER, qr, "Receiving wallet", avatar, address, amount().copy(readOnly = true))
    private fun node(id: Int) = compose.onNodeWithText(compose.activity.getString(id))
    private fun show(content: @Composable () -> Unit) = compose.setContent {
        SoraAppTheme { CompositionLocalProvider(LocalSelectedAccountReadOnly provides readOnly.value) { content() } }
    }
    private fun showReceive(state: State<ReceiveTokenByQrScreenState>) = show {
        ReceiveTokenByQrScreen(rememberScrollState(), state.value, { events += "copy" }, { events += "share" }, { events += "scan" }, { events += "retry" })
    }
    private fun showRequest(state: State<RequestTokenScreenState>) = show {
        RequestTokenScreen(0, rememberPagerState { 2 }, rememberScrollState(), state.value,
            { events += "copy" }, { events += "amount:$it" }, { events += "token" }, { events += "create" }, { events += "retry" })
    }
    private fun showConfirm(state: State<RequestTokenConfirmScreenState>) = show {
        RequestTokenConfirmScreen(rememberScrollState(), state.value, { events += "copy" }, { events += "share" }, { events += "retry" })
    }
    private fun showSend(enabled: State<Boolean> = mutableStateOf(true), busy: State<Boolean> = mutableStateOf(false)) = show {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SendConfirmScreen(address, avatar, { events += "copy" }, { events += "long-copy" }, amount().copy(readOnly = true),
                { events += "confirm" }, busy.value, "0.007 XOR", "$0.01", enabled.value)
        }
    }
    @Before fun setup() { WalletRecoveryCapabilityGate.enterNormal() }
    @After fun cleanup() { WalletRecoveryCapabilityGate.enterNormal(); qr.recycle() }

    @Test fun receiveShowsTheExactAccountAndRoutesCopyShareAndScanner() {
        showReceive(mutableStateOf(receive()))
        compose.onNodeWithText("Receiving wallet").performScrollTo().performClick()
        compose.onNodeWithText(address).assertExists()
        node(R.string.common_share).performScrollTo().performClick()
        node(R.string.commom_scan_qr).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("copy", "share", "scan"), events) }
    }
    @Test fun receiveLoadingHidesOldIdentityAndDisablesSharing() {
        val state = mutableStateOf(receive()); showReceive(state)
        compose.runOnIdle { state.value = state.value.copy(screenStatus = ScreenStatus.LOADING) }
        compose.onNodeWithText(address).assertDoesNotExist()
        node(R.string.common_share).performScrollTo().assertIsNotEnabled()
        node(R.string.commom_scan_qr).performScrollTo().assertIsEnabled()
    }
    @Test fun receiveErrorCanRetryWithoutSharingStaleQr() {
        showReceive(mutableStateOf(receive().copy(screenStatus = ScreenStatus.ERROR)))
        compose.onAllNodesWithText(compose.activity.getString(R.string.common_error_general_title))[0].performClick()
        node(R.string.common_share).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(listOf("retry"), events) }
    }
    @Test fun receiveCannotShareWithoutAnActualQrBitmap() {
        showReceive(mutableStateOf(receive().copy(untransformedQrBitmap = null)))
        node(R.string.common_share).performScrollTo().assertIsNotEnabled()
    }
    @Test fun receiveCannotShareAnEmptyAddress() {
        showReceive(mutableStateOf(receive().copy(untransformedUserAddress = " ")))
        node(R.string.common_share).performScrollTo().assertIsNotEnabled()
    }
    @Test fun changedRecipientReplacesOldNameAndAddress() {
        val state = mutableStateOf(receive()); showReceive(state)
        compose.runOnIdle { state.value = state.value.copy(untransformedUserName = "Another wallet", untransformedUserAddress = "another-address") }
        compose.onNodeWithText(address).assertDoesNotExist()
        compose.onNodeWithText("Receiving wallet").assertDoesNotExist()
        compose.onNodeWithText("another-address").performScrollTo().assertIsDisplayed()
    }
    @Test fun requestForwardsExactDecimalAndTokenSelectionBeforeCreation() {
        showRequest(mutableStateOf(request()))
        compose.onNode(hasSetTextAction()).performTextReplacement("2.000000000000000001")
        compose.onNodeWithText("XOR", useUnmergedTree = true).performClick()
        node(R.string.common_create_qr_request).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(events.contains("amount:2.000000000000000001")); assertTrue(events.contains("token")); assertEquals("create", events.last()) }
    }
    @Test fun requestReadinessDisablesCreateAndOffersRetryOnError() {
        val state = mutableStateOf(request()); showRequest(state)
        compose.runOnIdle { state.value = state.value.copy(screenStatus = ScreenStatus.ERROR) }
        node(R.string.common_create_qr_request).performScrollTo().assertIsNotEnabled()
        node(R.string.common_error_general_title).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("retry"), events) }
    }
    @Test fun requestWithoutAnAccountCannotCreateQr() {
        showRequest(mutableStateOf(request().copy(untransformedUserAddress = null, untransformedAvatarDrawable = null)))
        node(R.string.common_create_qr_request).performScrollTo().assertIsNotEnabled()
    }
    @Test fun requestCannotCreateForAnEmptyAddress() {
        showRequest(mutableStateOf(request().copy(untransformedUserAddress = " ")))
        node(R.string.common_create_qr_request).performScrollTo().assertIsNotEnabled()
    }
    @Test fun requestConfirmationShowsExactAmountAndRequiresExplicitShare() {
        showConfirm(mutableStateOf(confirm()))
        compose.onNodeWithText("1.234567890123456789").assertExists()
        compose.onNodeWithText(address).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("copy"), events) }
        node(R.string.common_share).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("copy", "share"), events) }
    }
    @Test fun requestConfirmationCannotShareMissingQr() {
        showConfirm(mutableStateOf(confirm().copy(untransformedQrBitmap = null)))
        node(R.string.common_share).performScrollTo().assertIsNotEnabled()
    }
    @Test fun requestConfirmationCannotShareAnEmptyAddress() {
        showConfirm(mutableStateOf(confirm().copy(untransformedUserAddress = "")))
        node(R.string.common_share).performScrollTo().assertIsNotEnabled()
    }
    @Test fun requestConfirmationLoadingAndErrorKeepShareUnavailable() {
        val state = mutableStateOf(confirm().copy(screenStatus = ScreenStatus.LOADING)); showConfirm(state)
        node(R.string.common_share).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(screenStatus = ScreenStatus.ERROR) }
        node(R.string.common_share).assertIsNotEnabled()
        compose.onAllNodesWithText(compose.activity.getString(R.string.common_error_general_title))[0].performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("retry"), events) }
    }
    @Test fun receiveRequestTabsRouteOnlyTheVisibleAction() {
        show {
            QrCodeMainScreen(rememberScrollState(), receive(), request(), { events += "receive-copy" }, { events += "scan" }, { events += "receive-share" }, {},
                { events += "request-copy" }, {}, {}, { events += "create" }, {})
        }
        node(R.string.common_share).performScrollTo().performClick()
        node(R.string.common_request).performClick()
        node(R.string.common_create_qr_request).performScrollTo().performClick()
        node(R.string.common_receive).performClick()
        node(R.string.commom_scan_qr).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("receive-share", "create", "scan"), events) }
    }
    @Test fun basicReceiveKeepsCopyAndShareIndependent() {
        show { Column(Modifier.verticalScroll(rememberScrollState())) { ReceiveScreen(qr, avatar, "Basic wallet", address, { events += "share" }, { events += "copy" }) } }
        compose.onNodeWithText("Basic wallet").performScrollTo().performClick()
        compose.onNodeWithText(address).performScrollTo().performClick()
        node(R.string.copy_receive_address).performScrollTo().performClick()
        node(R.string.common_share).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("copy", "copy", "copy", "share"), events) }
    }
    @Test fun basicReceivePlaceholderStillAllowsCopyingTheAddress() {
        show { Column(Modifier.verticalScroll(rememberScrollState())) { ReceiveScreen(null, avatar, "Basic wallet", address, {}, { events += "copy" }) } }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.wallet_receive_xor)).assertDoesNotExist()
        node(R.string.copy_receive_address).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("copy"), events) }
    }
    @Test fun sendConfirmationShowsRecipientAmountAndFeeBeforeSubmit() {
        showSend()
        compose.onNodeWithText(address).assertExists()
        compose.onNodeWithText("1.234567890123456789").assertExists()
        compose.onNodeWithText("0.007 XOR").assertExists()
        compose.onNodeWithText("$0.01").assertExists()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
        node(R.string.common_confirm).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("confirm"), events) }
    }
    @Test fun sendConfirmationReactsToRevokedReviewReadiness() {
        val enabled = mutableStateOf(true); showSend(enabled)
        compose.runOnIdle { enabled.value = false }
        node(R.string.common_confirm).performScrollTo().assertIsNotEnabled()
    }
    @Test fun sendConfirmationCannotSignAfterSwitchingToReadOnly() {
        showSend(); compose.runOnIdle { readOnly.value = true }
        node(R.string.common_confirm).performScrollTo().assertIsNotEnabled()
    }
    @Test fun sendConfirmationCannotSignDuringRecoveryMode() {
        showSend(); compose.runOnIdle { WalletRecoveryCapabilityGate.enterBlocked(); WalletRecoveryCapabilityGate.enterLegacyReadOnly() }
        node(R.string.common_confirm).performScrollTo().assertIsNotEnabled()
    }
    @Test fun sendConfirmationRecipientSupportsCopyAndLongCopy() {
        showSend()
        compose.onNodeWithText(address).performClick()
        compose.onNodeWithText(address).performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(listOf("copy", "long-copy"), events) }
    }
}
