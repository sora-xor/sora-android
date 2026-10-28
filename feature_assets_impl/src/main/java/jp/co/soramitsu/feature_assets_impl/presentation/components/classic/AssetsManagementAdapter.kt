/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_assets_impl.presentation.components.classic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.view.DragHandleView
import coil.load
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.DEFAULT_ICON
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.util.ext.showOrHide
import jp.co.soramitsu.feature_assets_impl.R
import jp.co.soramitsu.feature_assets_impl.presentation.states.AssetSettingsState

class AssetsManagementAdapter(
    private val touchHelper: ItemTouchHelper,
    private val canReorder: () -> Boolean,
    private val moveListener: (Int, Int) -> Boolean,
    private val favoriteListener: (AssetSettingsState) -> Unit,
    private val visibilityListener: (AssetSettingsState) -> Unit,
) : ListAdapter<AssetSettingsState, AssetViewHolder>(AssetsManagementAdapterDiffCallback) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): AssetViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.item_asset_in_settings, viewGroup, false)
        return AssetViewHolder(view, touchHelper, canReorder, moveListener, favoriteListener, visibilityListener) { position ->
            currentList.getOrNull(position)?.token?.let { it.isHidable && !AssetHolder.isKnownAsset(it.id) } == true
        }
    }

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class AssetViewHolder(
    itemView: View,
    touchHelper: ItemTouchHelper,
    private val canReorder: () -> Boolean,
    private val moveListener: (Int, Int) -> Boolean,
    private val favoriteListener: (AssetSettingsState) -> Unit,
    private val visibilityListener: (AssetSettingsState) -> Unit,
    private val canMovePosition: (Int) -> Boolean,
) :
    RecyclerView.ViewHolder(itemView) {
    private val dragIcon = itemView.findViewById<DragHandleView>(R.id.ivAssetSettingsItemDrag)
    private val assetIcon = itemView.findViewById<ImageView>(R.id.ivAssetSettingsIcon)
    private val favoriteIcon = itemView.findViewById<ImageView>(R.id.ivAssetSettingsFavorite)
    private val favoriteIconClickable = itemView.findViewById<View>(R.id.ivAssetSettingsFavoriteClickable)
    private val visibilityIcon = itemView.findViewById<ImageView>(R.id.ivAssetSettingsVisibility)
    private val title = itemView.findViewById<TextView>(R.id.tvAssetSettingsTokenName)
    private val amount = itemView.findViewById<TextView>(R.id.tvAssetSettingsAmount)

    init {
        dragIcon.bindReordering(
            onStartDrag = { if (canReorder()) touchHelper.startDrag(this) },
            canMove = { delta ->
                val position = bindingAdapterPosition
                canReorder() && position != RecyclerView.NO_POSITION &&
                    position + delta in 0 until (bindingAdapter?.itemCount ?: 0) &&
                    canMovePosition(position) && canMovePosition(position + delta)
            },
            move = { delta -> moveListener(bindingAdapterPosition, bindingAdapterPosition + delta) },
        )
    }

    fun bind(asset: AssetSettingsState) {
        try {
            assetIcon.load(asset.token.iconUri())
        } catch (t: Throwable) {
            assetIcon.setImageResource(DEFAULT_ICON)
        }
        title.text = asset.token.name
        amount.text = asset.assetAmount

        favoriteIcon.setImageResource(if (asset.favorite) R.drawable.ic_favorite_enabled else R.drawable.ic_favorite_disabled)
        favoriteIconClickable.contentDescription = itemView.context.getString(
            if (asset.favorite) R.string.asset_remove_from_favorites else R.string.asset_add_to_favorites,
            asset.token.name,
        )
        ViewCompat.setStateDescription(favoriteIconClickable, itemView.context.getString(
            if (asset.favorite) R.string.asset_favorite_state else R.string.asset_not_favorite_state,
        ))
        favoriteIconClickable.isEnabled = asset.token.isHidable
        if (asset.token.isHidable) {
            favoriteIconClickable.setOnClickListener {
                favoriteListener.invoke(asset)
            }
            favoriteIcon.alpha = 1f
        } else {
            favoriteIconClickable.setOnClickListener(null)
            favoriteIconClickable.isClickable = false
            favoriteIcon.alpha = 0.5f
        }

        visibilityIcon.setImageResource(if (asset.visible) R.drawable.ic_eye_enabled else R.drawable.ic_eye_disabled)
        visibilityIcon.contentDescription = itemView.context.getString(
            if (asset.visible) R.string.asset_hide else R.string.asset_show, asset.token.name,
        )
        ViewCompat.setStateDescription(visibilityIcon, itemView.context.getString(
            if (asset.visible) R.string.asset_visible_state else R.string.asset_hidden_state,
        ))
        visibilityIcon.isEnabled = asset.token.isHidable
        if (asset.token.isHidable) {
            visibilityIcon.setOnClickListener {
                visibilityListener.invoke(asset)
            }
            visibilityIcon.alpha = 1f
        } else {
            visibilityIcon.setOnClickListener(null)
            visibilityIcon.isClickable = false
            visibilityIcon.alpha = 0.5f
        }
        dragIcon.showOrHide(!AssetHolder.isKnownAsset(asset.token.id))
    }
}

object AssetsManagementAdapterDiffCallback : DiffUtil.ItemCallback<AssetSettingsState>() {
    override fun areItemsTheSame(
        oldItem: AssetSettingsState,
        newItem: AssetSettingsState
    ): Boolean {
        return oldItem.token.id == newItem.token.id
    }

    override fun areContentsTheSame(
        oldItem: AssetSettingsState,
        newItem: AssetSettingsState
    ): Boolean {
        return oldItem == newItem
    }
}
