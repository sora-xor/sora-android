/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.version

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.navigation.NavController
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.databinding.FragmentUnsupportedVersionBinding
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import javax.inject.Inject

class UnsupportedVersionFragment :
    BaseFragment<UnsupportedVersionViewModel>(R.layout.fragment_unsupported_version) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    companion object {
        private const val KEY_APP_URL = "app_url"

        @JvmStatic
        fun newInstance(appUrl: String, navController: NavController) {
            val bundle = Bundle().apply {
                putString(KEY_APP_URL, appUrl)
            }
            navController.navigate(R.id.unsupportedVersionFragment, bundle)
        }
    }

    private val viewBinding by viewBinding(FragmentUnsupportedVersionBinding::bind)

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(
            requireContext(),
            OnboardingFeatureApi::class.java
        )
            .unsupportedVersionComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val appUrl = requireArguments().getString(KEY_APP_URL, "")
        viewBinding.googlePlayBtn.setDebouncedClickListener(debounceClickHandler) {
            openGooglePlay(appUrl)
        }
    }

    private fun openGooglePlay(appUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(appUrl)
        }
        startActivity(intent)
    }
}
