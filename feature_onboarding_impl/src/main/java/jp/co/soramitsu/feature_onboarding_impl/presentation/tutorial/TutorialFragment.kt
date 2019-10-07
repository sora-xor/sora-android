/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.tutorial

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.SoraProgressDialog
import jp.co.soramitsu.common.util.SoraClickableSpan
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import kotlinx.android.synthetic.main.fragment_tutorial.indicator
import kotlinx.android.synthetic.main.fragment_tutorial.tutorialRestoreTextView
import kotlinx.android.synthetic.main.fragment_tutorial.tutorialSignUpButton
import kotlinx.android.synthetic.main.fragment_tutorial.tutorialTerms
import kotlinx.android.synthetic.main.fragment_tutorial.viewPager

@SuppressLint("CheckResult")
class TutorialFragment : BaseFragment<TutorialViewModel>() {

    private lateinit var progressDialog: SoraProgressDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_tutorial, container, false)
    }

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(context!!, OnboardingFeatureApi::class.java)
            .tutorialComponentBuilder()
            .withFragment(this)
            .withRouter(activity as OnboardingRouter)
            .build()
            .inject(this)
    }

    override fun initViews() {
        viewPager.adapter = SliderAdapter()
        indicator.setupWithViewPager(viewPager, true)

        val termsContent = SpannableString(getString(R.string.tutorial_terms_2))
        termsContent.setSpan(
            SoraClickableSpan { viewModel.showTermsScreen() },
            0,
            termsContent.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val privacyContent = SpannableString(getString(R.string.tutorial_terms_3))
        privacyContent.setSpan(
            SoraClickableSpan { viewModel.showPrivacyScreen() },
            0,
            privacyContent.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val bottomText = getString(R.string.tutorial_terms) + " "
        tutorialTerms.text = bottomText
        tutorialTerms.append(termsContent)
        tutorialTerms.append(" " + getString(R.string.and) + " ")
        tutorialTerms.append(privacyContent)
        tutorialTerms.movementMethod = LinkMovementMethod.getInstance()
        tutorialTerms.highlightColor = Color.TRANSPARENT

        progressDialog = SoraProgressDialog(activity!!)

        tutorialSignUpButton.setOnClickListener {
            viewModel.onSignUpClicked()
        }
        tutorialRestoreTextView.setOnClickListener { viewModel.onRecoveryClicked() }
    }

    override fun subscribe(viewModel: TutorialViewModel) {
        observe(viewModel.getProgressVisibility(), Observer {
            if (it) progressDialog.show() else progressDialog.dismiss()
        })
    }
}