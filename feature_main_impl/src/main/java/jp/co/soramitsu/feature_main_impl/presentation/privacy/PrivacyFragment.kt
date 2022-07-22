/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.privacy

import android.os.Bundle
import android.view.View
import android.webkit.WebViewClient
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.ext.attrColor
import jp.co.soramitsu.common.util.ext.setPageBackground
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentTermsBinding
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController

@AndroidEntryPoint
class PrivacyFragment : BaseFragment<PrivacyViewModel>(R.layout.fragment_terms) {

    private lateinit var progressDialog: SoraProgressDialog
    private val binding by viewBinding(FragmentTermsBinding::bind)

    private val vm: PrivacyViewModel by viewModels()
    override val viewModel: PrivacyViewModel
        get() = vm

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        progressDialog = SoraProgressDialog(requireContext())
        with(binding.toolbar) {
            setTitle(getString(R.string.about_privacy))
            setHomeButtonListener { viewModel.onBackPressed() }
            showHomeButton()
        }

        val bgColor = requireContext().attrColor(R.attr.baseBackground)

        configureWebView(bgColor)
    }

    private fun configureWebView(attrColor: Int) {
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.setPageBackground(attrColor)
                progressDialog.dismiss()
            }
        }

        progressDialog.show()
        binding.webView.loadUrl(Const.SORA_PRIVACY_PAGE)
    }
}
