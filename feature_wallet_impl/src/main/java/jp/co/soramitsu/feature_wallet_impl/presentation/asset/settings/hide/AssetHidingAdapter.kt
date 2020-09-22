package jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.hide

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.hide.model.AssetHidingModel
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.hide.view.AssetHidingView

class AssetHidingAdapter(
    private val checkedChangeListener: (AssetHidingModel, Boolean) -> Unit
) : ListAdapter<AssetHidingModel, AssetViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): AssetViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_asset_hiding, viewGroup, false)
        return AssetViewHolder(view)
    }

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        holder.bind(getItem(position), checkedChangeListener)
    }
}

class AssetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    fun bind(asset: AssetHidingModel, checkedChangeListener: (AssetHidingModel, Boolean) -> Unit) {
        with(itemView as AssetHidingView) {
            setAssetFirstName(asset.assetFirstName)
            setAssetIconResource(asset.assetIconResource)
            setAssetLastName(asset.assetLastName)

            asset.balance?.let {
                setBalance(it)
            }

            setCheckChangeListener { checkedChangeListener(asset, it) }
        }
    }
}

object DiffCallback : DiffUtil.ItemCallback<AssetHidingModel>() {
    override fun areItemsTheSame(oldItem: AssetHidingModel, newItem: AssetHidingModel): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: AssetHidingModel, newItem: AssetHidingModel): Boolean {
        return oldItem == newItem
    }
}