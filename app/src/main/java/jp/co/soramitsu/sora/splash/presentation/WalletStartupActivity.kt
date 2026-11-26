/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.sora.splash.presentation

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.intent.restartApplication
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.account.WalletStartupPass
import jp.co.soramitsu.common.domain.DarkThemeManager
import jp.co.soramitsu.common.presentation.compose.neon.safemode.TechnicalDetail
import jp.co.soramitsu.common.presentation.compose.neon.safemode.WalletUpgradeRetry
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.presentation.compose.theme.neon.DeviceFxSignals
import jp.co.soramitsu.common.presentation.compose.theme.neon.FxPolicy
import jp.co.soramitsu.common.util.ext.setNavigationBarColorCompat
import jp.co.soramitsu.common.util.ext.setStatusBarColorCompat
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.feature_main_api.launcher.MainStarter
import jp.co.soramitsu.feature_main_impl.presentation.pincode.ScreenLockPrompt
import jp.co.soramitsu.feature_multiaccount_api.MultiaccountStarter
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.sora.R
import jp.co.soramitsu.sora.databinding.ActivitySplashBinding
import jp.co.soramitsu.sora.splash.domain.StartupTimeTrace
import jp.co.soramitsu.sora.splash.presentation.recovery.SafeModeCallbacks
import jp.co.soramitsu.sora.splash.presentation.recovery.SafeModeEvent
import jp.co.soramitsu.sora.splash.presentation.recovery.SafeModeViewModel
import jp.co.soramitsu.sora.splash.presentation.recovery.SplashSafeMode
import jp.co.soramitsu.ui_core.sacred.TenmonFlight
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coordinates wallet verification, startup navigation, and Safe Mode (design spec
 * 9) when the upgrade needs the person: the upgrade progress (U1, U5), the storage screen (U4)
 * and the recovery options (U6), hosted in the `walletRecoveryCompose` view.
 *
 * While the wallet is verified the launch scene plays on the light launch page ([TenmonFlight]):
 * the mark comes down, the rings spread and wait. The first screen carries the scene on. It is
 * presentation only: the checks and the navigation never wait for it.
 */
@AndroidEntryPoint
class WalletStartupActivity : AppCompatActivity() {

    private val splashViewModel: SplashViewModel by viewModels()
    private val safeModeViewModel: SafeModeViewModel by viewModels()

    private lateinit var viewBinding: ActivitySplashBinding

    @Inject
    lateinit var multiaccStarter: MultiaccountStarter

    @Inject
    lateinit var mainStarter: MainStarter

    @Inject
    lateinit var darkThemeManager: DarkThemeManager

    @Inject
    lateinit var nodeManager: dagger.Lazy<NodeManager>

    private var isNavigationRequested = false
    private var isStartupReadyReported = false
    private var isSplashViewModelBound = false
    private var openedForRetry = false
    private var progressWatchdog: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android owns the launch splash and switches to the saved application theme.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(ActivitySplashBinding.inflate(layoutInflater).also { viewBinding = it }.root)
        // The launch page runs under the bars, as the first screen does, so the launch scene
        // stands in the same place on both.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setStatusBarColorCompat(Color.TRANSPARENT)
        window.setNavigationBarColorCompat(Color.TRANSPARENT)
        setUpSafeMode()

        val retryInProgress = savedInstanceState?.getBoolean(STATE_OPENED_FOR_RETRY) == true &&
            safeModeViewModel.state.value.visible
        if (retryInProgress || (savedInstanceState == null && isRetryFromHome())) {
            // "Try upgrade again" from the read-only home screen: the same action as Try again
            // on the recovery screen, with the progress screen from the start. The splash view
            // model never starts a verification of its own here: it only runs the retry (or
            // read-only access) that Safe Mode hands to it.
            openedForRetry = true
            hideStartAnimation()
            isNavigationRequested = true
            if (!retryInProgress) {
                safeModeViewModel.startRetryFromHome()
            } else if (savedInstanceState.getBoolean(STATE_SPLASH_BOUND)) {
                // Recreated (for example a theme change) while the retained view model works on
                // the retry: observe its result again.
                bindSplashViewModel()
            }
            return
        }
        bindSplashViewModel()
        splashViewModel.start()

        if (savedInstanceState != null && isSafeModeVisible()) {
            // Recreated (rotation, theme) while the recovery screen shows: its retained view
            // model already holds what is on screen, including a storage step or a retry that is
            // still running on a worker. Nothing is decided again, so a running step keeps its
            // progress screen and its result arrives as before.
            hideStartAnimation()
            return
        }

