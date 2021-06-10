package jp.co.soramitsu.feature_wallet_impl.presentation.assetlist.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.util.ext.showOrGone
import jp.co.soramitsu.feature_wallet_impl.R

class AssetListAdapter(
    private val clickListener: (AssetListItemModel) -> Unit,
    private val showAmount: Boolean = false,
) : ListAdapter<AssetListItemModel, AssetListItemViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): AssetListItemViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_asset_list, viewGroup, false)
        return AssetListItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: AssetListItemViewHolder, position: Int) {
        holder.bind(getItem(position), showAmount, clickListener)
    }
}

class AssetListItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {

    private val itemName: TextView = itemView.findViewById(R.id.tvAssetListItemName)
    private val itemAmount: TextView = itemView.findViewById(R.id.tvAssetListItemAmount)
    private val itemIcon: ImageView = itemView.findViewById(R.id.ivAssetListItem)

    @SuppressLint("SetTextI18n")
    fun bind(asset: AssetListItemModel, show: Boolean, clickListener: (AssetListItemModel) -> Unit) {
        itemView.setOnClickListener { clickListener.invoke(asset) }
        itemName.text = "${asset.title} (${asset.tokenName})"
        itemAmount.text = asset.amount
        itemAmount.showOrGone(show)
        itemIcon.setImageResource(asset.icon)
    }
}

object DiffCallback : DiffUtil.ItemCallback<AssetListItemModel>() {
    override fun areItemsTheSame(oldItem: AssetListItemModel, newItem: AssetListItemModel): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: AssetListItemModel, newItem: AssetListItemModel): Boolean {
        return oldItem == newItem
    }
}
