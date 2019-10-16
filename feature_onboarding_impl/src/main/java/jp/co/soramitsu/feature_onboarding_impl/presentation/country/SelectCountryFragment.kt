/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.country

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_account_api.domain.model.Country
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import jp.co.soramitsu.feature_onboarding_impl.presentation.country.adapter.CountryAdapter
import jp.co.soramitsu.feature_onboarding_impl.presentation.country.adapter.CountryItemDecoration
import kotlinx.android.synthetic.main.fragment_select_country.countriesRecyclerView
import kotlinx.android.synthetic.main.fragment_select_country.countrySearchView
import kotlinx.android.synthetic.main.fragment_select_country.preloader
import kotlinx.android.synthetic.main.fragment_select_country.toolbar

class SelectCountryFragment : BaseFragment<SelectCountryViewModel>() {

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(context!!, OnboardingFeatureApi::class.java)
            .selectCountryComponentBuilder()
            .withFragment(this)
            .withRouter(activity as OnboardingRouter)
            .build()
            .inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_select_country, container, false)
    }

    override fun initViews() {
        countrySearchView.setOnQueryTextListener(queryListener)

        toolbar.setTitle(getString(R.string.select_country_title))
        toolbar.setHomeButtonListener { viewModel.backButtonClick() }
    }

    override fun subscribe(viewModel: SelectCountryViewModel) {
        observe(viewModel.countriesLiveData, Observer {
            showCountries(it)
        })

        observe(viewModel.getPreloadVisibility(), Observer {
            if (it) showPreloader() else hidePreloader()
        })

        viewModel.getCountries()
    }

    private val queryListener = object : SearchView.OnQueryTextListener {

        override fun onQueryTextSubmit(query: String?): Boolean {
            hideSoftKeyboard(activity)
            viewModel.searchCountries(query!!)
            return true
        }

        override fun onQueryTextChange(newText: String?): Boolean {
            viewModel.searchCountries(newText!!)
            return true
        }
    }

    private fun showCountries(countries: List<Country>) {
        if (countriesRecyclerView.adapter == null) {
            countriesRecyclerView.layoutManager = LinearLayoutManager(activity)
            countriesRecyclerView.adapter = CountryAdapter { viewModel.countrySelected(it) }
            val decorator = CountryItemDecoration(activity!!, ContextCompat.getDrawable(activity!!, R.drawable.divider_country_item)!!)
            countriesRecyclerView.addItemDecoration(decorator)
        }
        (countriesRecyclerView.adapter as CountryAdapter).submitList(countries)
    }

    private fun showPreloader() {
        countriesRecyclerView.gone()
        preloader.show()
    }

    private fun hidePreloader() {
        countriesRecyclerView.show()
        preloader.gone()
    }
}