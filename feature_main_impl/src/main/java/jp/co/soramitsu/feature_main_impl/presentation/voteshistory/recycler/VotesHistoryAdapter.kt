/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.voteshistory.recycler

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.presentation.voteshistory.model.VotesHistoryItem
import kotlinx.android.synthetic.main.votes_hitsory_header_item.view.votesHistoryItemDayTextView
import kotlinx.android.synthetic.main.votes_hitsory_item.view.voteHistoryItemAmountTextView
import kotlinx.android.synthetic.main.votes_hitsory_item.view.voteHistoryItemDescriptionTextView
import kotlinx.android.synthetic.main.votes_hitsory_item.view.voteHistoryItemStatusImageView

class VotesHistoryAdapter(private val numbersFormatter: NumbersFormatter) : ListAdapter<VotesHistoryItem, VotesHistoryViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VotesHistoryViewHolder {
        return if (viewType == R.layout.votes_hitsory_item) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.votes_hitsory_item, parent, false)
            VotesHistoryViewHolder.VotesHistoryItemViewHolder(view, numbersFormatter)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.votes_hitsory_header_item, parent, false)
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

    class VotesHistoryItemViewHolder(itemView: View, private val numbersFormatter: NumbersFormatter) : VotesHistoryViewHolder(itemView) {

        private val voteHistoryItemAmountTextView: TextView = itemView.voteHistoryItemAmountTextView
        private val voteHistoryItemDescriptionTextView: TextView = itemView.voteHistoryItemDescriptionTextView
        private val voteHistoryItemStatusImageView: ImageView = itemView.voteHistoryItemStatusImageView

        fun bind(votesHistoryItem: VotesHistoryItem) {
            voteHistoryItemDescriptionTextView.text = votesHistoryItem.message
            voteHistoryItemAmountTextView.text = numbersFormatter.formatInteger(votesHistoryItem.votes)

            if (votesHistoryItem.operation == '-') {
                voteHistoryItemStatusImageView.setImageResource(R.drawable.minus)
            } else {
                voteHistoryItemStatusImageView.setImageResource(R.drawable.plus)
            }
        }
    }

    class VotesHistoryHeaderViewHolder(itemView: View) : VotesHistoryViewHolder(itemView) {

        private val votesHistoryItemDayTextView: TextView = itemView.votesHistoryItemDayTextView

        fun bind(votesHistoryItem: VotesHistoryItem) {
            votesHistoryItemDayTextView.text = votesHistoryItem.header
        }
    }
}