package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.asset

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.AssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.view.AssetView

class AssetAdapter(
    private val debounceClickHandler: DebounceClickHandler,
    private val clickListener: (AssetModel) -> Unit
) : ListAdapter<AssetModel, AssetViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): AssetViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_asset, viewGroup, false)
        return AssetViewHolder(view)
    }

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        holder.bind(getItem(position), debounceClickHandler, clickListener)
    }

    fun allowToSwipe(pos: Int): Boolean = getItem(pos).hidingAllowed
    fun isHideIcon(pos: Int): Boolean = getItem(pos).let {
        it.hidingAllowed || (!it.hidingAllowed && it.displayed)
    }
    fun getAsset(pos: Int): AssetModel = getItem(pos)
}

class AssetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    fun bind(asset: AssetModel, debounceClickHandler: DebounceClickHandler, clickListener: (AssetModel) -> Unit) {
        with(itemView as AssetView) {
            setAssetFirstName(asset.assetFirstName)
            setAssetIconResource(asset.assetIconResource)
            setAssetLastName(asset.assetLastName)

            setBalance(if (asset.showMainBalance) asset.balance.orEmpty() else "")

            asset.state?.let {
                val state = when (it) {
                    AssetModel.State.NORMAL -> AssetView.State.NORMAL
                    AssetModel.State.ASSOCIATING -> AssetView.State.ASSOCIATING
                    AssetModel.State.ERROR -> AssetView.State.ERROR
                }
                changeState(state)
            }

            setDebouncedClickListener(debounceClickHandler) { clickListener(asset) }
        }
    }
}

object DiffCallback : DiffUtil.ItemCallback<AssetModel>() {
    override fun areItemsTheSame(oldItem: AssetModel, newItem: AssetModel): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: AssetModel, newItem: AssetModel): Boolean {
        return oldItem == newItem
    }
}
