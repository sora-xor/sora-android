/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.assetlist

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.view.assetselectbottomsheet.adapter.AssetListAdapter
import jp.co.soramitsu.common.util.ext.hideSoftKeyboard
import jp.co.soramitsu.common.util.ext.showOrGone
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetListMode
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentAssetListBinding
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent

class AssetListFragment : BaseFragment<AssetListViewModel>(R.layout.fragment_asset_list) {

    companion object {
        private const val ARG_MODE = "arg_mode"
        private const val ARG_HIDDEN_ASSET_ID = "ARG_HIDDEN_ASSET_ID"
        fun createBundle(mode: AssetListMode, hiddenAssetId: String? = null) = Bundle().apply {
            putSerializable(ARG_MODE, mode)
            hiddenAssetId?.let {
                putString(ARG_HIDDEN_ASSET_ID, hiddenAssetId)
            }
        }
    }

    private val mode: AssetListMode by lazy { requireArguments().get(ARG_MODE) as AssetListMode }
    private val hiddenAssetId: String? by lazy { requireArguments().getString(ARG_HIDDEN_ASSET_ID) }
    private val viewBinding by viewBinding(FragmentAssetListBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        viewBinding.tbAssetList.setHomeButtonListener { viewModel.backClicked() }
        viewBinding.tbAssetList.setTitle(mode.titleRes)
        viewBinding.svAssetList.setOnQueryTextListener(queryListener)
        viewModel.displayingAssetsLiveData.observe {
            if (viewBinding.rvAssetList.adapter == null) {
                viewBinding.rvAssetList.layoutManager = LinearLayoutManager(context)
                viewBinding.rvAssetList.adapter = AssetListAdapter(
                    { listItem ->
                        viewModel.itemClicked(listItem)
                    },
                    mode == AssetListMode.SEND
                )
                ContextCompat.getDrawable(
                    viewBinding.rvAssetList.context,
                    R.drawable.line_ver_divider
                )?.let {
                    viewBinding.rvAssetList.addItemDecoration(
                        DividerItemDecoration(
                            viewBinding.rvAssetList.context,
                            DividerItemDecoration.VERTICAL
                        ).apply {
                            setDrawable(it)
                        }
                    )
                }
            }

            (viewBinding.rvAssetList.adapter as AssetListAdapter).submitList(it)
            viewBinding.grAssetNotFound.showOrGone(it.isEmpty())
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .assetListComponentBuilder()
            .withFragment(this)
            .withAssetListMode(mode)
            .withHiddenAssetId(hiddenAssetId)
            .build()
            .inject(this)
    }

    private val queryListener = object : SearchView.OnQueryTextListener {

        override fun onQueryTextSubmit(query: String?): Boolean {
            hideSoftKeyboard()
            viewModel.searchAssets(query.orEmpty())
            return true
        }

        override fun onQueryTextChange(newText: String?): Boolean {
            viewModel.searchAssets(newText.orEmpty())
            return true
        }
    }
}
