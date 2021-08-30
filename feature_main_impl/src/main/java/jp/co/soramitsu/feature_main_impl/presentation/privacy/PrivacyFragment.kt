package jp.co.soramitsu.feature_main_impl.presentation.privacy

import android.os.Bundle
import android.view.View
import android.webkit.WebViewClient
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentTermsBinding
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController

class PrivacyFragment : BaseFragment<PrivacyViewModel>(R.layout.fragment_terms) {

    private lateinit var progressDialog: SoraProgressDialog
    private val binding by viewBinding(FragmentTermsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        progressDialog = SoraProgressDialog(requireContext())
        with(binding.toolbar) {
            setTitle(getString(R.string.about_privacy))
            setHomeButtonListener { viewModel.onBackPressed() }
            showHomeButton()
        }

        configureWebView()
    }

    private fun configureWebView() {
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressDialog.dismiss()
            }
        }

        progressDialog.show()
        binding.webView.loadUrl(Const.SORA_PRIVACY_PAGE)
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(requireContext(), MainFeatureApi::class.java)
            .privacyComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }
}
