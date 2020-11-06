package jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.display

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.display.model.AssetConfigurableModel
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.display.view.AssetConfigurableView

class AssetConfigurableAdapter(
    private val touchHelper: ItemTouchHelper,
    private val checkedChangeListener: (AssetConfigurableModel, Boolean) -> Unit
) : ListAdapter<AssetConfigurableModel, AssetViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): AssetViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_asset_configurable, viewGroup, false)
        return AssetViewHolder(view, touchHelper)
    }

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        holder.bind(getItem(position), checkedChangeListener)
    }
}

class AssetViewHolder(itemView: View, touchHelper: ItemTouchHelper) : RecyclerView.ViewHolder(itemView) {
    init {
        with(itemView as AssetConfigurableView) {
            setDragTouchListener { _, _ ->
                touchHelper.startDrag(this@AssetViewHolder)

                true
            }
        }
    }

    fun bind(asset: AssetConfigurableModel, checkedChangeListener: (AssetConfigurableModel, Boolean) -> Unit) {
        with(itemView as AssetConfigurableView) {
            setAssetFirstName(asset.assetFirstName)
            setAssetIconResource(asset.assetIconResource)
            setAssetIconViewBackgroundColor(asset.assetIconBackgroundColor)
            setAssetLastName(asset.assetLastName)

            asset.balance?.let {
                setBalance(it)
            }

            asset.state?.let {
                val state = when (it) {
                    AssetConfigurableModel.State.NORMAL -> AssetConfigurableView.State.NORMAL
                    AssetConfigurableModel.State.ASSOCIATING -> AssetConfigurableView.State.ASSOCIATING
                    AssetConfigurableModel.State.ERROR -> AssetConfigurableView.State.ERROR
                }
                changeState(state)
            }

            changeCheckEnableState(asset.changeCheckStateEnabled)

            setChecked(asset.checked)

            setCheckChangeListener { checkedChangeListener(asset, it) }
        }
    }
}

object DiffCallback : DiffUtil.ItemCallback<AssetConfigurableModel>() {
    override fun areItemsTheSame(oldItem: AssetConfigurableModel, newItem: AssetConfigurableModel): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: AssetConfigurableModel, newItem: AssetConfigurableModel): Boolean {
        return oldItem == newItem
    }
}