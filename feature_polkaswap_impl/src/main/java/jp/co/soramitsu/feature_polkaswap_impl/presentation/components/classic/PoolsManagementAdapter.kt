/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.components.classic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.PoolSettingsState

class PoolsManagementAdapter(
    private val touchHelper: ItemTouchHelper,
    private val favoriteListener: (PoolSettingsState) -> Unit,
) : ListAdapter<PoolSettingsState, PoolViewHolder>(PoolsManagementAdapterDiffCallback) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): PoolViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.item_pool_in_settings, viewGroup, false)
        return PoolViewHolder(view, touchHelper, favoriteListener)
    }

    override fun onBindViewHolder(holder: PoolViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class PoolViewHolder(
    itemView: View,
    touchHelper: ItemTouchHelper,
    private val favoriteListener: (PoolSettingsState) -> Unit,
) :
    RecyclerView.ViewHolder(itemView) {
    private val dragIcon = itemView.findViewById<ImageView>(R.id.ivPoolSettingsItemDrag)
    private val assetIcon = itemView.findViewById<ImageView>(R.id.ivPool1SettingsIcon)
    private val asset2Icon = itemView.findViewById<ImageView>(R.id.ivPool2SettingsIcon)
    private val favoriteIcon = itemView.findViewById<ImageView>(R.id.ivPoolSettingsFavorite)
    private val favoriteIconClickable = itemView.findViewById<View>(R.id.ivPoolSettingsFavoriteClickable)
    private val title = itemView.findViewById<TextView>(R.id.tvPoolSettingsTokenName)
    private val amount = itemView.findViewById<TextView>(R.id.tvPoolSettingsAmount)

    init {
        dragIcon.setOnTouchListener { _, _ ->
            touchHelper.startDrag(this)
            true
        }
    }

    fun bind(asset: PoolSettingsState) {
        assetIcon.load(asset.token1Icon)
        asset2Icon.load(asset.token2Icon)
        title.text = asset.tokenName
        amount.text = asset.assetAmount

        favoriteIcon.setImageResource(if (asset.favorite) R.drawable.ic_favorite_enabled else R.drawable.ic_favorite_disabled)
        favoriteIconClickable.setOnClickListener { v ->
            if (v.isPressed) favoriteListener.invoke(asset)
        }
    }
}

object PoolsManagementAdapterDiffCallback : DiffUtil.ItemCallback<PoolSettingsState>() {
    override fun areItemsTheSame(
        oldItem: PoolSettingsState,
        newItem: PoolSettingsState
    ): Boolean {
        return oldItem.id.first == newItem.id.first && oldItem.id.second == newItem.id.second
    }

    override fun areContentsTheSame(
        oldItem: PoolSettingsState,
        newItem: PoolSettingsState
    ): Boolean {
        return oldItem == newItem
    }
}
