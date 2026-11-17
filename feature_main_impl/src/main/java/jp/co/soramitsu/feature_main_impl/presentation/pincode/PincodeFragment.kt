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

package jp.co.soramitsu.feature_main_impl.presentation.pincode

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.ScrollState
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.androidfoundation.intent.restartApplication
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.io.MainThreadExecutor
import jp.co.soramitsu.common.presentation.args.addresses
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.util.ext.runDelayed
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.feature_main_impl.presentation.pincode.fingerprint.FingerprintCallback
import jp.co.soramitsu.feature_main_impl.presentation.pincode.fingerprint.FingerprintWrapper
import jp.co.soramitsu.feature_main_impl.presentation.util.action

@AndroidEntryPoint
class PincodeFragment : SoraBaseFragment<PinCodeViewModel>() {

    companion object {
        private const val DATA_CLEAR_DELAY = 500L
    }

    /** Keyguard's screen-lock confirmation, on Android 8 to 10 ([ScreenLockPrompt]). */
    private val keyguardConfirmation =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.onScreenLockConfirmed(ScreenLockPrompt.KEYGUARD_CONFIRMED)
            }
        }

    private val fingerprintWrapper: FingerprintWrapper by lazy {
        val biometricManager = BiometricManager.from(context?.applicationContext!!)
        val biometricPrompt =
            BiometricPrompt(this, MainThreadExecutor(), FingerprintCallback(viewModel))
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_dialog_title))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(getString(R.string.common_cancel))
            .build()
        FingerprintWrapper(
            biometricManager,
            biometricPrompt,
            promptInfo,
            viewModel::prepareBiometricUnlock,
            viewModel::cancelBiometricUnlock,
        )
    }

    override val viewModel: PinCodeViewModel by viewModels()

    private lateinit var fingerprintDialog: BottomSheetDialog
    private lateinit var progressDialog: SoraProgressDialog
    private var enrollmentChangeDialog: AlertDialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        progressDialog = SoraProgressDialog(requireContext())

        fingerprintDialog = BottomSheetDialog(requireContext()).apply {
            setContentView(R.layout.fingerprint_bottom_dialog)
            setCancelable(true)
            setOnCancelListener { fingerprintWrapper.cancel() }
            findViewById<TextView>(R.id.btnCancel)?.setOnClickListener { fingerprintWrapper.cancel() }
        }

        viewModel.setBiometryAvailable(fingerprintWrapper.isAuthReady())

        initListeners()

        val action = requireArguments().action

        if (action == PinCodeAction.LOGOUT || action == PinCodeAction.OPEN_SEED || action == PinCodeAction.OPEN_JSON || action == PinCodeAction.OPEN_PASSPHRASE) {
            viewModel.addresses = requireArguments().addresses
        }

        viewModel.startAuth(action)
    }

    private fun initListeners() {
        viewModel.getProgressVisibility().observe {
            if (it) progressDialog.show() else progressDialog.dismiss()
        }
        viewModel.pincodeChangedEvent.observe {
            Toast.makeText(requireContext(), R.string.pincode_change_success, Toast.LENGTH_LONG)
                .show()
        }
        viewModel.biometryInitialDialogEvent.observe {
            AlertDialog.Builder(requireActivity())
                .setTitle(R.string.biometric_dialog_title)
                .setMessage(R.string.ask_biometry_message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.biometryDialogYesClicked() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> viewModel.biometryDialogNoClicked() }
                .show()
        }
        viewModel.enrollmentChangeQuestion.observe { asked ->
            enrollmentChangeDialog?.dismiss()
            enrollmentChangeDialog = if (asked) {
                AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.biometric_enrollment_changed_title)
                    .setMessage(R.string.biometric_enrollment_changed_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.biometric_enrollment_changed_keep) { _, _ -> viewModel.onEnrollmentChangeKept() }
                    .setNegativeButton(R.string.biometric_enrollment_changed_turn_off) { _, _ -> viewModel.onEnrollmentChangeRefused() }
                    .show()
            } else {
                null
            }
        }
        viewModel.screenLockPromptEvent.observe { confirmScreenLock() }
        viewModel.resetApplicationEvent.observe {
            runDelayed(DATA_CLEAR_DELAY) {
                requireContext().restartApplication()
            }
        }

        viewModel.switchAccountEvent.observe {
            runDelayed(DATA_CLEAR_DELAY) {
                viewModel.popBackToAccountList()
            }
        }

        viewModel.startFingerprintScannerEventLiveData.observe {
            if (fingerprintWrapper.isAuthReady() && it) {
                fingerprintWrapper.startAuth()
            }
        }

        viewModel.fingerPrintCanceledFromPromptEvent.observe {
            fingerprintWrapper.cancel()
        }

        viewModel.showFingerPrintEventLiveData.observe {
            if (!it) {
                fingerprintWrapper.cancel()
            }
            viewModel.changeFingerPrintButtonVisibility(fingerprintWrapper.isAuthReady() && it)
        }
        viewModel.fingerPrintDialogVisibilityLiveData.observe {
            if (it) fingerprintDialog.show() else fingerprintDialog.dismiss()
        }
        viewModel.fingerPrintErrorLiveData.observe {
            Toast.makeText(activity, it, Toast.LENGTH_LONG).show()
        }
        viewModel.closeAppLiveData.observe {
            (activity as? MainActivity)?.closeApp()
        }
    }

    /**
     * The phone's own screen lock (its PIN, pattern or password, never a biometric) in the
     * system prompt, before a new PIN replaces one that cannot be read ([ScreenLockPrompt]).
     * Built for each request: androidx keeps one callback per activity, and this fragment's
     * fingerprint prompt set its own. A prompt that cannot be shown changes nothing: touching
     * the keypad asks again.
     */
    private fun confirmScreenLock() {
        val title = getString(R.string.pincode_set_your_pin_code)
        val description = getString(R.string.account_repair_screen_lock_body)
        if (!ScreenLockPrompt.usesBiometricPrompt()) {
            val request = ScreenLockPrompt.keyguardIntent(requireContext(), title, description) ?: return
            runCatching { keyguardConfirmation.launch(request) }
            return
        }
        val prompt = BiometricPrompt(
            this,
            MainThreadExecutor(),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.onScreenLockConfirmed(result.authenticationType)
                }
            },
        )
        runCatching { prompt.authenticate(ScreenLockPrompt.promptInfo(title, description)) }
    }

    override fun onPause() {
        super.onPause()
        fingerprintWrapper.cancel()
    }

    override fun onStop() {
        super.onStop()
        // Leaving the screen veils a phrase or seed that the removal screen was showing.
        viewModel.onRemovalHidden()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun onDestroyView() {
        // The question is asked again when the view comes back.
        enrollmentChangeDialog?.dismiss()
        enrollmentChangeDialog = null
        super.onDestroyView()
    }

    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            // Only the keypad and dots are pinned left-to-right (inside PincodeScreen); the
            // Safe Mode panels on this screen follow the device's layout direction.
            val removal = viewModel.removal
            if (removal != null) {
                // "Forget account", after the PIN: the backup is shown before anything is
                // removed. The window is secure while a phrase or seed can be on screen.
                SecureWindow(enabled = removal.showsBackup)
                AccountRemovalScreen(
                    state = removal,
                    onReveal = viewModel::onRemovalRevealed,
                    onWrittenDown = viewModel::onRemovalWrittenDown,
                    onUnderstoodNoBackup = viewModel::onRemovalUnderstoodNoBackup,
                    onUnderstoodFoundWallets = viewModel::onRemovalUnderstoodFoundWallets,
                    onExportJson = viewModel::onRemovalExportJson,
                    onRemove = viewModel::onRemovalConfirmed,
                    onCancel = viewModel::onRemovalCancelled,
                )
                return@composable
            }
            PincodeScreen(
                pinCodeScreenState = viewModel.state,
                onNumClick = viewModel::pinCodeNumberClicked,
                onBiometricClick = fingerprintWrapper::toggleScanner,
                onDeleteClick = viewModel::pinCodeDeleteClicked,
                onWrongPinAnimationEnd = viewModel::onWrongPinAnimationEnd,
                onNewPinContinue = viewModel::setNewLengthPinCodeClicked,
            )
        }
    }
}
