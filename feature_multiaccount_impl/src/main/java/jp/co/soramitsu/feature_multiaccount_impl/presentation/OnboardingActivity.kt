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

package jp.co.soramitsu.feature_multiaccount_impl.presentation

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.presentation.compose.components.animatedComposable
import jp.co.soramitsu.common.presentation.compose.webview.WebView
import jp.co.soramitsu.common.presentation.view.SoraBaseActivity
import jp.co.soramitsu.common.util.DebounceClickHandler
import jp.co.soramitsu.feature_multiaccount_impl.presentation.backup_password.BackupPasswordScreen
import jp.co.soramitsu.feature_multiaccount_impl.presentation.create_account.CreateAccountScreen
import jp.co.soramitsu.feature_multiaccount_impl.presentation.enter_passphrase.EnterPassphraseScreen
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup.BackupScreen
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection.ExportProtection
import jp.co.soramitsu.feature_multiaccount_impl.presentation.import_account_list.ImportAccountListScreen
import jp.co.soramitsu.feature_multiaccount_impl.presentation.import_account_list.import_account_password.ImportAccountPasswordScreen
import jp.co.soramitsu.feature_multiaccount_impl.presentation.import_account_list.import_account_success.ImportAccountSuccessScreen
import jp.co.soramitsu.feature_multiaccount_impl.presentation.mnemonic_confirmation.MnemonicConfirmationScreen
import jp.co.soramitsu.feature_multiaccount_impl.presentation.tutorial.TermsAndPrivacyEnum
import jp.co.soramitsu.feature_multiaccount_impl.presentation.tutorial.TutorialScreen
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@AndroidEntryPoint
class OnboardingActivity : SoraBaseActivity<OnboardingViewModel>() {

