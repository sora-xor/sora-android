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
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.feature_main_api.launcher.MainStarter
import jp.co.soramitsu.feature_multiaccount_api.MultiaccountStarter
import jp.co.soramitsu.sora.databinding.ActivitySplashBinding
import kotlinx.coroutines.Dispatchers
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

        viewBinding.retryWalletMigrationButton.setOnClickListener { button ->
            button.isEnabled = false
            lifecycleScope.launch {
                val migrationRetry =
                    WalletUpgradeBackup.canAuthorizeMigrationRetry()
                val result = withContext(Dispatchers.IO) {
                    if (migrationRetry) {
                        WalletUpgradeBackup.authorizeMigrationRetry(applicationContext)
                    } else {
                        WalletUpgradeBackup.prepare(applicationContext)
                    }
                }
                button.isEnabled = true
                if (result.isSuccess) {
                    if (migrationRetry) {
                        splashViewModel.retryWalletMigration()
                    } else {
                        recreate()
                    }
                } else {
                    showRecovery(
                        code =
                            (result.exceptionOrNull() as?
                                jp.co.soramitsu.core_db.WalletUpgradeBackupException)
                                ?.code
                                ?: WalletUpgradeBackup.blockingFailure()?.code
                                ?: "BACKUP_RETRY_FAILED",
                        canContinueLegacyWallet = false,
                    )
                }
            }
        }
        viewBinding.contactWalletSupportButton.setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_SENDTO,
                    Uri.parse("mailto:support@sora.org?subject=SORA%20wallet%20upgrade%20recovery")
                )
            )
        }
        viewBinding.continueLegacyWalletButton.setOnClickListener {
            val button = viewBinding.continueLegacyWalletButton
            button.isEnabled = false
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    WalletUpgradeBackup.authorizeLegacyReadOnly(applicationContext)
                }
                button.isEnabled = true
                if (result.isSuccess) {
                    splashViewModel.continueLegacyWallet()
                } else {
                    showRecovery(
                        code =
                            (result.exceptionOrNull() as?
                                jp.co.soramitsu.core_db.WalletUpgradeBackupException)
                                ?.code
                                ?: "LEGACY_RECOVERY_AUTHORIZATION_FAILED",
                        canContinueLegacyWallet = false,
                    )
                }
            }
        }
        viewBinding.exportWalletRecoveryButton.setOnClickListener { button ->
            button.isEnabled = false
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    WalletUpgradeBackup.createRecoveryArchive(applicationContext)
                }
                button.isEnabled = true
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
                            getString(
                                jp.co.soramitsu.sora.R.string.wallet_upgrade_recovery_export,
                            ),
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
                viewBinding.walletRecoveryPanel.visibility != View.VISIBLE &&
                isFirstPartFinished &&
                !isSecondPartStarted
            ) {
                isSecondPartStarted = true
                viewBinding.animationView.resumeAnimation()
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
                mainStarter.start(this)
            } else {
                showRecovery(
                    code = backupFailure.code,
                    canContinueLegacyWallet = false,
                )
            }
        }
        splashViewModel.showOnBoardingScreen.observe(this) {
            multiaccStarter.startOnboardingFlow(this)
        }
        splashViewModel.showMainScreenFromInviteLink.observe(this) {
            mainStarter.startWithInvite(this)
            finish()
        }
        splashViewModel.showMigrationRecovery.observe(this) { recovery ->
            showRecovery(
                code = recovery.code,
                canContinueLegacyWallet = recovery.canContinueLegacyWallet,
            )
        }

        val startupFailure = WalletUpgradeBackup.blockingFailure()
        if (startupFailure == null) {
            viewBinding.animationView.addAnimatorUpdateListener(animatorUpdateListener)
        } else {
            showRecovery(
                code = startupFailure.code,
                canContinueLegacyWallet =
                    WalletUpgradeBackup.hasValidatedLegacyRecovery(),
            )
        }
    }

    private fun goNext() {
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
        viewBinding.walletRecoveryCodeTextView.text =
            getString(jp.co.soramitsu.sora.R.string.wallet_upgrade_recovery_code, code)
        viewBinding.continueLegacyWalletButton.visibility =
            if (canContinueLegacyWallet) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }
}
