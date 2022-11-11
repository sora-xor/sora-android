/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.io.MainThreadExecutor
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.util.ext.onBackPressed
import jp.co.soramitsu.common.util.ext.restartApplication
import jp.co.soramitsu.common.util.ext.runDelayed
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentPincodeBinding
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.feature_main_impl.presentation.pincode.fingerprint.FingerprintCallback
import jp.co.soramitsu.feature_main_impl.presentation.pincode.fingerprint.FingerprintWrapper
import jp.co.soramitsu.feature_main_impl.presentation.util.action
import jp.co.soramitsu.feature_main_impl.presentation.util.addresses
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController

@AndroidEntryPoint
class PincodeFragment : BaseFragment<PinCodeViewModel>(R.layout.fragment_pincode) {

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
    private val binding by viewBinding(FragmentPincodeBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        onBackPressed {
            viewModel.backPressed()
        }

        progressDialog = SoraProgressDialog(requireContext())

        fingerprintDialog = BottomSheetDialog(requireContext()).apply {
            setContentView(R.layout.fingerprint_bottom_dialog)
            setCancelable(true)
            setOnCancelListener { fingerprintWrapper.cancel() }
            findViewById<TextView>(R.id.btnCancel)?.setOnClickListener { fingerprintWrapper.cancel() }
        }

        with(binding.pinCodeView) {
            pinCodeListener =
                { viewModel.pinCodeNumberClicked(it, binding.dotsProgressView.maxProgress) }
            deleteClickListener = { viewModel.pinCodeDeleteClicked() }
            fingerprintClickListener = { fingerprintWrapper.toggleScanner() }
        }

        viewModel.setBiometryAvailable(fingerprintWrapper.isAuthReady())

        initListeners()

        val action = if (viewModel.isReturningFromInfo) {
            PinCodeAction.CREATE_PIN_CODE
        } else {
            requireArguments().action
        }

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
                .setNegativeButton(R.string.common_cancel) { _, _ -> viewModel.backPressed() }
                .show()
        }
        viewModel.biometryInitialDialogEvent.observe {
            AlertDialog.Builder(requireActivity())
                .setTitle(R.string.biometric_dialog_title)
                .setMessage(R.string.ask_biometry_message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.yes) { _, _ -> viewModel.biometryDialogYesClicked() }
                .setNegativeButton(android.R.string.no) { _, _ -> viewModel.biometryDialogNoClicked() }
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
            binding.pinCodeView.changeFingerPrintButtonVisibility(fingerprintWrapper.isAuthReady() && it)
        }
        viewModel.toolbarTitleResLiveData.observe {
            binding.pinCodeTitleTv.text = it
        }
        viewModel.wrongPinCodeEventLiveData.observe {
            playMatchingMnemonicErrorAnimation()
        }
        viewModel.fingerPrintDialogVisibilityLiveData.observe {
            if (it) fingerprintDialog.show() else fingerprintDialog.dismiss()
        }
        viewModel.fingerPrintAutFailedLiveData.observe {
            playMatchingMnemonicErrorAnimation()
        }
        viewModel.fingerPrintErrorLiveData.observe {
            Toast.makeText(activity, it, Toast.LENGTH_LONG).show()
        }
        viewModel.pinCodeProgressLiveData.observe {
            binding.dotsProgressView.setProgress(it)
        }
        viewModel.deleteButtonVisibilityLiveData.observe {
            binding.pinCodeView.changeDeleteButtonVisibility(it)
        }
        viewModel.closeAppLiveData.observe {
            (activity as? MainActivity)?.closeApp()
        }
        viewModel.checkInviteLiveData.observe {
            (activity as? MainActivity)?.checkInviteAction()
        }
        viewModel.currentPincodeLength.observe {
            binding.dotsProgressView.maxProgress = it
        }
        viewModel.showTriesLeftSnackbar.observe {
            Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
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

    private fun playMatchingMnemonicErrorAnimation() {
        val animation = AnimationUtils.loadAnimation(requireActivity(), R.anim.shake)
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationRepeat(animation: Animation?) {
            }

            override fun onAnimationStart(animation: Animation?) {
            }

            override fun onAnimationEnd(animation: Animation?) {
            }
        })
        binding.dotsProgressView.startAnimation(animation)
    }
}
