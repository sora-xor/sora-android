/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.events

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.EventSectionLoadStateBinding

class EventsLoadAdapter : LoadStateAdapter<EventsLoadStateItemViewHolder>() {

    override fun onBindViewHolder(
        holder: EventsLoadStateItemViewHolder,
        loadState: LoadState
    ) = holder.bindTo(loadState)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        loadState: LoadState
    ): EventsLoadStateItemViewHolder = EventsLoadStateItemViewHolder(parent)
}

class EventsLoadStateItemViewHolder(
    parent: ViewGroup,
) : RecyclerView.ViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.event_section_load_state, parent, false)
) {
    private val binding = EventSectionLoadStateBinding.bind(itemView)
    private val pb = binding.pbLoadState

    fun bindTo(loadState: LoadState) {
        pb.isVisible = loadState is LoadState.Loading
    }
}