    companion object {

        const val ACTION_INVITE = "jp.co.soramitsu.feature_onboarding_impl.ACTION_INVITE"

        fun start(context: Context, isClearTask: Boolean) {
            val intent = Intent(context, OnboardingActivity::class.java).apply {
                if (isClearTask) {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            }
            val options = ActivityOptions.makeCustomAnimation(
                context,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            context.startActivity(intent, options.toBundle())
        }

        fun startWithInviteLink(context: Context) {
            val intent = Intent(context, OnboardingActivity::class.java).apply {
                action = ACTION_INVITE
            }
            val options = ActivityOptions.makeCustomAnimation(
                context,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            context.startActivity(intent, options.toBundle())
        }
    }

    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                viewModel.onError(SoraException.businessError(ResponseCode.GOOGLE_LOGIN_FAILED))
            } else {
                viewModel.onSuccessfulGoogleSignin(navController)
            }
        }

    private val consentHandlerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                viewModel.onError(SoraException.businessError(ResponseCode.GOOGLE_LOGIN_FAILED))
            } else {
                viewModel.onConsentSuccess(navController)
            }
        }

    override val viewModel: OnboardingViewModel by viewModels()

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    override fun onToolbarNavigation() {
        val pop = navController.popBackStack()
        if (!pop) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.consentExceptionHandler.observe(this) {
            consentHandlerLauncher.launch(it)
        }
    }

    @OptIn(
        ExperimentalAnimationApi::class, ExperimentalUnitApi::class,
    )
    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState) {
        navController = rememberNavController()
        LaunchedEffect(Unit) {
            navController.addOnDestinationChangedListener { _, destination, _ ->
                viewModel.onDestinationChanged(destination.route ?: "")
            }
        }

        viewModel.skipDialogState.observeAsState().value?.let {
            if (it) {
                AlertDialog(
                    backgroundColor = MaterialTheme.customColors.bgPage,
                    title = {
                        Text(
                            text = stringResource(id = R.string.import_account_not_backed_up),
                            color = MaterialTheme.customColors.fgPrimary,
                            style = MaterialTheme.customTypography.textSBold
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(id = R.string.import_account_not_backed_up_alert_description),
                            color = MaterialTheme.customColors.fgPrimary,
                            style = MaterialTheme.customTypography.paragraphSBold
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.skipDialogConfirm(this@OnboardingActivity)
                            }
                        ) {
                            Text(
                                text = stringResource(id = R.string.import_account_not_backed_up_alert_action_title),
                                color = MaterialTheme.customColors.statusError,
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = viewModel::skipDialogDismiss
                        ) {
                            Text(
                                color = MaterialTheme.customColors.fgPrimary,
                                text = stringResource(id = R.string.common_cancel),
                            )
                        }
                    },
                    onDismissRequest = viewModel::skipDialogDismiss
                )
            }
        }

        NavHost(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState),
            navController = navController as NavHostController,
            startDestination = OnboardingFeatureRoutes.TUTORIAL
        ) {
            animatedComposable(
                route = OnboardingFeatureRoutes.TUTORIAL
            ) {
                val recoveryDialog = viewModel.recoveryDialog.collectAsStateWithLifecycle()
                if (recoveryDialog.value) {
                    AlertDialog(
                        backgroundColor = MaterialTheme.customColors.bgPage,
                        onDismissRequest = viewModel::onRecoverySourceDismiss,
                        text = {
                            Text(
                                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                                text = stringResource(id = R.string.recovery_source_type),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.customTypography.headline2,
                                color = MaterialTheme.customColors.fgPrimary,
                            )
                        },
                        buttons = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .padding(Dimens.x1),
                                verticalArrangement = Arrangement.spacedBy(Dimens.x1),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                jp.co.soramitsu.ui_core.component.button.TextButton(
                                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                                    size = Size.Small,
                                    order = Order.TERTIARY,
                                    text = stringResource(id = R.string.common_google),
                                    onClick = {
                                        viewModel.onGoogleSignin(
                                            navController,
                                            launcher
                                        )
                                    },
                                )
                                jp.co.soramitsu.ui_core.component.button.TextButton(
                                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                                    size = Size.Small,
                                    order = Order.TERTIARY,
                                    text = stringResource(id = R.string.common_passphrase_title),
                                    onClick = {
                                        viewModel.onRecoveryClicked(
                                            navController,
                                            1
                                        )
                                    },
                                )
                                jp.co.soramitsu.ui_core.component.button.TextButton(
                                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                                    size = Size.Small,
                                    order = Order.TERTIARY,
                                    text = stringResource(id = R.string.common_raw_seed),
                                    onClick = {
                                        viewModel.onRecoveryClicked(
                                            navController,
                                            2
                                        )
                                    },
                                )
                            }
                        },
                    )
                }

                viewModel.tutorialScreenState.observeAsState().value?.let {
                    Box(
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        TutorialScreen(
                            state = it,
                            onCreateAccount = { viewModel.onCreateAccountClicked(navController) },
                            onImportAccount = viewModel::onRecoveryDialogShow,
                            onGoogleSignin = {
                                debounceClickHandler.debounceClick {
                                    viewModel.onGoogleSignin(
                                        navController,
                                        launcher
                                    )
                                }
                            },
                            onTermsAndPrivacyClicked = {
                                when (it) {
                                    TermsAndPrivacyEnum.TERMS -> viewModel.onTermsClicked(
                                        navController
                                    )

                                    TermsAndPrivacyEnum.PRIVACY -> viewModel.onPrivacyClicked(
                                        navController
                                    )
                                }
                            }
                        )
                    }
                }
            }

            animatedComposable(
                route = OnboardingFeatureRoutes.CREATE_ACCOUNT
            ) {
                viewModel.createAccountCardState.observeAsState().value?.let {
                    Box {
                        CreateAccountScreen(
                            it,
                            viewModel::onAccountNameChanged
                        ) { viewModel.onCreateAccountContinueClicked(navController) }
                    }
                }
            }

            animatedComposable(
                route = OnboardingFeatureRoutes.DISCLAIMER
            ) {
                viewModel.disclaimerCardState.observeAsState().value?.let {
                    Box {
                        ExportProtection(
                            it,
                            viewModel::onItemClicked
                        ) { navController.navigate(OnboardingFeatureRoutes.PASSPHRASE) }
                    }
                }
            }

            animatedComposable(
                route = OnboardingFeatureRoutes.PASSPHRASE
            ) {
                viewModel.passphraseCardState.observeAsState().value?.let {
                    Box {
                        BackupScreen(
                            it,
                            onButtonPressed = {
                                viewModel.onPassphraseContinueClicked(
                                    navController
                                )
                            },
                            onBackupWithGoogleButtonPressed = if (!it.isViaGoogleDrive) {
                                {
                                    viewModel.onGoogleSignin(
                                        navController,
                                        launcher
                                    )
                                }
                            } else null
                        )
                    }
                }
            }

            animatedComposable(
                route = OnboardingFeatureRoutes.PASSPHRASE_CONFIRMATION
            ) {
                viewModel.passphraseConfirmationState.observeAsState().value?.let {
                    MnemonicConfirmationScreen(
                        it
                    ) {
                        viewModel.onConfirmationButtonPressed(this@OnboardingActivity, it)
                    }
                }
            }

            animatedComposable(
                route = OnboardingFeatureRoutes.TERMS_AND_PRIVACY
            ) {
                viewModel.termsAndPrivacyState.observeAsState().value?.let {
                    WebView(
                        state = it.webViewState,
                        onPageFinished = viewModel::onTermsAndPrivacyLoadingFinished
                    )
                }
            }

            animatedComposable(
                route = OnboardingFeatureRoutes.RECOVERY
            ) {
                viewModel.recoveryState.observeAsState().value?.let {
                    Box {
                        EnterPassphraseScreen(
                            recoveryState = it,
                            onRecoveryInputChanged = viewModel::onRecoveryInputChanged
                        ) {
                            navController.navigate(OnboardingFeatureRoutes.RECOVERY_ACCOUNT_NAME)
                        }
                    }
                }
            }

            animatedComposable(
                route = OnboardingFeatureRoutes.RECOVERY_ACCOUNT_NAME
            ) {
                viewModel.recoveryAccountNameCardState.observeAsState().value?.let {
                    Box {
                        CreateAccountScreen(
                            it,
                            viewModel::onRecoveryAccountChanged
                        ) {
                            viewModel.recoveryNextClicked(
                                navController,
                                this@OnboardingActivity
                            )
                        }
                    }
                }
            }

            animatedComposable(
                route = OnboardingFeatureRoutes.CREATE_BACKUP_PASSWORD
            ) {
                viewModel.createBackupPasswordState.observeAsState().value?.let {
                    Box {
                        BackupPasswordScreen(
                            it,
                            viewModel::onBackupPasswordChanged,
                            viewModel::onBackupPasswordConfirmationChanged,
                            viewModel::onWarningToggle
                        ) {
                            debounceClickHandler.debounceClick {
                                viewModel.onSetBackupPasswordClicked(
                                    this@OnboardingActivity
                                )
                            }
                        }
                    }
                }
            }
            animatedComposable(
                route = OnboardingFeatureRoutes.IMPORT_ACCOUNT_LIST
            ) {
                viewModel.importAccountListState.observeAsState().value?.let {
                    Box {
                        ImportAccountListScreen(
                            it,
                            {
                                viewModel.onImportAccountSelected(navController, it)
                            },
                            {
                                navController.navigate(OnboardingFeatureRoutes.CREATE_ACCOUNT)
                            }
                        )
                    }
                }
            }

            animatedComposable(
                route = OnboardingFeatureRoutes.IMPORT_ACCOUNT_PASSWORD
            ) {
                viewModel.importAccountPasswordState.observeAsState().value?.let {
                    Box {
                        ImportAccountPasswordScreen(
                            it,
                            viewModel::onImportPasswordChanged
                        ) {
                            debounceClickHandler.debounceClick {
                                viewModel.onImportContinueClicked(
                                    navController
                                )
                            }
                        }
                    }
                }
            }

            animatedComposable(
                route = OnboardingFeatureRoutes.IMPORT_ACCOUNT_SUCCESS
            ) {
                viewModel.importAccountPasswordState.observeAsState().value?.let {
                    Box {
                        ImportAccountSuccessScreen(
                            it,
                            { viewModel.onImportFinished(this@OnboardingActivity) }
                        ) {
                            debounceClickHandler.debounceClick {
                                viewModel.onImportMoreClicked(
                                    navController
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private lateinit var navController: NavController

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        intent?.let {
            if (ACTION_INVITE == it.action) {
                viewModel.startedWithInviteAction()
            }
        }
    }
}
