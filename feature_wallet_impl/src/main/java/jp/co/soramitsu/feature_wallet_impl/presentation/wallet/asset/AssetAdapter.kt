/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.asset

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.AssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.view.AssetView

class AssetAdapter(
    private val clickListener: (AssetModel) -> Unit
) : ListAdapter<AssetModel, AssetViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): AssetViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_asset, viewGroup, false)
        return AssetViewHolder(view)
    }

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        holder.bind(getItem(position), clickListener)
    }
}

class AssetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    fun bind(asset: AssetModel, clickListener: (AssetModel) -> Unit) {
        with(itemView as AssetView) {
            setAssetFirstName(asset.assetFirstName)
            setAssetIconResource(asset.assetIconResource)
            setAssetIconViewBackgroundColor(asset.assetIconBackgroundColor)
            setAssetLastName(asset.assetLastName)

            asset.balance?.let {
                setBalance(it)
            }

            asset.state?.let {
                val state = when (it) {
                    AssetModel.State.NORMAL -> AssetView.State.NORMAL
                    AssetModel.State.ASSOCIATING -> AssetView.State.ASSOCIATING
                    AssetModel.State.ERROR -> AssetView.State.ERROR
                }
                changeState(state)
            }

            setOnClickListener { clickListener(asset) }
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