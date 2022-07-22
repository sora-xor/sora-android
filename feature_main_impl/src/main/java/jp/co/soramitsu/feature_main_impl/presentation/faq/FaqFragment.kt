/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.faq

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebViewClient
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.util.ext.attrColor
import jp.co.soramitsu.common.util.ext.setPageBackground
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentTermsBinding
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController

@AndroidEntryPoint
class FaqFragment : BaseFragment<FaqViewModel>(R.layout.fragment_terms) {

    companion object {
        private const val SORA_FAQ_PAGE = "https://wiki.sora.org/sora-faq"
    }

    private val vm: FaqViewModel by viewModels()

    override val viewModel: FaqViewModel
        get() = vm

    private lateinit var progressDialog: SoraProgressDialog
    private val binding by viewBinding(FragmentTermsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        progressDialog = SoraProgressDialog(requireContext())
        with(binding.toolbar) {
            setHomeButtonListener { viewModel.onBackPressed() }
        }
        configureWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.setPageBackground(requireContext().attrColor(R.attr.baseBackground))
                progressDialog.dismiss()
            }
        }
        progressDialog.show()
        binding.webView.loadUrl(SORA_FAQ_PAGE)
    }
}
