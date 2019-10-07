/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.passphrase

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
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import kotlinx.android.synthetic.main.fragment_my_mnemonic.btnShare
import kotlinx.android.synthetic.main.fragment_my_mnemonic.passphraseTv
import kotlinx.android.synthetic.main.fragment_my_mnemonic.preloaderView
import kotlinx.android.synthetic.main.fragment_my_mnemonic.toolbar

@SuppressLint("CheckResult")
class PassphraseFragment : BaseFragment<PassphraseViewModel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_my_mnemonic, container, false)
    }

    override fun initViews() {
        (activity as MainActivity).hideBottomView()

        toolbar.setTitle(getString(R.string.passphrase_title))
        toolbar.setHomeButtonListener { activity!!.onBackPressed() }

        RxView.clicks(btnShare)
            .subscribe { ShareUtil.openShareDialog((activity as AppCompatActivity?)!!, getString(R.string.save_mnemonic_title), passphraseTv.text.toString()) }
    }

    override fun subscribe(viewModel: PassphraseViewModel) {
        observe(viewModel.passphraseLiveData, Observer {
            if (it.isNotEmpty()) passphraseTv.text = it
        })

        observe(viewModel.getPreloadVisibility(), Observer {
            if (it) preloaderView.visibility = View.VISIBLE else preloaderView.visibility = View.GONE
        })

        viewModel.getPassphrase()
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .passphraseComponentBuilder()
            .withFragment(this)
            .withRouter(activity as MainRouter)
            .build()
            .inject(this)
    }
}