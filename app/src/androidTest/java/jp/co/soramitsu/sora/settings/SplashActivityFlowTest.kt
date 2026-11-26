package jp.co.soramitsu.sora.settings

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.*
import jp.co.soramitsu.androidfoundation.fragment.SingleLiveEvent
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.account.WalletStartupPass
import jp.co.soramitsu.common.presentation.compose.neon.safemode.WalletUpgradeRetry
import jp.co.soramitsu.core_db.*
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_main_api.launcher.MainStarter
import jp.co.soramitsu.feature_multiaccount_api.MultiaccountStarter
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.sora.R
import jp.co.soramitsu.sora.splash.presentation.*
import jp.co.soramitsu.sora.splash.presentation.recovery.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Actual launcher/lifecycle/window with inert view-model actions and intercepted external intents. */
@RunWith(AndroidJUnit4::class)
class SplashActivityFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val splashModel = mockk<SplashViewModel>(relaxed = true)
    private val safeModel = mockk<SafeModeViewModel>(relaxed = true)
    private var afterActivityCreated: ((SplashActivity) -> Unit)? = null
    private val lifecycle = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity !is SplashActivity) return
            SplashActivity::class.java.getDeclaredField("splashViewModel\$delegate").apply { isAccessible = true }
                .set(activity, lazyOf(splashModel))
            SplashActivity::class.java.getDeclaredField("safeModeViewModel\$delegate").apply { isAccessible = true }
                .set(activity, lazyOf(safeModel))
        }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity is SplashActivity) afterActivityCreated?.invoke(activity)
        }
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
    private val runtimeReady = MutableLiveData(false)
    private val loading = SingleLiveEvent<Unit>()
    private val main = SingleLiveEvent<Unit>()
    private val onboarding = SingleLiveEvent<OnboardingState>()
    private val recovery = SingleLiveEvent<WalletMigrationRecoveryUiState>()
    private val safe = MutableStateFlow(SafeModeUiState())
    private val events = Channel<SafeModeEvent>(Channel.UNLIMITED)
    private val mainStarter = mockk<MainStarter>(relaxed = true)
    private val onboardingStarter = mockk<MultiaccountStarter>(relaxed = true)
    private val node = mockk<NodeManager>(relaxed = true)
    private var scenario: ActivityScenario<SplashActivity>? = null
    private val monitors = mutableListOf<Instrumentation.ActivityMonitor>()
    @Before fun setup() {
        WalletRecoveryCapabilityGate.enterNormal()
        mockkObject(WalletUpgradeBackup)
        every { WalletUpgradeBackup.startupFailureToShow(any()) } returns null
        every { WalletUpgradeBackup.blockingFailure() } returns null
        every { WalletUpgradeBackup.recoveryOptions() } returns null
        application.registerActivityLifecycleCallbacks(lifecycle)
        every { splashModel.runtimeInitiated } returns runtimeReady
        every { splashModel.loadingTextVisiblity } returns loading
        every { splashModel.showMainScreen } returns main
        every { splashModel.showOnBoardingScreen } returns onboarding
        every { splashModel.showMigrationRecovery } returns recovery
        every { splashModel.start() } just Runs
        every { splashModel.nextScreen() } just Runs
        every { splashModel.retryWalletMigration() } just Runs
        every { splashModel.continueLegacyWallet() } just Runs
        every { safeModel.state } returns safe
        every { safeModel.events } returns events.receiveAsFlow()
        every { safeModel.technicalDetails() } returns listOf("code" to "FIXTURE_RECOVERY")
        every { safeModel.showPanel(any(), any(), any(), any()) } just Runs
        every { safeModel.startRetryFromHome() } just Runs
        every { safeModel.onVerificationRouted() } just Runs
        every { safeModel.markProgressFinished() } returns false
        every { safeModel.showStartupProgress() } just Runs
        every { safeModel.onPaused() } just Runs
        every { safeModel.onResumed() } just Runs
        every { safeModel.onStopped() } just Runs
        every { safeModel.onNoEmailApp() } just Runs
    }
    @After fun cleanup() {
        scenario?.let { active ->
            if (active.state != Lifecycle.State.DESTROYED) active.onMainActivity { it.finish() }
            active.close()
        }
        monitors.forEach(instrumentation::removeMonitor)
        application.unregisterActivityLifecycleCallbacks(lifecycle)
        unmockkObject(WalletUpgradeBackup); WalletRecoveryCapabilityGate.enterNormal()
    }
    private fun launch(retry: Boolean = false, legacyLauncher: Boolean = false): ActivityScenario<SplashActivity> {
        val intent = if (legacyLauncher) {
            Intent().setClassName(application.packageName, "jp.co.soramitsu.sora.splash.presentation.SplashActivity")
        } else {
            Intent(ApplicationProvider.getApplicationContext(), SplashActivity::class.java)
        }
        intent.putExtra(WalletUpgradeRetry.EXTRA_RETRY_UPGRADE, retry)
        return ActivityScenario.launch<SplashActivity>(intent).also {
            scenario = it
            it.onMainActivity { activity ->
                activity.mainStarter = mainStarter
                activity.multiaccStarter = onboardingStarter
                activity.nodeManager = dagger.Lazy { node }
            }
            drainMainQueue()
        }
    }
    private fun ActivityScenario<SplashActivity>.onMainActivity(action: (SplashActivity) -> Unit) {
        instrumentation.runOnMainSync { onActivity(action) }
    }

    // Animation keeps this activity drawing. Drain posted events without waiting for all frames to stop.
    private fun drainMainQueue() { instrumentation.runOnMainSync {} }

    private fun emit(event: SafeModeEvent) {
        instrumentation.runOnMainSync { assertTrue(events.trySend(event).isSuccess) }
        drainMainQueue()
    }
    private fun monitor(action: String): Instrumentation.ActivityMonitor =
        instrumentation.addMonitor(IntentFilter(action).apply {
            if (action == Intent.ACTION_SENDTO) addDataScheme("mailto")
        }, Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
            .also { monitors += it }
    private fun panel() = RecoveryPanels.model(WalletRecoveryOptions.forState("FIXTURE_RECOVERY", false,
        RecoveryFacts(hasWalletFiles = true)), false, false)

    @Test fun systemSplashDoesNotPlayASecondAnimationOrWaitForItsRenderer() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        // Observe readiness before yielding the main loop: a slow instrumented launch may
        // legitimately outlast the renderer's four-second fallback before launch() returns.
        afterActivityCreated = {
            verify(exactly = 0) { splashModel.nextScreen() }
            runtimeReady.value = true
        }
        launch().onMainActivity { activity ->
            val animation = activity.findViewById<com.airbnb.lottie.LottieAnimationView>(R.id.animation_view)
            assertEquals(View.GONE, animation.visibility)
            assertFalse(animation.isAnimating)
        }
        verify(exactly = 1) { splashModel.nextScreen() }
        instrumentation.runOnMainSync { runtimeReady.value = true }
        drainMainQueue()
        verify(exactly = 1) { splashModel.nextScreen() }
    }

    // A start that plays no animation (Android 12 and later) asks once for the early route; the question it
    // hands over says no while a recovery state is known.
    @Test fun aStartThatPlaysNothingAsksForTheEarlyRouteAndRefusesItOverRecovery() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        val mayOpen = slot<() -> Boolean>()
        every { splashModel.routeEarly(capture(mayOpen)) } just Runs
        launch()
        verify(exactly = 1) { splashModel.routeEarly(any()) }
        assertTrue(mayOpen.captured())
        every { WalletUpgradeBackup.blockingFailure() } returns WalletUpgradeBackup.Failure("FIXTURE_BLOCKED")
        assertFalse(mayOpen.captured())
        every { WalletUpgradeBackup.blockingFailure() } returns null
        assertTrue(mayOpen.captured())
        instrumentation.runOnMainSync { safe.value = SafeModeUiState(visible = true, panel = panel()) }
        assertFalse(mayOpen.captured())
    }
    @Test fun anAnimatedStartNeverAsksForTheEarlyRoute() {
        Assume.assumeTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.S && android.animation.ValueAnimator.areAnimatorsEnabled())
        launch()
        verify(exactly = 0) { splashModel.routeEarly(any()) }
    }
    @Test fun retryFromHomeNeverAsksForTheEarlyRoute() {
        every { WalletUpgradeBackup.recoveryOptions() } returns WalletRecoveryOptions.forState("FIXTURE_RECOVERY", false, RecoveryFacts()); launch(retry = true)
        verify(exactly = 0) { splashModel.routeEarly(any()) }
    }
    @Test fun aStartHeldAtRecoveryNeverAsksForTheEarlyRoute() {
        every { WalletUpgradeBackup.startupFailureToShow(any()) } returns WalletUpgradeBackup.Failure("INSUFFICIENT_BACKUP_STORAGE")
        launch()
        verify(exactly = 0) { splashModel.routeEarly(any()) }
    }
    // A wallet opened before the usual moment is final: the usual moment neither routes again nor starts the
    // progress screen's timer.
    // The route is emitted before the screen starts (delivered at onStart), so a slow launch cannot reach the
    // four-second animation fallback first.
    @Test fun aWalletOpenedEarlyIsNotRoutedAgainAndShowsNoProgressScreen() {
        afterActivityCreated = { activity ->
            activity.mainStarter = mainStarter
            activity.nodeManager = dagger.Lazy { node }
            main.value = Unit
        }
        launch()
        instrumentation.runOnMainSync { runtimeReady.value = true }; drainMainQueue()
        verify(exactly = 1) { mainStarter.start(any()) }
        verify(exactly = 0) { splashModel.nextScreen() }
        Thread.sleep(SafeModeViewModel.SILENT_UPGRADE_MILLIS + 500)
        verify(exactly = 0) { safeModel.showStartupProgress() }
    }
    @Test fun onboardingOpenedEarlyIsNotRoutedAgain() {
        afterActivityCreated = { activity ->
            activity.multiaccStarter = onboardingStarter
            onboarding.value = OnboardingState.INITIAL
        }
        launch()
        instrumentation.runOnMainSync { runtimeReady.value = true }; drainMainQueue()
        verify(exactly = 1) { onboardingStarter.startOnboardingFlow(any()) }
        verify(exactly = 0) { splashModel.nextScreen() }
    }
    // A screen recreated after the early route decided the start neither routes nor starts the progress timer.
    @Test fun aStartAlreadyDecidedEarlyIsNotRoutedAgainByARecreatedScreen() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        every { splashModel.isStartDecided } returns true
        afterActivityCreated = { runtimeReady.value = true }
        launch()
        verify(exactly = 0) { splashModel.nextScreen() }
        Thread.sleep(SafeModeViewModel.SILENT_UPGRADE_MILLIS + 500)
        verify(exactly = 0) { safeModel.showStartupProgress() }
    }
    // The wallet is routed while the screen is in the background: on return it opens once, with no progress screen.
    @Test fun aRouteDecidedWhileStoppedOpensOnReturnWithoutTheProgressScreen() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        var nextScreenCalls = 0
        every { splashModel.nextScreen() } answers { nextScreenCalls += 1 }
        launch(); scenario!!.moveToState(Lifecycle.State.CREATED); drainMainQueue()
        // A launch slow enough for the four-second fallback has already gone the usual way: nothing to check here.
        Assume.assumeTrue(nextScreenCalls == 0)
        // As the early route does: the start is decided, then the route is emitted.
        every { splashModel.isStartDecided } returns true
        instrumentation.runOnMainSync { main.value = Unit; runtimeReady.value = true }; drainMainQueue()
        verify(exactly = 0) { mainStarter.start(any()) }
        scenario!!.moveToState(Lifecycle.State.RESUMED); drainMainQueue()
        verify(exactly = 1) { mainStarter.start(any()) }
        Thread.sleep(SafeModeViewModel.SILENT_UPGRADE_MILLIS + 500)
        verify(exactly = 0) { safeModel.showStartupProgress() }
        assertEquals(0, nextScreenCalls)
    }
    @Test fun publishedLauncherAliasStillStartsWalletVerification() {
        launch(legacyLauncher = true).onMainActivity { activity ->
            assertEquals(WalletStartupActivity::class.java, activity.javaClass)
            assertEquals(View.GONE, activity.findViewById<View>(R.id.walletRecoveryCompose).visibility)
        }
        verify(exactly = 1) { splashModel.start() }
    }

    @Test fun publishedLauncherAliasPreservesAuthorizedRecoveryRetryExtras() {
        every { WalletUpgradeBackup.recoveryOptions() } returns WalletRecoveryOptions.forState(
            "FIXTURE_RECOVERY", false, RecoveryFacts(),
        )
        launch(retry = true, legacyLauncher = true)
        verify(exactly = 1) { safeModel.startRetryFromHome() }
        verify(exactly = 0) { splashModel.start() }
    }

    @Test fun ordinaryLaunchStartsWalletVerificationAndKeepsRecoveryHidden() {
        launch().onMainActivity { activity ->
            assertEquals(View.GONE, activity.findViewById<View>(R.id.walletRecoveryCompose).visibility)
            assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
        }
        verify(exactly = 1) { splashModel.start() }
    }
    @Test fun strayRetryIntentWithoutRecoveryStillUsesNormalStartup() {
        launch(retry = true)
        verify(exactly = 1) { splashModel.start() }
        verify(exactly = 0) { safeModel.startRetryFromHome() }
    }
    @Test fun authorizedRecoveryRetryUsesOnlyRetryFlow() {
        every { WalletUpgradeBackup.recoveryOptions() } returns WalletRecoveryOptions.forState("FIXTURE_RECOVERY", false, RecoveryFacts()); launch(retry = true)
        verify(exactly = 1) { safeModel.startRetryFromHome() }
        verify(exactly = 0) { splashModel.start() }
    }
    @Test fun recoveryPanelHidesAnimationAndLoadingIndicators() {
        launch(); instrumentation.runOnMainSync { safe.value = SafeModeUiState(visible = true, panel = panel()) }
        drainMainQueue(); scenario!!.onMainActivity { activity ->
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.walletRecoveryCompose).visibility)
            assertEquals(View.GONE, activity.findViewById<View>(R.id.animation_view).visibility)
            assertEquals(View.GONE, activity.findViewById<View>(R.id.loadingProgressBar).visibility)
        }
    }
    @Test fun revealAuthenticationSecuresWindowAndClosingRestoresScreenshots() {
        launch(); instrumentation.runOnMainSync { safe.value = SafeModeUiState(visible = true, reveal = RevealUi.Auth(loading = false)) }
        drainMainQueue(); scenario!!.onMainActivity { activity ->
            assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
        instrumentation.runOnMainSync { safe.value = safe.value.copy(reveal = null) }; drainMainQueue()
        scenario!!.onMainActivity { activity -> assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) }
    }
    @Test fun loadingEventDisplaysProgressOnlyOutsideRecovery() {
        launch(); instrumentation.runOnMainSync { loading.value = Unit }; drainMainQueue()
        scenario!!.onMainActivity { activity -> assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.loadingProgressBar).visibility) }
        instrumentation.runOnMainSync { safe.value = SafeModeUiState(visible = true, panel = panel()) }
        drainMainQueue(); instrumentation.runOnMainSync { loading.value = Unit }; drainMainQueue()
        scenario!!.onMainActivity { activity -> assertEquals(View.GONE, activity.findViewById<View>(R.id.loadingProgressBar).visibility) }
    }
    @Test fun verifiedWalletStartsNodeBeforeMainScreen() {
        launch(); instrumentation.runOnMainSync { main.value = Unit }; drainMainQueue()
        verifyOrder { node.ensureStarted(); mainStarter.start(any()) }
        verify(exactly = 1) { safeModel.onVerificationRouted() }
    }
    @Test fun noAccountRoutesToOnboardingWithoutStartingNode() {
        launch(); instrumentation.runOnMainSync { onboarding.value = OnboardingState.INITIAL }; drainMainQueue()
        verify(exactly = 1) { onboardingStarter.startOnboardingFlow(any()) }
        verify(exactly = 0) { node.ensureStarted() }
    }
    @Test fun mainNavigationIsBlockedByNewRecoveryFailure() {
        launch(); every { WalletUpgradeBackup.blockingFailure() } returns WalletUpgradeBackup.Failure("FIXTURE_BLOCKED")
        instrumentation.runOnMainSync { main.value = Unit }; drainMainQueue()
        verify(exactly = 0) { mainStarter.start(any()); node.ensureStarted() }
        // A start nobody asked anything in: the panel may open the wallet read-only by itself.
        verify { safeModel.showPanel("FIXTURE_BLOCKED", any(), any(), unattended = true) }
    }
    @Test fun migrationFailurePassesAccountReadOnlyAvailabilityToPanel() {
        launch(); val options = WalletRecoveryOptions.forState("FIXTURE_VERIFY", false, RecoveryFacts())
        instrumentation.runOnMainSync { recovery.value = WalletMigrationRecoveryUiState("FIXTURE_VERIFY", false, options) }
        drainMainQueue()
        verify { safeModel.showPanel("FIXTURE_VERIFY", options, false, unattended = true) }
    }
    @Test fun retryReadyRebindsVerificationWithoutStartingNormalMigration() {
        every { WalletUpgradeBackup.recoveryOptions() } returns WalletRecoveryOptions.forState("FIXTURE_RECOVERY", false, RecoveryFacts()); launch(retry = true); emit(SafeModeEvent.RetryReady)
        verify(exactly = 1) { splashModel.retryWalletMigration() }
        verify(exactly = 0) { splashModel.start() }
    }
    @Test fun readOnlyReadyStartsNodeThenContinuesLegacyWallet() {
        launch(); emit(SafeModeEvent.ReadOnlyReady)
        verifyOrder { node.ensureStarted(); splashModel.continueLegacyWallet() }
    }
    @Test fun nodeStartupFailureDoesNotCrashReadOnlyRouting() {
        launch(); every { node.ensureStarted() } throws IllegalStateException("fixture node unavailable")
        emit(SafeModeEvent.ReadOnlyReady)
        verify(exactly = 1) { splashModel.continueLegacyWallet() }
    }
    @Test fun storageSettingsEventUsesStorageScreenIntent() {
        launch(); val monitor = monitor(Settings.ACTION_INTERNAL_STORAGE_SETTINGS); emit(SafeModeEvent.OpenStorageSettings)
        assertEquals(1, monitor.hits)
    }
    @Test fun supportEmailIsDeliveredAsMailtoWithoutSendingAnything() {
        launch(); val monitor = monitor(Intent.ACTION_SENDTO); emit(SafeModeEvent.Email("Fixture subject", "Fixture body"))
        assertEquals(1, monitor.hits)
    }
    @Test fun pauseAndStopNotifyRecoveryLifecycle() {
        launch(); scenario!!.moveToState(Lifecycle.State.CREATED); drainMainQueue()
        verify(atLeast = 1) { safeModel.onPaused(); safeModel.onStopped() }
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        verify(atLeast = 2) { safeModel.onResumed() }
    }
    @Test fun recreationKeepsPendingRecoveryRetryWithoutStartingItTwice() {
        every { WalletUpgradeBackup.recoveryOptions() } returns WalletRecoveryOptions.forState("FIXTURE_RECOVERY", false, RecoveryFacts()); safe.value = SafeModeUiState(visible = true, panel = panel())
        launch(retry = true); scenario!!.recreate(); drainMainQueue()
        verify(exactly = 1) { safeModel.startRetryFromHome() }
        verify(exactly = 0) { splashModel.start() }
    }
    // Codex re-review 2: the storage step gives back cache on a worker while it holds the backup's
    // lock. Recreating the startup screen (rotation) meanwhile must not wait for that lock on the
    // main thread. The real WalletUpgradeBackup answers here; the test holds its lock as a parked
    // storage step does (core_db's WalletUpgradeBackupTest parks the real one).
    @Test fun recreatingTheStartupScreenNeverWaitsForAStorageStepHoldingTheBackupLock() {
        unmockkObject(WalletUpgradeBackup)
        try {
            launch()
            val held = java.util.concurrent.CountDownLatch(1)
            val release = java.util.concurrent.CountDownLatch(1)
            val storageStep = kotlin.concurrent.thread(name = "parked-storage-step") {
                synchronized(WalletUpgradeBackup) {
                    held.countDown()
                    release.await(60, java.util.concurrent.TimeUnit.SECONDS)
                }
            }
            try {
                assertTrue(held.await(10, java.util.concurrent.TimeUnit.SECONDS))
                val recreation = kotlin.concurrent.thread(name = "recreate") { scenario!!.recreate() }
                recreation.join(15_000)
                assertFalse("the startup screen waited for the storage step's lock", recreation.isAlive)
                assertEquals(Lifecycle.State.RESUMED, scenario!!.state)
            } finally {
                release.countDown()
                storageStep.join(15_000)
            }
        } finally {
            mockkObject(WalletUpgradeBackup)
        }
    }
    // Codex re-review 2: recreated while the recovery screen shows (here: the storage step runs
    // behind its progress screen), the activity decides nothing again: no second panel, which
    // would drop the progress screen of a step still running.
    @Test fun recreationWhileTheRecoveryScreenShowsDecidesNothingAgain() {
        every { WalletUpgradeBackup.startupFailureToShow(any()) } returns
            WalletUpgradeBackup.Failure("INSUFFICIENT_BACKUP_STORAGE")
        launch()
        verify(exactly = 1) { safeModel.showPanel("INSUFFICIENT_BACKUP_STORAGE", any(), any(), unattended = true) }
        safe.value = SafeModeUiState(visible = true)

        scenario!!.recreate(); drainMainQueue()

        verify(exactly = 1) { safeModel.showPanel(any(), any(), any(), any()) }
        verify(exactly = 1) { WalletUpgradeBackup.startupFailureToShow(any()) }
        assertEquals(Lifecycle.State.RESUMED, scenario!!.state)
    }
    // Audit item 13: "Try upgrade again" from the read-only home gets its answer on the panel;
    // only a start nobody asked anything in may open the wallet read-only by itself.
    @Test fun retryFromHomeThatFailsShowsItsPanelAsAnAnswerNotAsAnUnattendedStart() {
        every { WalletUpgradeBackup.recoveryOptions() } returns WalletRecoveryOptions.forState("FIXTURE_RECOVERY", false, RecoveryFacts())
        launch(retry = true)
        emit(SafeModeEvent.RetryReady)
        val options = WalletRecoveryOptions.forState("FIXTURE_VERIFY", false, RecoveryFacts())
        instrumentation.runOnMainSync { recovery.value = WalletMigrationRecoveryUiState("FIXTURE_VERIFY", true, options) }; drainMainQueue()
        verify { safeModel.showPanel("FIXTURE_VERIFY", options, true, unattended = false) }
        verify(exactly = 0) { safeModel.showPanel(any(), any(), any(), unattended = true) }
    }
    // The wallet's main screen opens only in a process that came through this screen
    // (WalletStartupPass); the pass is given at the moment the wallet or onboarding opens.
    @Test fun thePassIsGivenWhenTheWalletOpensAndNotWhileAStartIsHeldAtRecovery() {
        WalletStartupPass.revokeForTest()
        launch(); assertFalse(WalletStartupPass.isGranted())
        every { WalletUpgradeBackup.blockingFailure() } returns WalletUpgradeBackup.Failure("FIXTURE_BLOCKED")
        instrumentation.runOnMainSync { main.value = Unit }; drainMainQueue()
        instrumentation.runOnMainSync { recovery.value = WalletMigrationRecoveryUiState("FIXTURE_VERIFY", false) }; drainMainQueue()
        assertFalse(WalletStartupPass.isGranted())
        every { WalletUpgradeBackup.blockingFailure() } returns null
        instrumentation.runOnMainSync { main.value = Unit }; drainMainQueue()
        assertTrue(WalletStartupPass.isGranted())
        verify(exactly = 1) { mainStarter.start(any()) }
    }
    @Test fun thePassIsGivenWhenOnboardingOpens() {
        WalletStartupPass.revokeForTest()
        launch(); instrumentation.runOnMainSync { onboarding.value = OnboardingState.INITIAL }; drainMainQueue()
        assertTrue(WalletStartupPass.isGranted())
    }
    @Test fun completedProgressTickDefersMainNavigationBriefly() {
        launch(); every { safeModel.markProgressFinished() } returns true
        instrumentation.runOnMainSync { main.value = Unit }
        verify(exactly = 0) { mainStarter.start(any()) }
        verify(timeout = 2500, exactly = 1) { mainStarter.start(any()) }
    }
}
