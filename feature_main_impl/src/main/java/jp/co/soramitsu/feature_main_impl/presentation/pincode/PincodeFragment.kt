/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode

import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
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
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.pincode.fingerprint.FingerprintWrapper
import kotlinx.android.synthetic.main.fragment_pincode.dotsProgressView
import kotlinx.android.synthetic.main.fragment_pincode.pinCodeTitleTv
import kotlinx.android.synthetic.main.fragment_pincode.pinCodeView
import kotlinx.android.synthetic.main.fragment_pincode.toolbar

class PincodeFragment : BaseFragment<PinCodeViewModel>() {

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
            .withMaxPinCodeLength(DotsProgressView.MAX_PROGRESS)
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

        with(pinCodeView) {
            pinCodeListener = { viewModel.pinCodeNumberClicked(it) }
            deleteClickListener = { viewModel.pinCodeDeleteClicked() }
            fingerprintClickListener = { fingerprintWrapper.toggleScanner() }
        }
    }

    override fun subscribe(viewModel: PinCodeViewModel) {
        fingerprintWrapper = FingerprintWrapper(
            FingerprintManagerCompat.from(activity!!),
            activity!!.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager,
            viewModel
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

        observe(viewModel.startFingerprintScannerEventLiveData, EventObserver {
            fingerprintWrapper.startAuth()
        })

        observe(viewModel.showFingerPrintEventLiveData, EventObserver {
            pinCodeView.changeFingerPrintButtonVisibility(fingerprintWrapper.isSensorReady())
        })

        observe(viewModel.toolbarTitleResLiveData, Observer {
            pinCodeTitleTv.setText(it)
        })

        observe(viewModel.wrongPinCodeEventLiveData, EventObserver {
            Toast.makeText(activity, getString(R.string.pincode_check_error), Toast.LENGTH_LONG).show()
        })

        observe(viewModel.fingerPrintDialogVisibilityLiveData, Observer {
            if (it) fingerprintDialog.show() else fingerprintDialog.dismiss()
        })

        observe(viewModel.fingerPrintAutFailedLiveData, EventObserver {
            Toast.makeText(context, R.string.fingerprint_error, Toast.LENGTH_SHORT).show()
        })

        observe(viewModel.fingerPrintErrorLiveData, EventObserver {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        })

        observe(viewModel.pinCodeProgressLiveData, Observer {
            dotsProgressView.setProgress(it)
        })

        observe(viewModel.deleteButtonVisibilityLiveData, Observer {
            pinCodeView.changeDeleteButtonVisibility(it)
        })

        val action = arguments!!.getSerializable(Const.PIN_CODE_ACTION) as PinCodeAction
        viewModel.startAuth(action)
    }

    fun onBackPressed() {
        viewModel.backPressed()
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item!!.itemId == android.R.id.home) {
            viewModel.backPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
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