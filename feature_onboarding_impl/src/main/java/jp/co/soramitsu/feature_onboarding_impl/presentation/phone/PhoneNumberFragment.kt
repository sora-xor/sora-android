/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.phone

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.NavController
import com.jakewharton.rxbinding2.widget.RxTextView
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.util.ext.disable
import jp.co.soramitsu.common.util.ext.enable
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingActivity
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import kotlinx.android.synthetic.main.fragment_phone_number.phoneCodeEt
import kotlinx.android.synthetic.main.fragment_phone_number.phoneEt
import kotlinx.android.synthetic.main.fragment_phone_number.sided_button
import kotlinx.android.synthetic.main.fragment_phone_number.toolbar

@SuppressLint("CheckResult")
class PhoneNumberFragment : BaseFragment<PhoneNumberViewModel>() {

    private lateinit var primaryButton: Button

    companion object {
        private const val KEY_COUNTRY_ISO = "country_iso"
        private const val KEY_PHONE_CODE = "phone_code"

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
        (activity as OnboardingActivity).window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        toolbar.setTitle(getString(R.string.fragment_phone_number_title))
        toolbar.setHomeButtonListener { viewModel.backButtonClick() }

        primaryButton = sided_button.findViewById(R.id.left_btn)
        val sidedButtonDescription = sided_button.findViewById<TextView>(R.id.description_text)
        sided_button.findViewById<ImageView>(R.id.description_image).visibility = View.GONE
        sided_button.setBackgroundColor(resources.getColor(R.color.greyBackground))

        primaryButton.setOnClickListener { viewModel.onPhoneEntered(phoneCodeEt.text.toString(), phoneEt.text.toString()) }
        sidedButtonDescription.text = getString(R.string.phone_number_sms_code_will_be_sent)

        val phoneCode = arguments!!.getString(KEY_PHONE_CODE)
        phoneCodeEt.setText(phoneCode)

        viewModel.setCountryIso(arguments!!.getString(KEY_COUNTRY_ISO, ""))

        RxTextView.textChanges(phoneEt)
            .subscribe {
                if (it.isEmpty()) {
                    primaryButton.disable()
                } else {
                    primaryButton.enable()
                }
            }
    }

    override fun subscribe(viewModel: PhoneNumberViewModel) {
    }
}