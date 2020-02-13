/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.passphrase

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.util.ShareUtil
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import kotlinx.android.synthetic.main.fragment_my_mnemonic.btnShare
import kotlinx.android.synthetic.main.fragment_my_mnemonic.passphraseTv
import kotlinx.android.synthetic.main.fragment_my_mnemonic.preloaderView
import kotlinx.android.synthetic.main.fragment_my_mnemonic.toolbar
import javax.inject.Inject

class PassphraseFragment : BaseFragment<PassphraseViewModel>() {

    @Inject lateinit var debounceClickHandler: DebounceClickHandler

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_my_mnemonic, container, false)
    }

    override fun initViews() {
        (activity as BottomBarController).hideBottomBar()

        toolbar.setHomeButtonListener { activity!!.onBackPressed() }

        btnShare.setOnClickListener(
            DebounceClickListener(debounceClickHandler) {
                ShareUtil.openShareDialog((activity as AppCompatActivity?)!!, getString(R.string.common_passphrase_save_mnemonic_title), passphraseTv.text.toString())
            }
        )
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
            .build()
            .inject(this)
    }
}