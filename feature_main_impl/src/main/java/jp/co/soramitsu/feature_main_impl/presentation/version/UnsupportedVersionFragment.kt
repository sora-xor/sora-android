/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.version

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import kotlinx.android.synthetic.main.fragment_unsupported_version.googlePlayBtn
import javax.inject.Inject

class UnsupportedVersionFragment : BaseFragment<UnsupportedVersionViewModel>() {

    companion object {
        private const val KEY_APP_URL = "app_url"

        fun createBundle(appUrl: String): Bundle {
            return Bundle().apply { putString(KEY_APP_URL, appUrl) }
        }
    }

    @Inject lateinit var debounceClickHandler: DebounceClickHandler

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_unsupported_version, container, false)
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .unsupportedVersionComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    override fun initViews() {
        (activity as BottomBarController).hideBottomBar()

        val appUrl = arguments!!.getString(KEY_APP_URL, "")
        googlePlayBtn.setOnClickListener(
            DebounceClickListener(debounceClickHandler) {
                openGooglePlay(appUrl)
            }
        )
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