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
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.presentation.compose.neon.safemode.UpgradeWelcome
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

        /** File managers label an exported backup in different ways, so any type can be picked. */
        private const val ANY_FILE_TYPE = "*/*"
        private const val BACKUP_FILE_CHUNK_BYTES = 8 * 1024
        private const val MAX_FILE_NAME_CHARS = 80

        /**
         * Above any account export this app writes: a batch of the 100 accounts the import
         * accepts is about 100 KB. A larger file is not a backup of this app and is not read.
         */
        private const val MAX_BACKUP_FILE_BYTES = 128 * 1024

        /** The phone's screen lock (PIN, pattern, password) or a biometric that stands for it. */
        private const val SCREEN_LOCK_AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

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
    }

    /** Lets the person pick the encrypted backup file (JSON) they exported earlier. */
    private val backupFilePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                lifecycleScope.launch {
                    val text = withContext(Dispatchers.IO) { readBackupFile(uri) }
                    if (text == null) {
                        viewModel.onRecoveryFileUnreadable()
                    } else {
                        // The view model checks that it is a backup file and keeps its text;
                        // the screen shows only the file's name.
                        val name = withContext(Dispatchers.IO) { backupFileName(uri) }
                        viewModel.onRecoveryFileChosen(text, name)
                    }
                }
            }
        }

    /** The name the file picker shows for [uri], or "" when it gives none. */
    private fun backupFileName(uri: Uri): String = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull().orEmpty().take(MAX_FILE_NAME_CHARS)

    /**
     * Confirms a repair with the phone's screen lock. Used only when a PIN is stored on this
     * phone but cannot be read, so the PIN cannot confirm anything.
     */
    private fun confirmRepairWithScreenLock() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.onRepairConfirmed(pin = "", screenLockConfirmed = true)
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.account_repair_screen_lock_prompt))
            .setAllowedAuthenticators(SCREEN_LOCK_AUTHENTICATORS)
            .build()
        // A prompt that cannot be shown changes nothing: the dialog stays open.
        runCatching { prompt.authenticate(info) }
    }

    private fun screenLockAvailable(): Boolean = runCatching {
        BiometricManager.from(this).canAuthenticate(SCREEN_LOCK_AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)

    /** The text of a chosen backup file, or null when it cannot be read or is far too large. */
    private fun readBackupFile(uri: Uri): String? = runCatching {
        contentResolver.openInputStream(uri)?.use { stream ->
            val out = ByteArrayOutputStream()
            val chunk = ByteArray(BACKUP_FILE_CHUNK_BYTES)
            while (true) {
                val read = stream.read(chunk)
                if (read < 0) break
                out.write(chunk, 0, read)
                if (out.size() > MAX_BACKUP_FILE_BYTES) return@runCatching null
            }
            out.toString(Charsets.UTF_8.name())
        }
    }.getOrNull()

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
        // Setting up a wallet on this version (first start, after "set up this device again", or
        // adding an account after the first unlock) never needs the upgrade-complete sheet.
        UpgradeWelcome.markDone(applicationContext)

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

        viewModel.repairPrompt.collectAsStateWithLifecycle().value?.let { prompt ->
            RepairAccountDialog(
                prompt = prompt,
                screenLockAvailable = !prompt.screenLockRequired || screenLockAvailable(),
                onConfirm = { pin -> viewModel.onRepairConfirmed(pin) },
                onConfirmWithScreenLock = ::confirmRepairWithScreenLock,
                onDismiss = viewModel::onRepairDismissed,
            )
        }

        viewModel.batchImportNotice.collectAsStateWithLifecycle().value?.let { notice ->
            val dismiss = { viewModel.onBatchImportNoticeDismissed(this@OnboardingActivity) }
            AlertDialog(
                backgroundColor = MaterialTheme.customColors.bgPage,
                onDismissRequest = dismiss,
                text = {
                    Text(
                        text = notice.message,
                        color = MaterialTheme.customColors.fgPrimary,
                        style = MaterialTheme.customTypography.paragraphS,
                    )
                },
                confirmButton = {
                    TextButton(modifier = Modifier.testTag("BatchImportNoticeOk"), onClick = dismiss) {
                        Text(
                            text = stringResource(id = R.string.common_ok),
                            color = MaterialTheme.customColors.accentPrimary,
                        )
                    }
                },
            )
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
                val tutorialScreenState = viewModel.tutorialScreenState.observeAsState().value
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
                                if (tutorialScreenState?.isGoogleBackupAvailable == true) {
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
                                }
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
                                jp.co.soramitsu.ui_core.component.button.TextButton(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight()
                                        .testTag("RecoveryBackupFile"),
                                    size = Size.Small,
                                    order = Order.TERTIARY,
                                    text = stringResource(id = R.string.recovery_json_option),
                                    onClick = {
                                        viewModel.onRecoveryClicked(
                                            navController,
                                            3
                                        )
                                    },
                                )
                            }
                        },
                    )
                }

                tutorialScreenState?.let {
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
                            onRecoveryInputChanged = viewModel::onRecoveryInputChanged,
                            onPasswordChanged = viewModel::onRecoveryPasswordChanged,
                            onChooseFile = { backupFilePicker.launch(ANY_FILE_TYPE) },
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
}

