package jp.co.soramitsu.feature_onboarding_impl.presentation.privacy

import android.os.Bundle
import android.view.View
import android.webkit.WebViewClient
import androidx.navigation.NavController
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.databinding.FragmentTermsBinding
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter

class PrivacyFragment : BaseFragment<PrivacyViewModel>(R.layout.fragment_terms) {

    companion object {
        fun start(navController: NavController) {
            navController.navigate(R.id.privacyFragment)
        }
    }

    private lateinit var progressDialog: SoraProgressDialog
    private val viewBinding by viewBinding(FragmentTermsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressDialog = SoraProgressDialog(requireContext())

        with(viewBinding.toolbar) {
            setTitle(getString(R.string.about_privacy))
            setHomeButtonListener { viewModel.onBackPressed() }
            showHomeButton()
        }

        configureWebView()
    }

    private fun configureWebView() {
        viewBinding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressDialog.dismiss()
            }
        }

        progressDialog.show()
        viewBinding.webView.loadUrl(Const.SORA_PRIVACY_PAGE)
    }

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(
            requireContext(),
            OnboardingFeatureApi::class.java
        )
            .privacyComponentBuilder()
            .withFragment(this)
            .withRouter(activity as OnboardingRouter)
            .build()
            .inject(this)
    }
}
