package jp.co.soramitsu.feature_main_impl.presentation.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.common.util.ext.createSendEmailIntent
import jp.co.soramitsu.common.util.ext.showBrowser
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import kotlinx.android.synthetic.main.fragment_about.contactUsTv
import kotlinx.android.synthetic.main.fragment_about.privacyTv
import kotlinx.android.synthetic.main.fragment_about.sourceCodeTv
import kotlinx.android.synthetic.main.fragment_about.termsTv
import kotlinx.android.synthetic.main.fragment_about.toolbar
import kotlinx.android.synthetic.main.fragment_about.versionTv
import javax.inject.Inject

class AboutFragment : BaseFragment<AboutViewModel>() {

    @Inject lateinit var debounceClickHandler: DebounceClickHandler

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .aboutComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    override fun initViews() {
        (activity as BottomBarController).hideBottomBar()

        with(toolbar) {
            setHomeButtonListener { viewModel.backPressed() }
            showHomeButton()
        }

        sourceCodeTv.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            viewModel.openSourceClicked()
        })

        termsTv.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            viewModel.termsClicked()
        })

        privacyTv.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            viewModel.privacyClicked()
        })

        contactUsTv.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            viewModel.contactsClicked()
        })
    }

    override fun subscribe(viewModel: AboutViewModel) {
        observe(viewModel.appVersionLiveData, Observer {
            versionTv.text = it
        })

        observe(viewModel.openSendEmailEvent, EventObserver {
            activity!!.createSendEmailIntent(it, getString(R.string.common_select_email_app_title))
        })

        observe(viewModel.showBrowserLiveData, EventObserver {
            showBrowser(it)
        })

        viewModel.getAppVersion()
    }
}