/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.language

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import kotlinx.android.synthetic.main.fragment_select_language.languageRecyclerView
import kotlinx.android.synthetic.main.fragment_select_language.toolbar
import javax.inject.Inject

class SelectLanguageFragment : BaseFragment<SelectLanguageViewModel>() {

    @Inject lateinit var debounceClickHandler: DebounceClickHandler

    @Inject lateinit var resourceManager: ResourceManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_select_language, container, false)
    }

    override fun initViews() {
        (activity as BottomBarController).hideBottomBar()

        with(toolbar) {
            setHomeButtonListener { viewModel.onBackPressed() }
            showHomeButton()
        }

        languageRecyclerView.adapter = SelectLanguageAdapter(debounceClickHandler) { viewModel.languageSelected(it) }
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .selectLanguageComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    override fun subscribe(viewModel: SelectLanguageViewModel) {
        observe(viewModel.languagesLiveData, Observer {
            (languageRecyclerView.adapter as SelectLanguageAdapter).submitList(it)
        })

        observe(viewModel.languageChangedLiveData, EventObserver {
            (activity as MainActivity).restartAfterLanguageChange()
        })
    }
}
