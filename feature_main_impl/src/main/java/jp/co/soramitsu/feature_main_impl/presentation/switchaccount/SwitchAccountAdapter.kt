/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.switchaccount

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.showOrHide
import jp.co.soramitsu.common.util.ext.truncateUserAddress
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.ItemListSwitchAccountBinding

class SwitchAccountAdapter(
    private val debounceClickHandler: DebounceClickHandler,
    private val onItemClickListener: (SwitchAccountItem) -> Unit,
    private val onItemLongClickListener: (SwitchAccountItem) -> Unit,
) :
    ListAdapter<SwitchAccountItem, SwitchAccountViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwitchAccountViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_list_switch_account, parent, false)
        return SwitchAccountViewHolder(view)
    }

    override fun onBindViewHolder(holder: SwitchAccountViewHolder, position: Int) {
        val item = getItem(position)
        holder.itemView.setDebouncedClickListener(debounceClickHandler) {
            onItemClickListener.invoke(item)
        }
        holder.itemView.setOnLongClickListener {
            onItemLongClickListener.invoke(item)
            true
        }
        holder.bind(item)
    }
}

class SwitchAccountViewHolder(
    view: View,
) : RecyclerView.ViewHolder(view) {

    private val binding = ItemListSwitchAccountBinding.bind(view)

    fun bind(accountItem: SwitchAccountItem) {
        binding.tvSwitchAccountAddress.text = accountItem.accountAddress.truncateUserAddress()
        binding.ivSwitchAccountIcon.setImageDrawable(accountItem.icon)
        binding.ivSwitchAccountSelected.showOrHide(accountItem.selected)
    }
}

object DiffCallback : DiffUtil.ItemCallback<SwitchAccountItem>() {
    override fun areItemsTheSame(oldItem: SwitchAccountItem, newItem: SwitchAccountItem): Boolean {
        return oldItem.accountAddress == newItem.accountAddress
    }

    override fun areContentsTheSame(
        oldItem: SwitchAccountItem,
        newItem: SwitchAccountItem
    ): Boolean {
        return oldItem == newItem
    }
}
