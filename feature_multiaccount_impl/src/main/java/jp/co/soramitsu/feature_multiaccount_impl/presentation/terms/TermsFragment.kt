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
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.ext.attrColor
import jp.co.soramitsu.common.util.ext.setPageBackground
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.databinding.FragmentTermsBinding

@AndroidEntryPoint
class TermsFragment : BaseFragment<TermsViewModel>(R.layout.fragment_terms) {

    private lateinit var progressDialog: SoraProgressDialog
    private val viewBinding by viewBinding(FragmentTermsBinding::bind)

    private val vm: TermsViewModel by viewModels()
    override val viewModel: TermsViewModel
        get() = vm

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressDialog = SoraProgressDialog(requireContext())
        viewBinding.toolbar.setTitle(getString(R.string.common_terms_title))
        viewBinding.toolbar.setHomeButtonListener { findNavController().popBackStack() }

        val bgColor = requireContext().attrColor(R.attr.baseBackground)

        configureWebView(bgColor)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(bgColor: Int) {
        viewBinding.webView.settings.javaScriptEnabled = true
        viewBinding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                view.setPageBackground(bgColor)
                progressDialog.dismiss()
            }
        }

        progressDialog.show()
        viewBinding.webView.loadUrl(Const.SORA_TERMS_PAGE)
    }
}
