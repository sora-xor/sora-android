/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.mnemonic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.util.ScreenshotBlockHelper
import jp.co.soramitsu.common.util.ShareUtil
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import kotlinx.android.synthetic.main.fragment_mnemonic.btnShare
import kotlinx.android.synthetic.main.fragment_mnemonic.nextBtn
import kotlinx.android.synthetic.main.fragment_mnemonic.passphraseTv
import kotlinx.android.synthetic.main.fragment_mnemonic.preloaderView
import kotlinx.android.synthetic.main.fragment_mnemonic.toolbar
import javax.inject.Inject

class MnemonicFragment : BaseFragment<MnemonicViewModel>() {

    @Inject lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var screenshotBlockHelper: ScreenshotBlockHelper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_mnemonic, container, false)
    }

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(context!!, OnboardingFeatureApi::class.java)
            .mnemonicComponentBuilder()
            .withFragment(this)
            .withRouter(activity as OnboardingRouter)
            .build()
            .inject(this)
    }

    override fun initViews() {
        toolbar.hideHomeButton()

        nextBtn.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            viewModel.btnNextClicked()
        })

        btnShare.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            ShareUtil.openShareDialog(
                (activity as AppCompatActivity?)!!, getString(R.string.common_passphrase_save_mnemonic_title),
                passphraseTv.text.toString()
            )
        })

        screenshotBlockHelper = ScreenshotBlockHelper(activity!!)
    }

    override fun subscribe(viewModel: MnemonicViewModel) {
        observe(viewModel.mnemonicLiveData, Observer {
            passphraseTv.text = it
        })

        observe(viewModel.getPreloadVisibility(), Observer {
            if (it) preloaderView.show() else preloaderView.gone()
        })

        viewModel.getPassphrase()
    }

    override fun onResume() {
        super.onResume()
        screenshotBlockHelper.disableScreenshoting()
    }

    override fun onPause() {
        super.onPause()
        screenshotBlockHelper.enableScreenshoting()
    }
}