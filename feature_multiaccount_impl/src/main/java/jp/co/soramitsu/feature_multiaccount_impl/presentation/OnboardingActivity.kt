/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.vanpra.composematerialdialogs.MaterialDialog
import com.vanpra.composematerialdialogs.listItems
import com.vanpra.composematerialdialogs.rememberMaterialDialogState
import com.vanpra.composematerialdialogs.title
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.animatedComposable
import jp.co.soramitsu.common.presentation.compose.webview.WebView
import jp.co.soramitsu.common.presentation.view.SoraBaseActivity
import jp.co.soramitsu.feature_multiaccount_impl.presentation.backup_password.BackupPasswordScreen
import jp.co.soramitsu.feature_multiaccount_impl.presentation.create_account.CreateAccountScreen
import jp.co.soramitsu.feature_multiaccount_impl.presentation.enter_passphrase.EnterPassphraseScreen
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup.BackupScreen
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection.ExportProtection
import jp.co.soramitsu.feature_multiaccount_impl.presentation.mnemonic_confirmation.MnemonicConfirmationScreen
import jp.co.soramitsu.feature_multiaccount_impl.presentation.tutorial.TermsAndPrivacyEnum
import jp.co.soramitsu.feature_multiaccount_impl.presentation.tutorial.TutorialScreen

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
            Log.e("TAGAA", "AAA" + result.toString())
            if (result.resultCode != Activity.RESULT_OK) {
                Toast.makeText(this, "Google signin failed", Toast.LENGTH_SHORT).show() //todo showError
            } else {
                viewModel.onSuccessfulGoogleSignin(this@OnboardingActivity, navController)
            }
        }

    override val viewModel: OnboardingViewModel by viewModels()

    override fun onToolbarNavigation() {
        val pop = navController.popBackStack()
        if (!pop) finish()
    }

    @OptIn(
        ExperimentalAnimationApi::class, ExperimentalUnitApi::class,
    )
    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState) {
        navController = rememberAnimatedNavController()
        LaunchedEffect(Unit) {
            navController.addOnDestinationChangedListener { _, destination, _ ->
                viewModel.onDestinationChanged(destination.route ?: "")
            }
        }
        AnimatedNavHost(
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
                val dialogState = rememberMaterialDialogState()

                Box(
                    contentAlignment = Alignment.BottomCenter
                ) {
                    TutorialScreen(
                        onCreateAccount = { viewModel.onCreateAccountClicked(navController) },
                        onImportAccount = { dialogState.show() },
                        onGoogleSignin = { viewModel.onGoogleSignin(navController, this@OnboardingActivity, launcher) },
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

                    MaterialDialog(dialogState = dialogState) {
                        title(text = stringResource(id = R.string.recovery_source_type))
                        listItems(
                            listOf(
                                stringResource(id = R.string.common_passphrase_title),
                                stringResource(id = R.string.common_raw_seed)
                            ),
                            onClick = { index, _ ->
                                viewModel.onRecoveryClicked(navController, index)
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
                            onSkipButtonPressed = { viewModel.onSkipButtonPressed(this@OnboardingActivity) }
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
                            viewModel.onSetBackupPasswordClicked(
                                this@OnboardingActivity
                            )
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
