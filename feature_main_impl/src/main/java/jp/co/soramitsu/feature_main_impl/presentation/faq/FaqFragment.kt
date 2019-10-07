/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.faq

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebViewClient
import androidx.navigation.NavController
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.SoraProgressDialog
import jp.co.soramitsu.common.presentation.view.SoraToolbar
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import kotlinx.android.synthetic.main.fragment_terms.toolbar
import kotlinx.android.synthetic.main.fragment_terms.webView

class FaqFragment : BaseFragment<FaqViewModel>() {

    companion object {
        private const val SORA_FAQ_PAGE = "https://sora.org/faq"

        fun start(navController: NavController) {
            navController.navigate(R.id.privacyFragment)
        }
    }

    private lateinit var progressDialog: SoraProgressDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_terms, container, false)
    }

    override fun initViews() {
        progressDialog = SoraProgressDialog(activity!!)

        with(toolbar as SoraToolbar) {
            setTitle(getString(R.string.faq))
            setHomeButtonListener { viewModel.onBackPressed() }
            showHomeButton()
        }

        configureWebView()
    }

    override fun subscribe(viewModel: FaqViewModel) {}

    private fun configureWebView() {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressDialog.dismiss()
            }
        }

        progressDialog.show()
        webView.loadUrl(SORA_FAQ_PAGE)
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .faqComponentBuilder()
            .withFragment(this)
            .withRouter(activity as MainRouter)
            .build()
            .inject(this)
    }
}