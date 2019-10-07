/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.mnemonic

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.jakewharton.rxbinding2.view.RxView
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.util.ShareUtil
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import kotlinx.android.synthetic.main.fragment_mnemonic.btnShare
import kotlinx.android.synthetic.main.fragment_mnemonic.nextBtn
import kotlinx.android.synthetic.main.fragment_mnemonic.passphraseTv
import kotlinx.android.synthetic.main.fragment_mnemonic.preloaderView
import kotlinx.android.synthetic.main.fragment_mnemonic.toolbar

@SuppressLint("CheckResult")
class MnemonicFragment : BaseFragment<MnemonicViewModel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_mnemonic, container, false)
    }

    override fun initViews() {
        toolbar.setTitle(getString(R.string.passphrase_title))
        toolbar.hideHomeButton()
    }

    override fun subscribe(viewModel: MnemonicViewModel) {
        RxView.clicks(nextBtn)
            .subscribe { viewModel.btnNextClicked() }

        RxView.clicks(btnShare)
            .subscribe {
                ShareUtil.openShareDialog(
                    (activity as AppCompatActivity?)!!, getString(R.string.save_mnemonic_title),
                    passphraseTv.text.toString()
                )
            }

        observe(viewModel.mnemonicLiveData, Observer {
            passphraseTv.text = it
        })

        observe(viewModel.getPreloadVisibility(), Observer {
            if (it) preloaderView.show() else preloaderView.gone()
        })

        viewModel.getPassphrase()
    }

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(context!!, OnboardingFeatureApi::class.java)
            .mnemonicComponentBuilder()
            .withFragment(this)
            .withRouter(activity as OnboardingRouter)
            .build()
            .inject(this)
    }
}