/**
 * "Repair this account with its recovery phrase". States what happens before anything happens:
 * the stored keys of this one account are replaced by the ones the entered phrase derives, a copy
 * of the old keys stays on the phone, and nothing else changes. For a batch backup file the
 * accounts are listed. Confirmed with the PIN when one is set, or with the phone's screen lock
 * when the stored PIN cannot be read; Cancel leaves everything as it is.
 */
@Composable
private fun RepairAccountDialog(
    prompt: OnboardingViewModel.RepairPrompt,
    screenLockAvailable: Boolean,
    onConfirm: (String) -> Unit,
    onConfirmWithScreenLock: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Held in memory only: the typed PIN is never written to the saved instance state.
    var pin by remember { mutableStateOf("") }
    val batch = prompt.batchAccounts.isNotEmpty()
    AlertDialog(
        backgroundColor = MaterialTheme.customColors.bgPage,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    id = when {
                        batch -> R.string.account_repair_title_batch
                        prompt.fromPhrase -> R.string.account_repair_title
                        else -> R.string.account_repair_title_backup
                    },
                ),
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.textSBold,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.x1),
            ) {
                Text(
                    text = stringResource(
                        id = when {
                            batch -> R.string.account_repair_body_batch
                            prompt.accountExists -> R.string.account_repair_body_account
                            else -> R.string.account_repair_body_keys
                        },
                    ),
                    color = MaterialTheme.customColors.fgPrimary,
                    style = MaterialTheme.customTypography.paragraphS,
                )
                if (batch) {
                    Text(
                        modifier = Modifier.testTag("RepairBatchAccounts"),
                        text = prompt.batchAccounts.joinToString("\n"),
                        color = MaterialTheme.customColors.fgPrimary,
                        style = MaterialTheme.customTypography.textXS,
                    )
                }
                if (prompt.screenLockRequired) {
                    Text(
                        modifier = Modifier.testTag("RepairScreenLock"),
                        text = stringResource(
                            id = if (screenLockAvailable) {
                                R.string.account_repair_screen_lock_body
                            } else {
                                R.string.account_repair_screen_lock_missing
                            },
                        ),
                        color = MaterialTheme.customColors.fgPrimary,
                        style = MaterialTheme.customTypography.paragraphS,
                    )
                } else if (prompt.pinRequired) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth().testTag("RepairPin"),
                        value = pin,
                        onValueChange = { value -> pin = asciiPinDigits(value) },
                        enabled = !prompt.working,
                        singleLine = true,
                        label = { Text(text = stringResource(id = R.string.account_repair_pin_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = prompt.wrongPin,
                    )
                    if (prompt.wrongPin) {
                        Text(
                            text = stringResource(id = R.string.account_repair_pin_wrong),
                            color = MaterialTheme.customColors.statusError,
                            style = MaterialTheme.customTypography.textXS,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag("RepairConfirm"),
                enabled = !prompt.working && when {
                    prompt.screenLockRequired -> screenLockAvailable
                    prompt.pinRequired -> pin.isNotEmpty()
                    else -> true
                },
                onClick = {
                    if (prompt.screenLockRequired) {
                        onConfirmWithScreenLock()
                    } else {
                        val entered = pin
                        pin = ""
                        onConfirm(entered)
                    }
                },
            ) {
                Text(
                    text = stringResource(
                        id = when {
                            prompt.screenLockRequired -> R.string.account_repair_confirm_screen_lock
                            batch -> R.string.account_repair_confirm_batch
                            else -> R.string.account_repair_confirm
                        },
                    ),
                    color = MaterialTheme.customColors.accentPrimary,
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !prompt.working, onClick = onDismiss) {
                Text(
                    text = stringResource(id = R.string.common_cancel),
                    color = MaterialTheme.customColors.fgPrimary,
                )
            }
        },
    )
}

/** The longest PIN the field accepts (the app's PIN has 6 digits; older ones had 4). */
private const val MAX_PIN_DIGITS = 12

/**
 * The digits of [typed] as the ASCII digits the app's PIN is stored in. A keyboard set to
 * Arabic, Persian or Hindi types the same PIN in its own digits; those are the same numbers and
 * are converted, so a correct PIN is never refused for the script it was typed in. Everything
 * that is not a decimal digit is dropped.
 */
internal fun asciiPinDigits(typed: String): String = buildString {
    for (char in typed) {
        val digit = Character.digit(char, 10)
        if (digit in 0..9 && Character.isDigit(char)) append('0' + digit)
        if (length == MAX_PIN_DIGITS) break
    }
}
