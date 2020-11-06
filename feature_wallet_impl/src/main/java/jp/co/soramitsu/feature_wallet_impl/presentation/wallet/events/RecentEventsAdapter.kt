/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.events

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat.getColor
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.EventHeader
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.SoraTransaction
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.event_section_header.eventItemDayTextView
import kotlinx.android.synthetic.main.event_section_item.rootEvent
import kotlinx.android.synthetic.main.event_section_item.eventStatusIconImageView
import kotlinx.android.synthetic.main.event_section_item.eventItemTitleTextView
import kotlinx.android.synthetic.main.event_section_item.eventItemDescriptionTextView
import kotlinx.android.synthetic.main.event_section_item.eventItemAmountTextView
import kotlinx.android.synthetic.main.event_section_item.eventItemDateTextView

class RecentEventsAdapter(
    private val debounceClickHandler: DebounceClickHandler,
    private val itemClickedListener: (SoraTransaction) -> Unit
) : ListAdapter<Any, EventViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is SoraTransaction -> R.layout.event_section_item
            is EventHeader -> R.layout.event_section_header
            else -> throw IllegalStateException("Unknown view type at position $position")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        return when (viewType) {
            R.layout.event_section_item -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.event_section_item, parent, false)
                EventViewHolder.EventItemViewHolder(view)
            }
            R.layout.event_section_header -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.event_section_header, parent, false)
                EventViewHolder.EventHeaderViewHolder(view)
            }
            else -> throw IllegalStateException("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        when (holder) {
            is EventViewHolder.EventItemViewHolder -> holder.bind(getItem(position) as SoraTransaction, debounceClickHandler, itemClickedListener)
            is EventViewHolder.EventHeaderViewHolder -> holder.bind(getItem(position) as EventHeader)
        }
    }
}

object DiffCallback : DiffUtil.ItemCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        return when {
            oldItem is SoraTransaction && newItem is SoraTransaction -> oldItem.id == newItem.id
            oldItem is EventHeader && newItem is EventHeader -> true
            else -> false
        }
    }

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        return when {
            oldItem is SoraTransaction && newItem is SoraTransaction -> oldItem == newItem
            oldItem is EventHeader && newItem is EventHeader -> true
            else -> true
        }
    }
}

sealed class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), LayoutContainer {

    class EventItemViewHolder(override val containerView: View?) : EventViewHolder(containerView!!) {

        fun bind(soraTransaction: SoraTransaction, debounceClickHandler: DebounceClickHandler, itemClickedListener: (SoraTransaction) -> Unit) {
            rootEvent.setOnClickListener(DebounceClickListener(debounceClickHandler) {
                itemClickedListener(soraTransaction)
            })

            eventItemTitleTextView.text = soraTransaction.title
            eventItemDateTextView.text = soraTransaction.dateString

            if (soraTransaction.description.isEmpty()) {
                eventItemDescriptionTextView.gone()
            } else {
                eventItemDescriptionTextView.text = soraTransaction.description
            }

            if (soraTransaction.isIncoming) {
                val amountText = "+ ${soraTransaction.amountFormatted}"
                eventItemAmountTextView.setTextColor(getColor(eventItemAmountTextView.context, R.color.secondaryGreen))
                eventItemAmountTextView.text = amountText
            } else {
                val amountText = "- ${soraTransaction.amountFormatted}"
                eventItemAmountTextView.setTextColor(getColor(eventItemAmountTextView.context, R.color.black))
                eventItemAmountTextView.text = amountText
            }

            eventStatusIconImageView.setImageResource(soraTransaction.statusIconResource)
        }
    }

    class EventHeaderViewHolder(override val containerView: View?) : EventViewHolder(containerView!!), LayoutContainer {

        fun bind(item: EventHeader) {
            eventItemDayTextView.text = item.title
        }
    }
}