/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.presentation.components.classic

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
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.DEFAULT_ICON
import jp.co.soramitsu.common.util.ext.showOrHide
import jp.co.soramitsu.feature_assets_impl.R
import jp.co.soramitsu.feature_assets_impl.presentation.states.AssetSettingsState

class AssetsManagementAdapter(
    private val touchHelper: ItemTouchHelper,
    private val favoriteListener: (AssetSettingsState) -> Unit,
    private val visibilityListener: (AssetSettingsState) -> Unit,
) : ListAdapter<AssetSettingsState, AssetViewHolder>(AssetsManagementAdapterDiffCallback) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): AssetViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.item_asset_in_settings, viewGroup, false)
        return AssetViewHolder(view, touchHelper, favoriteListener, visibilityListener)
    }

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class AssetViewHolder(
    itemView: View,
    touchHelper: ItemTouchHelper,
    private val favoriteListener: (AssetSettingsState) -> Unit,
    private val visibilityListener: (AssetSettingsState) -> Unit,
) :
    RecyclerView.ViewHolder(itemView) {
    private val dragIcon = itemView.findViewById<ImageView>(R.id.ivAssetSettingsItemDrag)
    private val assetIcon = itemView.findViewById<ImageView>(R.id.ivAssetSettingsIcon)
    private val favoriteIcon = itemView.findViewById<ImageView>(R.id.ivAssetSettingsFavorite)
    private val favoriteIconClickable = itemView.findViewById<View>(R.id.ivAssetSettingsFavoriteClickable)
    private val visibilityIcon = itemView.findViewById<ImageView>(R.id.ivAssetSettingsVisibility)
    private val title = itemView.findViewById<TextView>(R.id.tvAssetSettingsTokenName)
    private val amount = itemView.findViewById<TextView>(R.id.tvAssetSettingsAmount)

    init {
        dragIcon.setOnTouchListener { _, _ ->
            touchHelper.startDrag(this)
            true
        }
    }

    fun bind(asset: AssetSettingsState) {
        try {
            assetIcon.load(asset.tokenIcon)
        } catch (t: Throwable) {
            assetIcon.setImageResource(DEFAULT_ICON)
        }
        title.text = asset.tokenName
        amount.text = asset.assetAmount

        favoriteIcon.setImageResource(if (asset.favorite) R.drawable.ic_favorite_enabled else R.drawable.ic_favorite_disabled)
        if (asset.hideAllowed) {
            favoriteIconClickable.setOnClickListener { v ->
                if (v.isPressed) favoriteListener.invoke(asset)
            }
            favoriteIcon.alpha = 1f
        } else {
            favoriteIconClickable.setOnClickListener(null)
            favoriteIcon.alpha = 0.5f
        }

        visibilityIcon.setImageResource(if (asset.visible) R.drawable.ic_eye_enabled else R.drawable.ic_eye_disabled)
        if (asset.hideAllowed) {
            visibilityIcon.setOnClickListener { v ->
                if (v.isPressed) visibilityListener.invoke(asset)
            }
            visibilityIcon.alpha = 1f
        } else {
            visibilityIcon.setOnClickListener(null)
            visibilityIcon.alpha = 0.5f
        }
        dragIcon.showOrHide(!AssetHolder.isKnownAsset(asset.id))
    }
}

object AssetsManagementAdapterDiffCallback : DiffUtil.ItemCallback<AssetSettingsState>() {
    override fun areItemsTheSame(
        oldItem: AssetSettingsState,
        newItem: AssetSettingsState
    ): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(
        oldItem: AssetSettingsState,
        newItem: AssetSettingsState
    ): Boolean {
        return oldItem == newItem
    }
}
