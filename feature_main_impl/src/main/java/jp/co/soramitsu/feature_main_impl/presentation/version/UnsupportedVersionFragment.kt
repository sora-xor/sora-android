/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.version

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentUnsupportedVersionBinding
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import javax.inject.Inject

class UnsupportedVersionFragment :
    BaseFragment<UnsupportedVersionViewModel>(R.layout.fragment_unsupported_version) {

    companion object {
        private const val KEY_APP_URL = "app_url"

        fun createBundle(appUrl: String): Bundle {
            return Bundle().apply { putString(KEY_APP_URL, appUrl) }
        }
    }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler
    private val binding by viewBinding(FragmentUnsupportedVersionBinding::bind)

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(requireContext(), MainFeatureApi::class.java)
            .unsupportedVersionComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        val appUrl = requireArguments().getString(KEY_APP_URL, "")
        binding.googlePlayBtn.setDebouncedClickListener(debounceClickHandler) {
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
