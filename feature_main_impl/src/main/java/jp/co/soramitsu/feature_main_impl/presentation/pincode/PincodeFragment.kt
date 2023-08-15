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

package jp.co.soramitsu.feature_main_impl.presentation.pincode

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.composable
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.io.MainThreadExecutor
import jp.co.soramitsu.common.presentation.args.addresses
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.util.ext.restartApplication
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

    private val fingerprintWrapper: FingerprintWrapper by lazy {
        val biometricManager = BiometricManager.from(context?.applicationContext!!)
        val biometricPrompt =
            BiometricPrompt(this, MainThreadExecutor(), FingerprintCallback(viewModel))
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_dialog_title))
            .setNegativeButtonText(getString(R.string.common_cancel))
            .build()
        FingerprintWrapper(
            biometricManager,
            biometricPrompt,
            promptInfo
        )
    }

    override val viewModel: PinCodeViewModel by viewModels()

    private lateinit var fingerprintDialog: BottomSheetDialog
    private lateinit var progressDialog: SoraProgressDialog

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
        viewModel.logoutEvent.observe { message ->
            AlertDialog.Builder(requireActivity())
                .setTitle(R.string.profile_logout_title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.profile_logout_title) { _, _ -> viewModel.logoutOkPressed() }
                .setNegativeButton(R.string.common_cancel) { _, _ -> viewModel.onBackPressed() }
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
        viewModel.checkInviteLiveData.observe {
            (activity as? MainActivity)?.checkInviteAction()
        }

        viewModel.pincodeLengthInfoAlertLiveData.observe {
            AlertDialog.Builder(requireActivity())
                .setTitle(R.string.pincode_length_info_title)
                .setMessage(R.string.pincode_length_info_message)
                .setPositiveButton(R.string.pincode_length_info_button_text) { _, _ ->
                    viewModel.setNewLengthPinCodeClicked()
                }
                .setCancelable(false)
                .show()
        }
    }

    override fun onPause() {
        super.onPause()
        fingerprintWrapper.cancel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                PincodeScreen(
                    pinCodeScreenState = viewModel.state,
                    onNumClick = viewModel::pinCodeNumberClicked,
                    onBiometricClick = fingerprintWrapper::toggleScanner,
                    onDeleteClick = viewModel::pinCodeDeleteClicked,
                    onWrongPinAnimationEnd = viewModel::onWrongPinAnimationEnd,
                    onSwitchNode = viewModel::onSwitchNode,
                )
            }
        }
    }
}
