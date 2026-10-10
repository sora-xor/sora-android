/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.sora.splash.presentation

import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.intent.restartApplication
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.domain.DarkThemeManager
import jp.co.soramitsu.common.presentation.compose.neon.safemode.TechnicalDetail
import jp.co.soramitsu.common.presentation.compose.neon.safemode.UpgradeWelcome
import jp.co.soramitsu.common.presentation.compose.neon.safemode.WalletUpgradeRetry
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.feature_main_api.launcher.MainStarter
import jp.co.soramitsu.feature_multiaccount_api.MultiaccountStarter
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.sora.R
import jp.co.soramitsu.sora.databinding.ActivitySplashBinding
import jp.co.soramitsu.sora.splash.domain.StartupTimeTrace
import jp.co.soramitsu.sora.splash.presentation.recovery.SafeModeCallbacks
import jp.co.soramitsu.sora.splash.presentation.recovery.SafeModeEvent
import jp.co.soramitsu.sora.splash.presentation.recovery.SafeModeViewModel
import jp.co.soramitsu.sora.splash.presentation.recovery.SafeSheetKind
import jp.co.soramitsu.sora.splash.presentation.recovery.SplashSafeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The launcher screen: the start animation, the wallet verification, and Safe Mode (design spec
 * 9) when the upgrade needs the person: the upgrade progress (U1, U5), the storage screen (U4)
 * and the recovery options (U6), hosted in the `walletRecoveryCompose` view.
 */
@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

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

    private var isFirstPartFinished = false
    private var isSecondPartStarted = false
    private var isNavigationRequested = false
    private var isStartupReadyReported = false
    private var isSplashViewModelBound = false
    private var progressWatchdog: Job? = null

    private val animatorUpdateListener = ValueAnimator.AnimatorUpdateListener {
        val progress = it.animatedFraction
        if (progress >= 0.8 && !isFirstPartFinished) {
            isFirstPartFinished = true
            viewBinding.animationView.pauseAnimation()
        }
        if (splashViewModel.runtimeInitiated.value == true && isFirstPartFinished && !isSecondPartStarted) {
            isSecondPartStarted = true
            viewBinding.animationView.resumeAnimation()
        }
        if (progress >= 0.89) {
            goNext()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivitySplashBinding.inflate(layoutInflater).also { viewBinding = it }.root)
        setUpSafeMode()

        if (savedInstanceState == null && isRetryFromHome()) {
            // "Try upgrade again" from the read-only home screen: the same action as Try again
            // on the recovery screen, with the progress screen from the start. The splash view
            // model (which starts a verification) is bound only once the checks have passed.
            hideStartAnimation()
            isNavigationRequested = true
            safeModeViewModel.startRetryFromHome()
            return
        }
        bindSplashViewModel()

        // An interrupted verification is retried automatically first (SplashInteractor); the
        // recovery options appear only if that retry does not succeed.
        val startupFailure = WalletUpgradeBackup.startupFailureToShow(applicationContext)
        if (startupFailure == null) {
            viewBinding.animationView.addAnimatorUpdateListener(animatorUpdateListener)
        } else {
            showRecovery(startupFailure.code)
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
            if (it && !isSafeModeVisible()) {
                // Navigation still awaits the wallet migration result in SplashViewModel. Start
                // that wait after the short launch floor instead of making a completed migration
                // sit behind 89% of the three-second Lottie animation.
                goNext()
            }
        }
        splashViewModel.loadingTextVisiblity.observe(this) {
            if (!isSafeModeVisible()) {
                viewBinding.loadingDisclaimerTextView.visibility = View.VISIBLE
                viewBinding.loadingProgressBar.visibility = View.VISIBLE
            }
        }
        splashViewModel.showMainScreen.observe(this) {
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
            afterVerification {
                // A wallet set up on this version never needs the upgrade-complete sheet (U11).
                UpgradeWelcome.markDone(applicationContext)
                reportStartupReady()
                multiaccStarter.startOnboardingFlow(this)
            }
        }
        splashViewModel.showMainScreenFromInviteLink.observe(this) {
            afterVerification {
                runCatching { nodeManager.get().ensureStarted() }
                reportStartupReady()
                mainStarter.startWithInvite(this)
                finish()
            }
        }
        splashViewModel.showMigrationRecovery.observe(this) { recovery ->
            showRecovery(recovery.code, recovery.options)
        }
    }

    private fun setUpSafeMode() {
        viewBinding.walletRecoveryCompose.setContent {
            val dark by darkThemeManager.darkModeStatusFlow.collectAsStateWithLifecycle()
            val state by safeModeViewModel.state.collectAsStateWithLifecycle()
            SoraAppTheme(darkTheme = dark) {
                if (state.visible) {
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
                            onGetHelp = { safeModeViewModel.openSheet(SafeSheetKind.SUPPORT) },
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
        viewBinding.animationView.removeUpdateListener(animatorUpdateListener)
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

    private fun hideStartAnimation() {
        viewBinding.animationView.cancelAnimation()
        viewBinding.animationView.visibility = View.GONE
        viewBinding.loadingDisclaimerTextView.visibility = View.GONE
        viewBinding.loadingProgressBar.visibility = View.GONE
    }

    private fun showRecovery(
        code: String,
        options: jp.co.soramitsu.core_db.WalletRecoveryOptions? = null,
    ) {
        progressWatchdog?.cancel()
        hideStartAnimation()
        safeModeViewModel.showPanel(code, options ?: WalletUpgradeBackup.recoveryOptions()?.takeIf { it.code == code })
        reportStartupReady()
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

    private fun authenticate(deviceCredential: Boolean) {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    safeModeViewModel.onAuthenticated()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    safeModeViewModel.onAuthenticationEnded()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.recovery_auth_title))
            .apply {
                if (deviceCredential) {
                    setAllowedAuthenticators(SafeModeViewModel.DEVICE_CREDENTIAL_AUTHENTICATORS)
                } else {
                    setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    setNegativeButtonText(getString(jp.co.soramitsu.common.R.string.common_cancel))
                }
            }
            .build()
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
    }
}
