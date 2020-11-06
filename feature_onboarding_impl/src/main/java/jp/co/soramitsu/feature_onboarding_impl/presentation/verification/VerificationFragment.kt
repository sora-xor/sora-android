package jp.co.soramitsu.feature_onboarding_impl.presentation.verification

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import com.google.android.gms.auth.api.phone.SmsRetriever
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.common.util.ext.disable
import jp.co.soramitsu.common.util.ext.enable
import jp.co.soramitsu.common.util.ext.unregisterReceiverIfNeeded
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import kotlinx.android.synthetic.main.fragment_verification.codeEt
import kotlinx.android.synthetic.main.fragment_verification.nextBtn
import kotlinx.android.synthetic.main.fragment_verification.requestTimeTv
import kotlinx.android.synthetic.main.fragment_verification.toolbar
import javax.inject.Inject

class VerificationFragment : BaseFragment<VerificationViewModel>() {

    @Inject lateinit var debounceClickHandler: DebounceClickHandler

    companion object {
        private const val KEY_BLOCKING_TIME = "blocking_time"
        private const val KEY_COUNTRY_ISO = "country_iso"

        fun newInstance(
            navController: NavController,
            countryIso: String,
            blockingTime: Int
        ) {
            val bundle = Bundle().apply {
                putInt(KEY_BLOCKING_TIME, blockingTime)
                putString(KEY_COUNTRY_ISO, countryIso)
            }
            navController.navigate(R.id.verificationFragment, bundle)
        }
    }

    private lateinit var progressDialog: SoraProgressDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_verification, container, false)
    }

    override fun onResume() {
        super.onResume()
        toolbar.setHomeButtonListener { viewModel.backPressed() }
    }

    @SuppressLint("CheckResult")
    override fun initViews() {
        progressDialog = SoraProgressDialog(activity!!)
        nextBtn.disable()
    }

    override fun subscribe(viewModel: VerificationViewModel) {
        initListeners()

        observe(viewModel.getProgressVisibility(), Observer {
            if (it) progressDialog.show() else progressDialog.dismiss()
        })

        observe(viewModel.timerLiveData, Observer {
            it?.let {
                requestTimeTv.setTextColor(ContextCompat.getColor(activity!!, R.color.grey))
                requestTimeTv.isEnabled = false
                requestTimeTv.text = getString(R.string.verification_request_new_code_time, it.first, it.second)
            }
        })

        observe(viewModel.timerFinishedLiveData, EventObserver {
            requestTimeTv.isEnabled = true
            requestTimeTv.setTextColor(ContextCompat.getColor(activity!!, R.color.uikit_lightRed))
            requestTimeTv.text = getString(R.string.verification_resend_code)
        })

        observe(viewModel.smsCodeStartActivityForResult, Observer {
            startActivityForResult(it, VerificationViewModel.SMS_CONSENT_REQUEST)
        })

        observe(viewModel.smsCodeAutofillLiveData, Observer {
            if (it.isNotEmpty()) codeEt.setText(it)
        })

        observe(viewModel.resetCodeLiveData, EventObserver {
            codeEt.setText("")
        })

        observe(viewModel.nextButtonEnableLiveData, Observer {
            if (it) {
                nextBtn.enable()
            } else {
                nextBtn.disable()
            }
        })

        viewModel.setCountryIso(arguments!!.getString(KEY_COUNTRY_ISO, ""))

        viewModel.setTimer(arguments!!.getInt(KEY_BLOCKING_TIME))

        setupSmsReceiver()
    }

    private fun initListeners() {
        requestTimeTv.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            viewModel.requestNewCode()
        })

        nextBtn.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            viewModel.onVerify(codeEt.text.toString())
        })

        codeEt.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.codeEntered(s.toString())
            }
        })

        codeEt.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_DONE -> {
                    viewModel.onVerify(codeEt.text.toString())
                }
            }
            false
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(context!!, OnboardingFeatureApi::class.java)
            .verificationComponentBuilder()
            .withFragment(this)
            .withRouter(activity as OnboardingRouter)
            .build()
            .inject(this)
    }

    private fun setupSmsReceiver() {
        SmsRetriever.getClient(activity!!)
            .startSmsUserConsent(null)
            .addOnSuccessListener {
                val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
                activity?.registerReceiver(smsVerificationReceiver, intentFilter)
            }
    }

    private val smsVerificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            viewModel.onSmsReceive(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        viewModel.onActivityResult(requestCode, resultCode, data)
    }

    override fun onPause() {
        super.onPause()
        activity?.unregisterReceiverIfNeeded(smsVerificationReceiver)
    }
}