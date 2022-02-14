/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.language

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentSelectLanguageBinding
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import javax.inject.Inject

class SelectLanguageFragment :
    BaseFragment<SelectLanguageViewModel>(R.layout.fragment_select_language) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    @Inject
    lateinit var resourceManager: ResourceManager
    private val binding by viewBinding(FragmentSelectLanguageBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        binding.toolbar.setHomeButtonListener { viewModel.onBackPressed() }

        binding.languageRecyclerView.adapter =
            SelectLanguageAdapter(debounceClickHandler) { viewModel.languageSelected(it) }

        ContextCompat.getDrawable(
            binding.languageRecyclerView.context,
            R.drawable.line_ver_divider
        )?.let {
            binding.languageRecyclerView.addItemDecoration(
                DividerItemDecoration(
                    binding.languageRecyclerView.context,
                    DividerItemDecoration.VERTICAL
                ).apply {
                    setDrawable(it)
                }
            )
        }

        viewModel.languagesLiveData.observe {
            (binding.languageRecyclerView.adapter as SelectLanguageAdapter).submitList(it)
        }

        viewModel.languageChangedLiveData.observe {
            (activity as MainActivity).restartAfterLanguageChange()
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(requireContext(), MainFeatureApi::class.java)
            .selectLanguageComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }
}
