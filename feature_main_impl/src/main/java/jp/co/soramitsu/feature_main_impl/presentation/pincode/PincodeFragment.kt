/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode

import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.hardware.fingerprint.FingerprintManagerCompat
import androidx.lifecycle.Observer
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.SoraProgressDialog
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.pincode.custom.PinLockListener
import jp.co.soramitsu.feature_main_impl.presentation.pincode.fingerprint.FingerPrintListener
import jp.co.soramitsu.feature_main_impl.presentation.pincode.fingerprint.FingerprintWrapper
import kotlinx.android.synthetic.main.fragment_pincode.indicator_dots
import kotlinx.android.synthetic.main.fragment_pincode.pin_code_title
import kotlinx.android.synthetic.main.fragment_pincode.pin_lock_view
import kotlinx.android.synthetic.main.fragment_pincode.toolbar

class PincodeFragment : BaseFragment<PinCodeViewModel>() {

    companion object {
        private const val WAIT_FOR_DOTS_ANIMATION_TIME: Long = 12
    }

    private lateinit var fingerprintWrapper: FingerprintWrapper
    private lateinit var fingerprintDialog: BottomSheetDialog
    private lateinit var progressDialog: SoraProgressDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_pincode, container, false)
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .pinCodeComponentBuilder()
            .withFragment(this)
            .withRouter(activity as MainRouter)
            .build()
            .inject(this)
    }

    override fun initViews() {
        progressDialog = SoraProgressDialog(activity!!)

        fingerprintDialog = BottomSheetDialog(activity!!).apply {
            setContentView(R.layout.fingerprint_bottom_dialog)
            setCancelable(true)
            setOnCancelListener { fingerprintWrapper.cancel() }
            findViewById<TextView>(R.id.btnCancel)?.setOnClickListener { fingerprintWrapper.cancel() }
        }

        toolbar.setHomeButtonListener { viewModel.backPressed() }
        toolbar.setTitle(getString(R.string.pincode))
    }

    override fun subscribe(viewModel: PinCodeViewModel) {
        fingerprintWrapper = FingerprintWrapper(
            FingerprintManagerCompat.from(activity!!),
            activity!!.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager,
            fingerprintListener
        )

        observe(viewModel.getProgressVisibility(), Observer {
            if (it) progressDialog.show() else progressDialog.dismiss()
        })

        observe(viewModel.backButtonVisibilityLiveData, Observer {
            if (it) {
                toolbar.showHomeButton()
            } else {
                toolbar.hideHomeButton()
            }
        })

        observe(viewModel.startFingerprintScannerEventLiveData, Observer {
            fingerprintWrapper.startAuth()
        })

        observe(viewModel.setPinCodeEventLiveData, Observer {
            showPinCodeSet()
        })

        observe(viewModel.checkPinCodeEventLiveData, Observer {
            showPinCodeCheck()
        })

        observe(viewModel.repeatPinCodeEventLiveData, Observer {
            showRepeatPinCode()
        })

        observe(viewModel.resetPinCodeEventLiveData, Observer {
            resetPinCodeView()
        })

        observe(viewModel.wrongPinCodeEventLiveData, Observer {
            pinCodeCheckError()
        })

        val action = arguments!!.getSerializable(Const.PIN_CODE_ACTION) as PinCodeAction
        viewModel.onActivityCreated(action)
    }

    private fun showPinCodeSet() {
        resetPinCodeView()
        setupPinLockView()
        pin_lock_view.isFingerprintButtonNeeded = false
    }

    private val fingerprintListener = object : FingerPrintListener {

        override fun onFingerPrintSuccess() {
            viewModel.fingerprintSuccess()
        }

        override fun onAuthFailed() {
            Toast.makeText(context, R.string.fingerprint_error, Toast.LENGTH_SHORT).show()
        }

        override fun onAuthenticationHelp(message: String) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        override fun onAuthenticationError(message: String) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        override fun showFingerPrintDialog() {
            fingerprintDialog.show()
        }

        override fun hideFingerPrintDialog() {
            fingerprintDialog.dismiss()
        }
    }

    private fun setupPinLockView() {
        pin_lock_view.attachIndicatorDots(indicator_dots)

        pin_lock_view.setPinLockListener(object : PinLockListener {
            override fun onFingerprintButtonClicked() {
                fingerprintWrapper.toggleScanner()
            }

            override fun onComplete(pin: String) {
                Handler().postDelayed({ viewModel.pinCodeEntered(pin) }, WAIT_FOR_DOTS_ANIMATION_TIME)
            }

            override fun onEmpty() {}

            override fun onPinChange(pinLength: Int, intermediatePin: String) {}
        })
    }

    private fun resetPinCodeView() {
        pin_lock_view.resetPinLockView()
        pin_code_title.setText(R.string.pincode_title)
        toolbar.hideHomeButton()
    }

    private fun pinCodeCheckError() {
        Toast.makeText(activity, getString(R.string.pincode_check_error), Toast.LENGTH_LONG).show()
        pin_lock_view.resetPinLockView()
    }

    fun onBackPressed() {
        viewModel.backPressed()
    }

    private fun showPinCodeCheck() {
        pin_code_title.setText(R.string.pincode_title_check)
        setupPinLockView()
        pin_lock_view.isFingerprintButtonNeeded = fingerprintWrapper.isSensorReady()
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item!!.itemId == android.R.id.home) {
            resetPinCodeView()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showRepeatPinCode() {
        pin_code_title.setText(R.string.pincode_title2)
        pin_lock_view.resetPinLockView()
    }

    override fun onPause() {
        super.onPause()
        fingerprintWrapper.cancel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }
}