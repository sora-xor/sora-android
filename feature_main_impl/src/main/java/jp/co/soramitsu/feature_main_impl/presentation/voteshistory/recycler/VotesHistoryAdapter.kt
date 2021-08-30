/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.voteshistory.recycler

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.VotesHitsoryHeaderItemBinding
import jp.co.soramitsu.feature_main_impl.databinding.VotesHitsoryItemBinding
import jp.co.soramitsu.feature_main_impl.presentation.voteshistory.model.VotesHistoryItem

class VotesHistoryAdapter(private val numbersFormatter: NumbersFormatter) :
    ListAdapter<VotesHistoryItem, VotesHistoryViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VotesHistoryViewHolder {
        return if (viewType == R.layout.votes_hitsory_item) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.votes_hitsory_item, parent, false)
            VotesHistoryViewHolder.VotesHistoryItemViewHolder(view, numbersFormatter)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.votes_hitsory_header_item, parent, false)
            VotesHistoryViewHolder.VotesHistoryHeaderViewHolder(view)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isHeader()) R.layout.votes_hitsory_header_item else R.layout.votes_hitsory_item
    }

    override fun onBindViewHolder(holder: VotesHistoryViewHolder, position: Int) {
        when (holder) {
            is VotesHistoryViewHolder.VotesHistoryItemViewHolder -> holder.bind(getItem(position))
            is VotesHistoryViewHolder.VotesHistoryHeaderViewHolder -> holder.bind(getItem(position))
        }
    }
}

object DiffCallback : DiffUtil.ItemCallback<VotesHistoryItem>() {

    override fun areItemsTheSame(oldItem: VotesHistoryItem, newItem: VotesHistoryItem): Boolean {
        return oldItem.message == newItem.message &&
            oldItem.timestamp == newItem.timestamp &&
            oldItem.operation == newItem.operation &&
            oldItem.votes == newItem.votes
    }

    override fun areContentsTheSame(oldItem: VotesHistoryItem, newItem: VotesHistoryItem): Boolean {
        return oldItem.message == newItem.message &&
            oldItem.timestamp == newItem.timestamp &&
            oldItem.operation == newItem.operation &&
            oldItem.votes == newItem.votes
    }
}

sealed class VotesHistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    class VotesHistoryItemViewHolder(
        itemView: View,
        private val numbersFormatter: NumbersFormatter
    ) : VotesHistoryViewHolder(itemView) {

        private val binding = VotesHitsoryItemBinding.bind(itemView)

        fun bind(votesHistoryItem: VotesHistoryItem) {
            binding.voteHistoryItemDescriptionTextView.text = votesHistoryItem.message
            binding.voteHistoryItemAmountTextView.text =
                numbersFormatter.formatInteger(votesHistoryItem.votes)
            if (votesHistoryItem.operation == '-') {
                binding.voteHistoryItemStatusImageView.setImageResource(R.drawable.minus)
            } else {
                binding.voteHistoryItemStatusImageView.setImageResource(R.drawable.plus)
            }
        }
    }

    class VotesHistoryHeaderViewHolder(itemView: View) : VotesHistoryViewHolder(itemView) {

        private val binding = VotesHitsoryHeaderItemBinding.bind(itemView)

        fun bind(votesHistoryItem: VotesHistoryItem) {
            binding.votesHistoryItemDayTextView.text = votesHistoryItem.header
        }
    }
}