        // An interrupted verification is retried automatically first (SplashInteractor); the
        // recovery options appear only if that retry does not succeed. Never waits for a worker
        // that holds the backup's lock ([WalletUpgradeBackup.startupFailureToShow]).
        val startupFailure = WalletUpgradeBackup.startupFailureToShow(applicationContext)
        if (startupFailure == null) {
            // The motion policy is asked first: a phone that will not play the traced scene
            // (power saving, animations removed) shows it at rest and never opens the GPU for it.
            TenmonFlight.begin(viewBinding.launchScene, FxPolicy.from(DeviceFxSignals.read(this)).litMotion)
            showLaunchPageBars(true)
            // The launch scene plays beside the start and never holds it, so nothing is waited for: a verified
            // wallet whose routing reads answer at once opens now, and its first screen carries the scene on.
            // Anything else takes the usual way, at the usual time. (The check holds the recovery view model,
            // never this screen.)
            val safeMode = safeModeViewModel
            splashViewModel.routeEarly(
                mayOpen = { !safeMode.state.value.visible && WalletUpgradeBackup.blockingFailure() == null },
            )
        } else {
            showRecovery(startupFailure.code)
        }
    }

    /** Dark icons in the bars over the light launch page; the theme's own once it has gone. */
    private fun showLaunchPageBars(launchPage: Boolean) {
        val light = launchPage || resources.getBoolean(jp.co.soramitsu.common.R.bool.neon_light_system_bars)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    /** Only while a recovery state is active; a stray intent cannot start anything else. */
    private fun isRetryFromHome(): Boolean =
        intent?.getBooleanExtra(WalletUpgradeRetry.EXTRA_RETRY_UPGRADE, false) == true &&
            (
                WalletRecoveryCapabilityGate.mode() != WalletRecoveryCapabilityGate.Mode.NORMAL ||
                    WalletUpgradeBackup.recoveryOptions() != null
                )

    private fun bindSplashViewModel() {
        if (isSplashViewModelBound) return
        isSplashViewModelBound = true
        splashViewModel.runtimeInitiated.observe(this) {
            // Wallet verification starts independently in start(); the launch scene plays
            // beside it and never holds it up.
            if (it && !isSafeModeVisible()) goNext()
        }
        splashViewModel.loadingTextVisiblity.observe(this) {
            if (!isSafeModeVisible()) {
                // The scene's surface lies over this window: it comes to rest in the window
                // itself, and the note shows on it.
                TenmonFlight.rest(viewBinding.launchScene)
                viewBinding.loadingDisclaimerTextView.visibility = View.VISIBLE
                viewBinding.loadingProgressBar.visibility = View.VISIBLE
            }
        }
        splashViewModel.showMainScreen.observe(this) {
            // A route decided (early or not) is final: a later goNext neither routes again nor starts the progress
            // screen's timer.
            isNavigationRequested = true
            val backupFailure = WalletUpgradeBackup.blockingFailure()
            if (backupFailure == null) {
                afterVerification {
                    // F11: read-only and a successful retry need the live node connection.
                    runCatching { nodeManager.get().ensureStarted() }
                    reportStartupReady()
                    mainStarter.start(this)
                }
            } else {
                showRecovery(backupFailure.code)
            }
        }
        splashViewModel.showOnBoardingScreen.observe(this) {
            isNavigationRequested = true
            afterVerification {
                reportStartupReady()
                multiaccStarter.startOnboardingFlow(this)
            }
        }
        splashViewModel.showMigrationRecovery.observe(this) { recovery ->
            showRecovery(recovery.code, recovery.options, recovery.canContinueLegacyWallet)
        }
    }

    private fun setUpSafeMode() {
        viewBinding.walletRecoveryCompose.setContent {
            val dark by darkThemeManager.darkModeStatusFlow.collectAsStateWithLifecycle()
            val state by safeModeViewModel.state.collectAsStateWithLifecycle()
            // The theme (and the moving light it puts the screen under) only while the panel is up: a hidden panel
            // keeps no tilt sensor on behind the launch scene.
            if (state.visible) {
                SoraAppTheme(darkTheme = dark) {
                    val labels = detailLabels()
                    val details = safeModeViewModel.technicalDetails().map { (key, value) ->
                        TechnicalDetail(labels[key] ?: key, value)
                    }
                    SplashSafeMode(
                        state = state,
                        details = details,
                        callbacks = SafeModeCallbacks(
                            onAction = safeModeViewModel::onAction,
                            onCopyDetail = { safeModeViewModel.copyToClipboard(it.label, it.value) },
                            onCloseSheet = safeModeViewModel::closeSheet,
                            onConfirmReadOnly = safeModeViewModel::confirmReadOnly,
                            onConfirmRestore = safeModeViewModel::confirmRestore,
                            onCreateExport = safeModeViewModel::createExport,
                            onEmailSupport = { safeModeViewModel.emailSupport(labels) },
                            onCopySupport = {
                                safeModeViewModel.copyToClipboard(
                                    "SORA support details",
                                    safeModeViewModel.supportText(labels),
                                )
                            },
                            onGetHelp = safeModeViewModel::onGetHelp,
                            onPinDigit = safeModeViewModel::pinDigit,
                            onPinDelete = safeModeViewModel::pinDelete,
                            onBiometric = safeModeViewModel::requestBiometric,
                            onRetryAuth = safeModeViewModel::retryAuthCheck,
                            onOpenEntry = { safeModeViewModel.openEntry(it) },
                            onReveal = safeModeViewModel::setRevealed,
                            onRevealBack = safeModeViewModel::backFromReveal,
                            onRevealClose = safeModeViewModel::closeReveal,
                        ),
                    )
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                safeModeViewModel.state.collect { state ->
                    viewBinding.walletRecoveryCompose.visibility =
                        if (state.visible) View.VISIBLE else View.GONE
                    if (state.visible) hideStartAnimation()
                    setSecureWindow(state.reveal != null)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                safeModeViewModel.events.collect(::onSafeModeEvent)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun detailLabels(): Map<String, String> = mapOf(
        SafeModeViewModel.DETAIL_CODE to stringResource(R.string.recovery_detail_code),
        SafeModeViewModel.DETAIL_LAST_ATTEMPT to stringResource(R.string.recovery_detail_last_attempt),
        SafeModeViewModel.DETAIL_VERSION to stringResource(R.string.recovery_detail_version),
        SafeModeViewModel.DETAIL_TIME to stringResource(R.string.recovery_detail_time),
        SafeModeViewModel.DETAIL_ANDROID to stringResource(R.string.recovery_detail_android),
    )

    private fun onSafeModeEvent(event: SafeModeEvent) {
        when (event) {
            SafeModeEvent.RetryReady -> {
                // The startup checks passed: run the wallet verification again and route like a
                // normal start (Recreating the Activity retains its ViewModel and the completed
                // failed migration result; a retry explicitly replaces that result).
                bindSplashViewModel()
                splashViewModel.retryWalletMigration()
            }

            SafeModeEvent.ReadOnlyReady -> {
                bindSplashViewModel()
                runCatching { nodeManager.get().ensureStarted() }
                splashViewModel.continueLegacyWallet()
            }

            is SafeModeEvent.ShareExport -> shareExport(event.file)
            SafeModeEvent.RestartForRestore -> restartApp()
            SafeModeEvent.OpenStorageSettings -> openStorageSettings()
            is SafeModeEvent.Email -> emailSupport(event.subject, event.body)
            SafeModeEvent.Copied -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(this, R.string.recovery_support_copied, Toast.LENGTH_SHORT).show()
            }

            is SafeModeEvent.Authenticate -> authenticate(event.deviceCredential)
        }
    }

    private fun goNext() {
        if (isNavigationRequested) return
        isNavigationRequested = true
        // A recreated screen whose start the early route already decided: nothing to route, no progress screen.
        if (splashViewModel.isStartDecided) return
        splashViewModel.nextScreen()
        // U0: a verification that finishes within 1.5 s stays silent; after that the progress
        // screen shows its real phases (U1, or U5 when resuming an interrupted one).
        progressWatchdog?.cancel()
        progressWatchdog = lifecycleScope.launch {
            delay(SafeModeViewModel.SILENT_UPGRADE_MILLIS)
            if (!isFinishing && safeModeViewModel.state.value.panel == null) {
                safeModeViewModel.showStartupProgress()
            }
        }
    }

    /**
     * Navigation after a verification: when the progress screen is showing, its last row gets
     * its tick first (design spec 9.3), then the next screen opens.
     */
    private fun afterVerification(navigate: () -> Unit) {
        progressWatchdog?.cancel()
        // This process has been through the startup checks: the wallet's main screen may open.
        WalletStartupPass.grant()
        safeModeViewModel.onVerificationRouted()
        if (safeModeViewModel.markProgressFinished()) {
            lifecycleScope.launch {
                delay(FINISH_TICK_MILLIS)
                navigate()
            }
        } else {
            navigate()
        }
    }

    private fun isSafeModeVisible(): Boolean = safeModeViewModel.state.value.visible

    /** The launch scene and its light page give way to what this screen shows in the theme's own colours. */
    private fun hideStartAnimation() {
        TenmonFlight.end(viewBinding.launchScene)
        viewBinding.launchScene.visibility = View.GONE
        viewBinding.splashContainer.background = null
        showLaunchPageBars(false)
        viewBinding.loadingDisclaimerTextView.visibility = View.GONE
        viewBinding.loadingProgressBar.visibility = View.GONE
    }

    private fun showRecovery(
        code: String,
        options: jp.co.soramitsu.core_db.WalletRecoveryOptions? = null,
        canContinueLegacyWallet: Boolean? = null,
    ) {
        progressWatchdog?.cancel()
        hideStartAnimation()
        safeModeViewModel.showPanel(
            code = code,
            options = options ?: WalletUpgradeBackup.recoveryOptions()?.takeIf { it.code == code },
            // False when the current account cannot be resolved: read-only would only come
            // back to this screen, so it is not offered.
            canContinueLegacyWallet = canContinueLegacyWallet,
            // An app start is not a question: where the wallet can open read-only behind its
            // PIN, it does. "Try upgrade again" from the read-only home gets its answer here.
            unattended = !openedForRetry,
        )
        reportStartupReady()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_OPENED_FOR_RETRY, openedForRetry)
        outState.putBoolean(STATE_SPLASH_BOUND, isSplashViewModelBound)
    }

    override fun onResume() {
        super.onResume()
        safeModeViewModel.onResumed()
    }

    override fun onPause() {
        safeModeViewModel.onPaused()
        super.onPause()
    }

    override fun onStop() {
        // A revealed recovery phrase never outlives the screen.
        if (!isChangingConfigurations) safeModeViewModel.onStopped()
        super.onStop()
    }

    private fun setSecureWindow(secure: Boolean) {
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!secure)
        }
    }

    /**
     * Keyguard's confirmation, on Android 8 to 10 only. Platform limit: it may accept the
     * phone's fingerprint too, and says so in its result's name ([ScreenLockPrompt]); Android 11
     * and later ask for the device credential alone and never accept this result.
     */
    private val keyguardConfirmation =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                safeModeViewModel.onAuthenticated(ScreenLockPrompt.SCREEN_LOCK_OR_SYSTEM_BIOMETRIC_PRE_R)
            } else {
                safeModeViewModel.onAuthenticationEnded()
            }
        }

    private fun authenticate(deviceCredential: Boolean) {
        if (deviceCredential && !ScreenLockPrompt.usesBiometricPrompt()) {
            // The phone's screen lock alone: Keyguard's own confirmation on Android 8 to 10.
            val request = ScreenLockPrompt.keyguardIntent(this, getString(R.string.recovery_auth_title), "")
            if (request == null || runCatching { keyguardConfirmation.launch(request) }.isFailure) {
                safeModeViewModel.onAuthenticationEnded()
            }
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    safeModeViewModel.onAuthenticated(result.authenticationType)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    safeModeViewModel.onAuthenticationEnded()
                }
            },
        )
        val info = if (deviceCredential) {
            // The screen lock alone, never a biometric in its place ([ScreenLockPrompt]).
            ScreenLockPrompt.promptInfo(getString(R.string.recovery_auth_title), null)
        } else {
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.recovery_auth_title))
                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText(getString(jp.co.soramitsu.common.R.string.common_cancel))
                .build()
        }
        runCatching { prompt.authenticate(info) }.onFailure { safeModeViewModel.onAuthenticationEnded() }
    }

    private fun shareExport(archive: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(this, "$packageName.soraFileProvider", archive)
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newUri(contentResolver, "SORA recovery file", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    getString(R.string.recovery_export),
                ),
            )
        }
    }

    /** Never crashes: without an email app the address is shown in the sheet instead. */
    private fun emailSupport(subject: String, body: String) {
        val mailto = buildString {
            append("mailto:").append(SafeModeViewModel.SUPPORT_EMAIL)
            append("?subject=").append(Uri.encode(subject))
            append("&body=").append(Uri.encode(body))
        }
        try {
            startActivity(Intent(Intent.ACTION_SENDTO, mailto.toUri()))
        } catch (_: ActivityNotFoundException) {
            safeModeViewModel.onNoEmailApp()
        } catch (_: SecurityException) {
            safeModeViewModel.onNoEmailApp()
        }
    }

    /** U4: the system storage screen. Never "Clear app data". */
    private fun openStorageSettings() {
        val opened = runCatching {
            startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
        }.isSuccess
        if (!opened) runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    /**
     * "Set up this device again" was recorded; the next process sets the old files aside (kept,
     * never deleted) before anything opens them. The same restart as after removing every
     * account: the launcher in a new task, then this process ends so nothing holds the old files.
     */
    private fun restartApp() {
        applicationContext.restartApplication()
    }

    private fun reportStartupReady() {
        if (isStartupReadyReported) return
        isStartupReadyReported = true
        StartupTimeTrace.end()
    }

    private companion object {
        const val FINISH_TICK_MILLIS = 300L
        const val STATE_OPENED_FOR_RETRY = "safeModeOpenedForRetry"
        const val STATE_SPLASH_BOUND = "safeModeSplashBound"
    }
}
