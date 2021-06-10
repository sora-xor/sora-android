/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.events

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationSet
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.presentation.view.ViewAnimations
import jp.co.soramitsu.common.util.ext.doAnimation
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.common.util.ext.showOrGone
import jp.co.soramitsu.common.util.ext.truncateUserAddress
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.EventSectionHeaderBinding
import jp.co.soramitsu.feature_wallet_impl.databinding.EventSectionItemBinding
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.EventHeader
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.SoraTransaction

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
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.event_section_item, parent, false)
                EventViewHolder.EventItemViewHolder(view)
            }
            R.layout.event_section_header -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.event_section_header, parent, false)
                EventViewHolder.EventHeaderViewHolder(view)
            }
            else -> throw IllegalStateException("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        when (holder) {
            is EventViewHolder.EventItemViewHolder -> holder.bind(
                getItem(position) as SoraTransaction,
                debounceClickHandler,
                itemClickedListener
            )
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

sealed class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    class EventItemViewHolder(containerView: View) : EventViewHolder(containerView) {

        private val viewBinding = EventSectionItemBinding.bind(containerView)
        private val rotateAnimation: AnimationSet = ViewAnimations.rotateAnimation

        fun bind(
            soraTransaction: SoraTransaction,
            debounceClickHandler: DebounceClickHandler,
            itemClickedListener: (SoraTransaction) -> Unit
        ) {
            viewBinding.rootEvent.setOnClickListener(
                DebounceClickListener(debounceClickHandler) {
                    itemClickedListener(soraTransaction)
                }
            )

            viewBinding.eventItemTitleTextView.text = soraTransaction.title.truncateUserAddress()
            viewBinding.eventItemDateTextView.text = soraTransaction.dateString

            if (soraTransaction.amountFormatted.isNotEmpty()) {
                viewBinding.eventItemFailedTextView.gone()
                viewBinding.eventItemAmountTextView.show()
                viewBinding.eventItemAmountFull.show()
                if (soraTransaction.isIncoming) {
                    val amountText = "+ ${soraTransaction.amountFormatted}"
                    viewBinding.eventItemAmountTextView.setTextColor(
                        MaterialColors.getColor(
                            viewBinding.eventItemAmountTextView,
                            R.attr.statusSuccess
                        )
                    )
                    viewBinding.eventItemAmountTextView.text = amountText
                    viewBinding.eventItemAmountFull.text =
                        "+ ${soraTransaction.amountFullFormatted}"
                } else {
                    val amountText = "− ${soraTransaction.amountFormatted}"
                    viewBinding.eventItemAmountTextView.setTextColor(
                        MaterialColors.getColor(
                            viewBinding.eventItemAmountTextView,
                            R.attr.contentPrimary
                        )
                    )
                    viewBinding.eventItemAmountTextView.text = amountText
                    viewBinding.eventItemAmountFull.text =
                        "- ${soraTransaction.amountFullFormatted}"
                }
            } else {
                viewBinding.eventItemFailedTextView.show()
                viewBinding.eventItemAmountTextView.gone()
                viewBinding.eventItemAmountFull.gone()
            }

            viewBinding.eventStatusIconImageView.setImageResource(soraTransaction.statusIconResource)
            viewBinding.eventStatusIconImageViewSp.showOrGone(soraTransaction.pending)
            viewBinding.eventStatusIconImageViewSp.doAnimation(
                soraTransaction.pending,
                rotateAnimation
            )
        }
    }

    class EventHeaderViewHolder(containerView: View) : EventViewHolder(containerView) {

        private val viewBinding = EventSectionHeaderBinding.bind(containerView)

        fun bind(item: EventHeader) {
            viewBinding.eventItemDayTextView.text = item.title
        }
    }
}
