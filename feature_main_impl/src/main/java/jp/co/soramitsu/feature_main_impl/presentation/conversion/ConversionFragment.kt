/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.conversion

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_information_api.domain.model.Currency
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import kotlinx.android.synthetic.main.fragment_conversion.currency_list
import kotlinx.android.synthetic.main.fragment_conversion.preloaderView
import kotlinx.android.synthetic.main.fragment_conversion.toolbar

@SuppressLint("CheckResult")
class ConversionFragment : BaseFragment<ConversionViewModel>() {

    private lateinit var adapter: CurrenciesAdapter

    private val currenciesVm = mutableListOf<Currency>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_conversion, container, false)
    }

    override fun initViews() {
        toolbar.setTitle(getString(R.string.conversion))
        toolbar.setHomeButtonListener { viewModel.backButtonPressed() }

        (activity as MainActivity).hideBottomView()

        adapter = CurrenciesAdapter(activity!!, currenciesVm)
        val linearLayoutManager = LinearLayoutManager(activity, RecyclerView.VERTICAL, false)
        currency_list.layoutManager = linearLayoutManager
        currency_list.adapter = adapter
        currency_list.setHasFixedSize(true)
    }

    override fun subscribe(viewModel: ConversionViewModel) {
        viewModel.loadCurrencies(true, false)
        adapter.itemViewClickSubject
            .subscribe { currency -> viewModel.setCurrencySelected(currency) }

        observe(viewModel.currenciesLiveData, Observer {
            adapter.setCurrencies(it)
            currency_list.show()
        })

        observe(viewModel.getPreloadVisibility(), Observer {
            if (it) preloaderView.show() else preloaderView.gone()
        })
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .conversionComponentBuilder()
            .withFragment(this)
            .withRouter(activity as MainRouter)
            .build()
            .inject(this)
    }
}