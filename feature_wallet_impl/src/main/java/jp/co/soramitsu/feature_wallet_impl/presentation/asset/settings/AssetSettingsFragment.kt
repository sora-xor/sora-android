package jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.view.hideSoftKeyboard
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentAssetSettingsBinding
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.display.AssetConfigurableAdapter

class AssetSettingsFragment :
    BaseFragment<AssetSettingsViewModel>(R.layout.fragment_asset_settings) {

    private val itemTouchHelperCallback = CustomItemTouchHelperCallback { from, to ->
        viewModel.assetPositionChanged(from, to)
    }
    private val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)

    private val binding by viewBinding(FragmentAssetSettingsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        binding.tbAssetManagement.setRightActionClickListener { viewModel.doneClicked() }
        binding.tbAssetManagement.setHomeButtonListener { viewModel.backClicked() }
        itemTouchHelper.attachToRecyclerView(binding.rvAssetManagementList)
        binding.svAssetList.setOnQueryTextListener(queryListener)
        viewModel.assetsListLiveData.observe {
            if (binding.rvAssetManagementList.adapter == null) {
                binding.rvAssetManagementList.layoutManager = LinearLayoutManager(requireContext())
                binding.rvAssetManagementList.adapter =
                    AssetConfigurableAdapter(itemTouchHelper) { asset, checked ->
                        viewModel.checkChanged(asset, checked)
                    }
            }
            (binding.rvAssetManagementList.adapter as AssetConfigurableAdapter).submitList(it)
        }
        viewModel.assetPositions.observe {
            (binding.rvAssetManagementList.adapter as AssetConfigurableAdapter).notifyItemMoved(
                it.first,
                it.second
            )
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .assetSettingsComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    private val queryListener = object : SearchView.OnQueryTextListener {

        override fun onQueryTextSubmit(query: String?): Boolean {
            hideSoftKeyboard(activity)
            viewModel.searchAssets(query.orEmpty())
            return true
        }

        override fun onQueryTextChange(newText: String?): Boolean {
            viewModel.searchAssets(newText.orEmpty())
            itemTouchHelperCallback.isDraggable = newText.isNullOrBlank()
            return true
        }
    }
}
