/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.phone

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.util.ext.disable
import jp.co.soramitsu.common.util.ext.enable
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingActivity
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import kotlinx.android.synthetic.main.fragment_phone_number.descriptionText
import kotlinx.android.synthetic.main.fragment_phone_number.nextBtn
import kotlinx.android.synthetic.main.fragment_phone_number.phoneCodeEt
import kotlinx.android.synthetic.main.fragment_phone_number.phoneEt
import kotlinx.android.synthetic.main.fragment_phone_number.toolbar
import javax.inject.Inject

@SuppressLint("CheckResult")
class PhoneNumberFragment : BaseFragment<PhoneNumberViewModel>() {

    @Inject lateinit var debounceClickHandler: DebounceClickHandler

    companion object {
        private const val KEY_COUNTRY_ISO = "country_iso"
        private const val KEY_PHONE_CODE = "phone_code"

        private const val MAX_PHONE_LENGTH = 15

        @JvmStatic fun newInstance(navController: NavController, countryIso: String, phoneCode: String) {
            val bundle = Bundle().apply {
                putString(KEY_COUNTRY_ISO, countryIso)
                putString(KEY_PHONE_CODE, phoneCode)
            }
            navController.navigate(R.id.phoneNumberFragment, bundle)
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(context!!, OnboardingFeatureApi::class.java)
            .phoneNumberComponentBuilder()
            .withFragment(this)
            .withRouter(activity as OnboardingRouter)
            .build()
            .inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_phone_number, container, false)
    }

    override fun initViews() {
        nextBtn.disable()

        (activity as OnboardingActivity).window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        toolbar.setHomeButtonListener { viewModel.backButtonClick() }

        nextBtn.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            viewModel.onPhoneEntered(phoneCodeEt.text.toString(), phoneEt.text.toString())
        })

        descriptionText.text = getString(R.string.phone_number_sms_code_will_be_sent)

        val phoneCode = arguments!!.getString(KEY_PHONE_CODE)
        phoneCodeEt.setText(phoneCode)
        phoneEt.filters = arrayOf(InputFilter.LengthFilter(MAX_PHONE_LENGTH - phoneCode.length))

        viewModel.setCountryIso(arguments!!.getString(KEY_COUNTRY_ISO, ""))

        val textWatcher = object : TextWatcher {

            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.onPhoneChanged(s!!)
            }
        }

        phoneEt.addTextChangedListener(textWatcher)
    }

    override fun subscribe(viewModel: PhoneNumberViewModel) {
        observe(viewModel.nextButtonEnableLiveData, Observer {
            if (it) {
                nextBtn.enable()
            } else {
                nextBtn.disable()
            }
        })
    }
}