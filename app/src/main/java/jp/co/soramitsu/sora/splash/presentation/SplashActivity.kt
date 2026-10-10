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
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.feature_main_api.launcher.MainStarter
import jp.co.soramitsu.feature_multiaccount_api.MultiaccountStarter
import jp.co.soramitsu.sora.R
import jp.co.soramitsu.sora.databinding.ActivitySplashBinding
import jp.co.soramitsu.sora.splash.domain.StartupTimeTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    private val splashViewModel: SplashViewModel by viewModels()

    private lateinit var viewBinding: ActivitySplashBinding

    @Inject
    lateinit var multiaccStarter: MultiaccountStarter

    @Inject
    lateinit var mainStarter: MainStarter

    private var isFirstPartFinished = false
    private var isSecondPartStarted = false
    private var isNavigationRequested = false
    private var isStartupReadyReported = false

    /** Code of the recovery state on screen; shown under Technical details and sent to support. */
    private var recoveryCode: String? = null

    /** One recovery action at a time; the running one reads "Working…". */
    private var isRecoveryActionRunning = false
    private var runningButtonLabel: CharSequence? = null
    private var runningButtonMinHeight = 0

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

        viewBinding.retryWalletMigrationButton.setOnClickListener {
            runRecoveryAction(
                button = viewBinding.retryWalletMigrationButton,
                // Re-runs the startup checks and, for an interrupted wallet verification,
                // authorizes one more run. Never deletes or rewrites wallet data.
                work = { WalletUpgradeBackup.retry(applicationContext) },
            ) { result ->
                if (result.isSuccess) {
                    // Recreating the Activity retains its ViewModel and the completed failed
                    // migration result. A successful preflight must explicitly replace that result
                    // and rerun wallet verification before navigation, just like a journal retry.
                    splashViewModel.retryWalletMigration()
                } else {
                    showRecovery(
                        code =
                            (result.exceptionOrNull() as?
                                jp.co.soramitsu.core_db.WalletUpgradeBackupException)
                                ?.code
                                ?: WalletUpgradeBackup.blockingFailure()?.code
                                ?: "BACKUP_RETRY_FAILED",
                        canContinueLegacyWallet =
                            WalletUpgradeBackup.recoveryOptions()?.canReadOnly == true,
                    )
                }
            }
        }
        viewBinding.contactWalletSupportButton.setOnClickListener { contactSupport() }
        viewBinding.walletRecoveryDetailsButton.setOnClickListener {
            val show = viewBinding.walletRecoveryCodeTextView.visibility != View.VISIBLE
            viewBinding.walletRecoveryCodeTextView.visibility = if (show) View.VISIBLE else View.GONE
            viewBinding.walletRecoveryDetailsButton.setText(
                if (show) {
                    R.string.wallet_upgrade_recovery_details_hide
                } else {
                    R.string.wallet_upgrade_recovery_details_show
                }
            )
        }
        viewBinding.continueLegacyWalletButton.setOnClickListener {
            runRecoveryAction(
                button = viewBinding.continueLegacyWalletButton,
                work = { WalletUpgradeBackup.authorizeSafeReadOnly(applicationContext) },
            ) { result ->
                val route = result.getOrNull()
                if (
                    route == WalletUpgradeBackup.SafeReadOnlyRoute.ROOM_READ_ONLY ||
                    route == WalletUpgradeBackup.SafeReadOnlyRoute.NORMAL_ACCESS
                ) {
                    splashViewModel.continueLegacyWallet()
                } else {
                    showRecovery(
                        code =
                            (result.exceptionOrNull() as?
                                jp.co.soramitsu.core_db.WalletUpgradeBackupException)
                                ?.code
                                ?: "LEGACY_RECOVERY_AUTHORIZATION_FAILED",
                        canContinueLegacyWallet =
                            WalletUpgradeBackup.recoveryOptions()?.canReadOnly == true,
                    )
                }
            }
        }
        viewBinding.exportWalletRecoveryButton.setOnClickListener {
            runRecoveryAction(
                button = viewBinding.exportWalletRecoveryButton,
                work = { WalletUpgradeBackup.createRecoveryArchive(applicationContext) },
            ) { result ->
                result.onSuccess { archive ->
                    val uri = FileProvider.getUriForFile(
                        this@SplashActivity,
                        "$packageName.soraFileProvider",
                        archive,
                    )
                    startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newUri(
                                    contentResolver,
                                    "SORA encrypted recovery archive",
                                    uri,
                                )
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                            getString(R.string.wallet_upgrade_recovery_export),
                        )
                    )
                }.onFailure {
                    showRecovery(
                        code =
                            (it as? jp.co.soramitsu.core_db.WalletUpgradeBackupException)?.code
                                ?: "RECOVERY_EXPORT_FAILED",
                        canContinueLegacyWallet = false,
                    )
                }
            }
        }

        splashViewModel.runtimeInitiated.observe(
            this
        ) {
            if (
                it &&
                viewBinding.walletRecoveryPanel.visibility != View.VISIBLE
            ) {
                // Navigation still awaits the wallet migration result in SplashViewModel. Start
                // that wait after the short launch floor instead of making a completed migration
                // sit behind 89% of the three-second Lottie animation.
                goNext()
            }
        }

        splashViewModel.loadingTextVisiblity.observe(
            this
        ) {
            if (viewBinding.walletRecoveryPanel.visibility != View.VISIBLE) {
                viewBinding.loadingDisclaimerTextView.visibility = View.VISIBLE
                viewBinding.loadingProgressBar.visibility = View.VISIBLE
            }
        }

        splashViewModel.showMainScreen.observe(this) {
            val backupFailure = WalletUpgradeBackup.blockingFailure()
            if (backupFailure == null) {
                reportStartupReady()
                mainStarter.start(this)
            } else {
                showRecovery(
                    code = backupFailure.code,
                    canContinueLegacyWallet = false,
                )
            }
        }
        splashViewModel.showOnBoardingScreen.observe(this) {
            reportStartupReady()
            multiaccStarter.startOnboardingFlow(this)
        }
        splashViewModel.showMainScreenFromInviteLink.observe(this) {
            reportStartupReady()
            mainStarter.startWithInvite(this)
            finish()
        }
        splashViewModel.showMigrationRecovery.observe(this) { recovery ->
            showRecovery(
                code = recovery.code,
                canContinueLegacyWallet = recovery.canContinueLegacyWallet,
            )
        }

        // An interrupted verification is retried automatically first (SplashInteractor); the
        // recovery options appear only if that retry does not succeed.
        val startupFailure = WalletUpgradeBackup.startupFailureToShow(applicationContext)
        if (startupFailure == null) {
            viewBinding.animationView.addAnimatorUpdateListener(animatorUpdateListener)
        } else {
            showRecovery(
                code = startupFailure.code,
                canContinueLegacyWallet =
                    WalletUpgradeBackup.recoveryOptions()?.canReadOnly == true,
            )
        }
    }

    private fun goNext() {
        if (isNavigationRequested) return
        isNavigationRequested = true
        viewBinding.animationView.removeUpdateListener(animatorUpdateListener)
        splashViewModel.nextScreen()
    }

    private fun showRecovery(
        code: String,
        canContinueLegacyWallet: Boolean,
    ) {
        viewBinding.animationView.cancelAnimation()
        viewBinding.animationView.visibility = View.GONE
        viewBinding.loadingDisclaimerTextView.visibility = View.GONE
        viewBinding.loadingProgressBar.visibility = View.GONE
        viewBinding.walletRecoveryPanel.visibility = View.VISIBLE
        recoveryCode = code
        viewBinding.walletRecoveryCodeTextView.text =
            getString(R.string.wallet_upgrade_recovery_code, code)
        viewBinding.continueLegacyWalletButton.visibility =
            if (canContinueLegacyWallet) {
                View.VISIBLE
            } else {
                View.GONE
            }
        reportStartupReady()
    }

    /**
     * Runs one recovery action off the main thread. While it runs, the tapped button keeps its
     * colours and reads "Working…", the other actions are disabled (greyed), a thin progress line
     * shows, and after 10 s a "Still working…" line appears (design spec 7.1, 9.1-5). Contact
     * support and Technical details stay available.
     */
    private fun <T> runRecoveryAction(
        button: MaterialButton,
        work: () -> T,
        onDone: (T) -> Unit,
    ) {
        if (isRecoveryActionRunning) return
        isRecoveryActionRunning = true
        showRecoveryActionRunning(button, running = true)
        lifecycleScope.launch {
            val stillWorking = launch {
                delay(STILL_WORKING_AFTER_MILLIS)
                viewBinding.walletRecoveryStatusTextView.visibility = View.VISIBLE
            }
            val result = try {
                withContext(Dispatchers.IO) { work() }
            } finally {
                stillWorking.cancel()
                showRecoveryActionRunning(button, running = false)
                isRecoveryActionRunning = false
            }
            onDone(result)
        }
    }

    private fun showRecoveryActionRunning(button: MaterialButton, running: Boolean) {
        val actions = listOf(
            viewBinding.retryWalletMigrationButton,
            viewBinding.exportWalletRecoveryButton,
            viewBinding.continueLegacyWalletButton,
        )
        if (running) {
            runningButtonLabel = button.text
            runningButtonMinHeight = button.minHeight
            // Keep the height, so a two-line label does not make the panel jump.
            button.minHeight = button.height
            button.setText(R.string.wallet_upgrade_recovery_working)
        } else {
            runningButtonLabel?.let { button.text = it }
            button.minHeight = runningButtonMinHeight
            runningButtonLabel = null
        }
        actions.forEach { action ->
            action.isEnabled = !running
            action.isActivated = running && action === button
        }
        viewBinding.walletRecoveryProgressBar.visibility =
            if (running) View.VISIBLE else View.INVISIBLE
        if (!running) viewBinding.walletRecoveryStatusTextView.visibility = View.GONE
    }

    private fun contactSupport() {
        val body = recoveryCode?.let { getString(R.string.wallet_upgrade_recovery_code, it) }
        val mailto = buildString {
            append("mailto:").append(SUPPORT_EMAIL)
            append("?subject=").append(Uri.encode("SORA wallet upgrade recovery"))
            if (body != null) append("&body=").append(Uri.encode(body))
        }
        try {
            startActivity(Intent(Intent.ACTION_SENDTO, mailto.toUri()))
        } catch (e: ActivityNotFoundException) {
            // No email app: show the address instead of failing silently or crashing.
            Toast.makeText(
                this,
                getString(R.string.wallet_upgrade_recovery_support_no_email, SUPPORT_EMAIL),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun reportStartupReady() {
        if (isStartupReadyReported) return
        isStartupReadyReported = true
        StartupTimeTrace.end()
    }

    private companion object {
        const val SUPPORT_EMAIL = "support@sora.org"
        const val STILL_WORKING_AFTER_MILLIS = 10_000L
    }
}
