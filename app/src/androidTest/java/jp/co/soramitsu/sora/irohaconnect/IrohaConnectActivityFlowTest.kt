package jp.co.soramitsu.sora.irohaconnect

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.*
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.irohaconnect.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Actual window, intent and lifecycle routing. View model and authentication boundaries cannot sign. */
@RunWith(AndroidJUnit4::class)
class IrohaConnectActivityFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val screenState = MutableStateFlow(IrohaConnectUiState(IrohaConnectUiPhase.CONNECTED, "Fixture wallet", "public-address", "Taira", appName = "Fixture app"))
    private val vm = mockk<IrohaConnectViewModel>(relaxed = true) { every { state } returns this@IrohaConnectActivityFlowTest.screenState }
    private var scenario: ActivityScenario<IrohaConnectActivity>? = null
    private var launchLink: String? = null
    private val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity is IrohaConnectActivity) {
                IrohaConnectActivity::class.java.getDeclaredField("viewModel\$delegate")
                    .apply { isAccessible = true }.set(activity, lazyOf(vm))
                // ActivityScenario matches the original intent. Deliver the URI only to the
                // actual activity so scrubbing it in onCreate preserves that match.
                if (savedInstanceState == null) activity.intent = Intent(activity.intent).apply {
                    data = launchLink?.let(Uri::parse)
                }
            }
        }
        override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
    @Before fun setup() { app.registerActivityLifecycleCallbacks(callbacks) }
    @After fun cleanup() { scenario?.close(); app.unregisterActivityLifecycleCallbacks(callbacks); unmockkStatic(BiometricManager::class) }
    private fun launch(link: String? = "iroha-connect://public-fixture") {
        launchLink = link
        scenario = ActivityScenario.launch(Intent(app, IrohaConnectActivity::class.java))
        compose.waitForIdle()
    }
    private fun phase(value: IrohaConnectUiPhase) = compose.runOnUiThread { screenState.value = screenState.value.copy(phase = value) }
    private fun unavailableAuthentication() {
        mockkStatic(BiometricManager::class)
        val manager = mockk<BiometricManager> { every { canAuthenticate(any()) } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE }
        every { BiometricManager.from(any()) } returns manager
    }
    private fun pairing() { screenState.value = screenState.value.copy(phase = IrohaConnectUiPhase.PAIRING,
        pairing = IrohaConnectPairingReview("Fixture app", "https://fixture.invalid", "Taira", "taira",
            IrohaConnectPermissions(listOf("sign_raw"), emptyList(), emptyList()), "abcd1234", Long.MAX_VALUE)) }
    private fun signing() { screenState.value = screenState.value.copy(phase = IrohaConnectUiPhase.SIGNING,
        signing = IrohaConnectSigningReview("fixture-request", IrohaConnectSigningKind.RAW, "fixture.scope", 12, "deadbeef", Long.MAX_VALUE, "Public fixture message")) }
    @Test fun launchConsumesDeepLinkOnceAndScrubsIntent() {
        launch(); verify(exactly = 1) { vm.start("iroha-connect://public-fixture") }
        scenario!!.onActivity { assertNull(it.intent.data) }
    }
    @Test fun missingDeepLinkIsForwardedAsMissing() { launch(null); verify(exactly = 1) { vm.start(null) } }
    @Test fun activeSessionWindowProtectsScreenshots() {
        launch(); scenario!!.onActivity { assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
    }
    @Test fun rotationDoesNotRestartConnectedSession() {
        launch(); scenario!!.recreate(); compose.waitForIdle(); verify(exactly = 1) { vm.start(any()) }
    }
    @Test fun rotationDoesNotTreatConfigurationChangeAsBackground() {
        launch(); scenario!!.recreate(); compose.waitForIdle(); verify(exactly = 0) { vm.onAppBackgrounded() }
    }
    @Test fun loadingSessionRestartsAfterRotationWithConsumedIntent() {
        screenState.value = screenState.value.copy(phase = IrohaConnectUiPhase.LOADING); launch(); scenario!!.recreate(); compose.waitForIdle()
        verify(exactly = 1) { vm.start("iroha-connect://public-fixture") }; verify(exactly = 1) { vm.start(null) }
    }
    @Test fun backgroundingClosesSessionThroughViewModel() {
        launch(); scenario!!.moveToState(Lifecycle.State.CREATED); verify(exactly = 1) { vm.onAppBackgrounded() }
    }
    @Test fun closeActiveSessionAsksViewModelBeforeFinishing() {
        launch(); compose.onNodeWithTag("close_session").performScrollTo().performClick()
        verify(exactly = 1) { vm.closeByUser() }; assertEquals(Lifecycle.State.RESUMED, scenario!!.state)
    }
    @Test fun closeTerminalErrorFinishesWithoutClosingAgain() {
        phase(IrohaConnectUiPhase.ERROR); launch(); compose.onNodeWithTag("terminal_action").performScrollTo().performClick()
        compose.waitForIdle(); verify(exactly = 0) { vm.closeByUser() }
        compose.waitUntil(5_000) { scenario!!.state == Lifecycle.State.DESTROYED }
    }
    @Test fun closeTerminalSessionFinishesWithoutClosingAgain() {
        phase(IrohaConnectUiPhase.CLOSED); launch(); compose.onNodeWithTag("terminal_action").performScrollTo().performClick()
        compose.waitForIdle(); verify(exactly = 0) { vm.closeByUser() }; compose.waitUntil(5_000) { scenario!!.state == Lifecycle.State.DESTROYED }
    }
    @Test fun backClosesSessionAndFinishesActivity() {
        launch(); scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }; compose.waitForIdle()
        verify(exactly = 1) { vm.closeByUser() }; compose.waitUntil(5_000) { scenario!!.state == Lifecycle.State.DESTROYED }
    }
    @Test fun rejectingPairingUsesOnlyPairingRejection() {
        pairing(); launch(); compose.onNodeWithText(app.getString(R.string.wallet_connect_action_decline)).performScrollTo().performClick()
        verify(exactly = 1) { vm.rejectPairing() }; verify(exactly = 0) { vm.approvePairingAfterAuthentication(); vm.approveSigningAfterAuthentication() }
    }
    @Test fun rejectingSigningUsesOnlySigningRejection() {
        signing(); launch(); compose.onNodeWithTag("reject_signing").performScrollTo().performClick()
        verify(exactly = 1) { vm.rejectSigning() }; verify(exactly = 0) { vm.approveSigningAfterAuthentication() }
    }
    @Test fun pairingCannotApproveWhenDeviceAuthenticationIsUnavailable() {
        unavailableAuthentication(); pairing(); launch(); compose.onNodeWithTag("approve_pairing").performScrollTo().performClick()
        verify(exactly = 1) { vm.authorizationUnavailable() }; verify(exactly = 0) { vm.approvePairingAfterAuthentication() }
    }
    @Test fun signingCannotApproveWhenDeviceAuthenticationIsUnavailable() {
        unavailableAuthentication(); signing(); launch(); compose.onNodeWithTag("approve_signing").performScrollTo().performClick()
        verify(exactly = 1) { vm.authorizationUnavailable() }; verify(exactly = 0) { vm.approveSigningAfterAuthentication() }
    }
    @Test fun newIntentConsumesReplacementLinkAndStartsNewAttempt() {
        launch(); val next = Intent(app, IrohaConnectActivity::class.java).setData(Uri.parse("iroha-connect://replacement-fixture"))
        scenario!!.onActivity { activity -> IrohaConnectActivity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java)
            .apply { isAccessible = true }.invoke(activity, next); assertNull(activity.intent.data) }
        verify(exactly = 1) { vm.start("iroha-connect://replacement-fixture") }
    }
}
