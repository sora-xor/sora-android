package jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.display.AssetConfigurableAdapter
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.hide.HidingAssetsBottomSheet
import kotlinx.android.synthetic.main.fragment_asset_settings.addAssetTv
import kotlinx.android.synthetic.main.fragment_asset_settings.assetSettingsToolbar
import kotlinx.android.synthetic.main.fragment_asset_settings.assetsRv

class AssetSettingsFragment : BaseFragment<AssetSettingsViewModel>() {

    private val itemTouchHelper = createTouchHelper()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_asset_settings, container, false)
    }

    override fun initViews() {
        assetSettingsToolbar.setLeftActionClickListener { viewModel.doneClicked() }
        assetSettingsToolbar.setRightActionClickListener { viewModel.hideAssetsButtonClicked() }
        addAssetTv.setOnClickListener { viewModel.addAssetClicked() }

        itemTouchHelper.attachToRecyclerView(assetsRv)
    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(context!!, WalletFeatureApi::class.java)
            .assetSettingsComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    override fun subscribe(viewModel: AssetSettingsViewModel) {
        observe(viewModel.displayingAssetsLiveData, Observer {
            if (assetsRv.adapter == null) {
                assetsRv.layoutManager = LinearLayoutManager(activity!!)
                assetsRv.adapter = AssetConfigurableAdapter(itemTouchHelper) { asset, checked ->
                    viewModel.checkChanged(asset, checked)
                }
            }
            (assetsRv.adapter as AssetConfigurableAdapter).submitList(it)
        })

        observe(viewModel.hideButtonEnabledLiveData, Observer {
            assetSettingsToolbar.setRightActionEnabled(it)
        })

        observe(viewModel.addingAccountAvailableLiveData, Observer {
            addAssetTv.isEnabled = it
        })

        observe(viewModel.showHidingAssetsView, EventObserver {
            val bottomSheet = HidingAssetsBottomSheet(activity!!, this, viewModel)
            bottomSheet.show()
        })
    }

    private fun createTouchHelper(): ItemTouchHelper {
        val callback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

            override fun isLongPressDragEnabled() = false

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                viewModel.assetPositionChanged(from, to)
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            }
        }

        return ItemTouchHelper(callback)
    }
}
