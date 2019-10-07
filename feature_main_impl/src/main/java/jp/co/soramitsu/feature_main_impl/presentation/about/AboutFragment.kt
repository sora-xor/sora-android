/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.about

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import com.jakewharton.rxbinding2.view.RxView
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.view.SoraToolbar
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.common.util.ext.createSendEmailIntent
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import kotlinx.android.synthetic.main.fragment_about.contacts_text
import kotlinx.android.synthetic.main.fragment_about.open_source_text
import kotlinx.android.synthetic.main.fragment_about.privacy_text
import kotlinx.android.synthetic.main.fragment_about.terms_text
import kotlinx.android.synthetic.main.fragment_about.toolbar
import kotlinx.android.synthetic.main.fragment_about.version_text

@SuppressLint("CheckResult")
class AboutFragment : BaseFragment<AboutViewModel>() {

    companion object {
        fun start(navController: NavController) {
            navController.navigate(R.id.aboutFragment)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .aboutComponentBuilder()
            .withFragment(this)
            .withRouter(activity as MainRouter)
            .build()
            .inject(this)
    }

    override fun initViews() {
        with(toolbar as SoraToolbar) {
            setTitle(getString(R.string.about))
            setHomeButtonListener { viewModel.backPressed() }
            showHomeButton()
        }

        RxView.clicks(open_source_text)
            .subscribe { viewModel.opensourceClick() }

        RxView.clicks(terms_text)
            .subscribe { viewModel.termsClick() }

        RxView.clicks(privacy_text)
            .subscribe { viewModel.privacyClick() }

        RxView.clicks(contacts_text)
            .subscribe { viewModel.contactsClicked() }
    }

    override fun subscribe(viewModel: AboutViewModel) {
        observe(viewModel.appVersionLiveData, Observer {
            version_text.text = it
        })

        observe(viewModel.openSendEmailEvent, EventObserver {
            activity!!.createSendEmailIntent(it, getString(R.string.send_email))
        })

        viewModel.init()
    }
}