package jp.co.soramitsu.sora.assets

import android.graphics.Bitmap
import android.content.res.Resources
import android.graphics.drawable.BitmapDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.*
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.ScreenStatus
import jp.co.soramitsu.feature_assets_api.presentation.selectsearchtoken.emptySearchTokenFilter
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.receiverequest.*
import jp.co.soramitsu.feature_assets_impl.presentation.screens.receiverequest.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.math.BigDecimal

/** Real receive/request graph; no scanner, file sharing, clipboard or wallet access occurs. */
@RunWith(AndroidJUnit4::class)
class QrDestinationFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private val avatar = BitmapDrawable(Resources.getSystem(), bitmap)
    private val address = "public-qr-fixture"
    private val amount = AssetAmountInputState(Token("xor", "SORA", "XOR", 18, true, null, 1.0, 0.0, "$"), "100 XOR", BigDecimal("1.25"), "$1.25", true)
    private val receive = MutableStateFlow(ReceiveTokenByQrScreenState(ScreenStatus.READY_TO_RENDER, bitmap, "Receiving wallet", avatar, address))
    private val request = MutableStateFlow(RequestTokenScreenState(ScreenStatus.READY_TO_RENDER, "Receiving wallet", avatar, address, amount))
    private val confirmation = MutableStateFlow(RequestTokenConfirmScreenState(ScreenStatus.READY_TO_RENDER, bitmap, "Receiving wallet", avatar, address, amount.copy(enabled = false, readOnly = true)))
    private val vm = mockk<QRCodeFlowViewModel>(relaxed = true) {
        every { receiveTokenByQrScreenState } returns receive
        every { requestTokenScreenState } returns request
        every { requestTokenConfirmScreenState } returns confirmation
        every { selectTokenScreenState } returns MutableStateFlow(emptySearchTokenFilter)
    }
    private lateinit var nav: NavHostController
    private fun show(route: String = QRCodeFlowRoute.MainScreen.route) {
        val fragment = QRCodeFlowFragment()
        fragment.javaClass.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }.set(fragment, lazyOf(vm))
        compose.setContent { SoraAppTheme {
            nav = rememberNavController(); val scroll = rememberScrollState()
            NavHost(nav, startDestination = route) { with(fragment) { content(scroll, nav) } }
        } }
    }
    private fun node(id: Int) = compose.onNodeWithText(compose.activity.getString(id))
    private fun requestPage() { node(R.string.common_request).performClick(); compose.waitForIdle() }
    @Test fun receiveShareUsesReceiveHandlerOnly() {
        show(); node(R.string.common_share).performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onShareQrCodeInReceiveScreen() }; verify(exactly = 0) { vm.onShareQrCodeInRequestConfirmScreen() } }
    }
    @Test fun receiveAddressCopiesThroughTheReceiveHandler() {
        show(); compose.onNodeWithText(address).performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onUserAddressClickInReceiveScreen() } }
    }
    @Test fun incompleteReceiveCannotShare() {
        receive.value = receive.value.copy(untransformedQrBitmap = null); show(); node(R.string.common_share).performScrollTo().assertIsNotEnabled().performClick()
        compose.runOnIdle { verify(exactly = 0) { vm.onShareQrCodeInReceiveScreen() } }
    }
    @Test fun requestAmountIsPassedWithoutFloatingPointRounding() {
        show(); requestPage(); compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement("0.000000000000000001")
        compose.runOnIdle { verify(exactly = 1) { vm.onRequestAmountChange(match { it.compareTo(BigDecimal("0.000000000000000001")) == 0 }) } }
    }
    @Test fun creatingRequestLoadsConfirmationBeforeNavigating() {
        show(); requestPage(); node(R.string.common_create_qr_request).performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onLoadRequestConfirmScreenDataAgainClick() }; assertEquals(QRCodeFlowRoute.ConfirmRequestByQRCode.route, nav.currentDestination?.route) }
    }
    @Test fun missingRequestAddressDisablesCreation() {
        request.value = request.value.copy(untransformedUserAddress = null); show(); requestPage()
        node(R.string.common_create_qr_request).performScrollTo().assertIsNotEnabled().performClick()
        compose.runOnIdle { verify(exactly = 0) { vm.onLoadRequestConfirmScreenDataAgainClick() }; assertEquals(QRCodeFlowRoute.MainScreen.route, nav.currentDestination?.route) }
    }
    @Test fun confirmationSharesThroughTheConfirmationHandlerOnly() {
        show(QRCodeFlowRoute.ConfirmRequestByQRCode.route); node(R.string.common_share).performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onShareQrCodeInRequestConfirmScreen() }; verify(exactly = 0) { vm.onShareQrCodeInReceiveScreen() } }
    }
    @Test fun confirmationCopiesTheDisplayedRecipientThroughItsOwnHandler() {
        show(QRCodeFlowRoute.ConfirmRequestByQRCode.route); compose.onNodeWithText(address).performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onUserAddressClickInRequestConfirmScreen() }; verify(exactly = 0) { vm.onUserAddressClickInReceiveScreen() } }
    }
    @Test fun confirmationAmountIsReadOnly() {
        show(QRCodeFlowRoute.ConfirmRequestByQRCode.route); compose.onNodeWithText("1.25").assertExists()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }
    @Test fun clearingConfirmationBitmapDisablesStaleShare() {
        show(QRCodeFlowRoute.ConfirmRequestByQRCode.route); compose.runOnIdle { confirmation.value = confirmation.value.copy(untransformedQrBitmap = null) }
        node(R.string.common_share).performScrollTo().assertIsNotEnabled().performClick()
        compose.runOnIdle { verify(exactly = 0) { vm.onShareQrCodeInRequestConfirmScreen() } }
    }
}
