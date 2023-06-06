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
