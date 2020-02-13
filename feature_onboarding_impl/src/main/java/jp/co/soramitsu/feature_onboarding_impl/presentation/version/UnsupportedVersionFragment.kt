/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.version

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.NavController
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import kotlinx.android.synthetic.main.fragment_unsupported_version.googlePlayBtn
import javax.inject.Inject

class UnsupportedVersionFragment : jp.co.soramitsu.common.base.BaseFragment<UnsupportedVersionViewModel>() {

    @Inject lateinit var debounceClickHandler: DebounceClickHandler

    companion object {
        private const val KEY_APP_URL = "app_url"

        @JvmStatic fun newInstance(appUrl: String, navController: NavController) {
            val bundle = Bundle().apply {
                putString(KEY_APP_URL, appUrl)
            }
            navController.navigate(R.id.unsupportedVersionFragment, bundle)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_unsupported_version, container, false)
    }

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(context!!, OnboardingFeatureApi::class.java)
            .unsupportedVersionComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    override fun initViews() {
        val appUrl = arguments!!.getString(KEY_APP_URL, "")
        googlePlayBtn.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            openGooglePlay(appUrl)
        })
    }

    override fun subscribe(viewModel: UnsupportedVersionViewModel) {
    }

    private fun openGooglePlay(appUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(appUrl)
        }
        startActivity(intent)
    }
}