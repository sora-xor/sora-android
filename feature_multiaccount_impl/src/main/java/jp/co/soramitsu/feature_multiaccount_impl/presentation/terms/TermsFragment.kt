/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.terms

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.ext.attrColor
import jp.co.soramitsu.common.util.ext.setPageBackground
import jp.co.soramitsu.feature_multiaccount_api.di.MultiaccountFeatureApi
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.databinding.FragmentTermsBinding
import jp.co.soramitsu.feature_multiaccount_impl.di.MultiaccountFeatureComponent

class TermsFragment : BaseFragment<TermsViewModel>(R.layout.fragment_terms) {

    private lateinit var progressDialog: SoraProgressDialog
    private val viewBinding by viewBinding(FragmentTermsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressDialog = SoraProgressDialog(requireContext())
        viewBinding.toolbar.setTitle(getString(R.string.common_terms_title))
        viewBinding.toolbar.setHomeButtonListener { viewModel.onBackPressed() }
        configureWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        viewBinding.webView.settings.javaScriptEnabled = true
        viewBinding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                view.setPageBackground(requireContext().attrColor(R.attr.baseBackground))
                progressDialog.dismiss()
            }
        }

        progressDialog.show()
        viewBinding.webView.loadUrl(Const.SORA_TERMS_PAGE)
    }

    override fun inject() {
        FeatureUtils.getFeature<MultiaccountFeatureComponent>(
            requireContext(),
            MultiaccountFeatureApi::class.java
        )
            .termsComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }
}
