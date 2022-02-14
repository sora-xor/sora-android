/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.display

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.feature_wallet_impl.R

class AssetConfigurableAdapter(
    private val touchHelper: ItemTouchHelper,
    private val checkedChangeListener: (AssetConfigurableModel, Boolean) -> Unit
) : ListAdapter<AssetConfigurableModel, AssetViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): AssetViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.item_asset_configurable, viewGroup, false)
        return AssetViewHolder(view, touchHelper)
    }

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        holder.bind(getItem(position), checkedChangeListener)
    }
}

class AssetViewHolder(itemView: View, touchHelper: ItemTouchHelper) :
    RecyclerView.ViewHolder(itemView) {
    private val dragIcon = itemView.findViewById<ImageView>(R.id.ivAssetManagementItemDrag)
    private val assetIcon = itemView.findViewById<ImageView>(R.id.ivAssetManagementItemIcon)
    private val switch = itemView.findViewById<SwitchMaterial>(R.id.swAssetManagementItem)
    private val title = itemView.findViewById<TextView>(R.id.tvAssetManagementItemTitle)
    private val ticker = itemView.findViewById<TextView>(R.id.tvAssetManagementItemTicker)
    private val amount = itemView.findViewById<TextView>(R.id.tvAssetManagementItemAmount)

    init {
        dragIcon.setOnTouchListener { _, _ ->
            touchHelper.startDrag(this)
            true
        }
    }

    fun bind(
        asset: AssetConfigurableModel,
        checkedChangeListener: (AssetConfigurableModel, Boolean) -> Unit
    ) {
        try {
            assetIcon.setImageResource(asset.assetIconResource)
        } catch (t: Throwable) {
            assetIcon.setImageResource(OptionsProvider.DEFAULT_ICON)
        }
        title.text = asset.assetLastName
        ticker.text = asset.assetFirstName
        amount.text = asset.balance
        switch.isChecked = asset.visible
        switch.setOnCheckedChangeListener { v, b ->
            if (v.isPressed)
                checkedChangeListener.invoke(asset, b)
        }
        dragIcon.isEnabled = asset.hideAllowed
    }
}

object DiffCallback : DiffUtil.ItemCallback<AssetConfigurableModel>() {
    override fun areItemsTheSame(
        oldItem: AssetConfigurableModel,
        newItem: AssetConfigurableModel
    ): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(
        oldItem: AssetConfigurableModel,
        newItem: AssetConfigurableModel
    ): Boolean {
        return oldItem == newItem
    }
}
