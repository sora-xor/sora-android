package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.bottomsheet

import android.annotation.SuppressLint
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.R
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
            oldItem is SoraTransaction && newItem is SoraTransaction -> oldItem.transactionId == newItem.transactionId
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

    class EventItemViewHolder(view: View) : EventViewHolder(view) {

        private val eventItemRoot: View = view.findViewById(R.id.rootEvent)
        private val eventItemDescriptionTextView: TextView = view.findViewById(R.id.eventItemDescriptionTextView)
        private val eventItemAmountTextView: TextView = view.findViewById(R.id.eventItemAmountTextView)
        private val eventItemStatusImageView: ImageView = view.findViewById(R.id.eventItemStatusImageView)

        fun bind(soraTransaction: SoraTransaction, debounceClickHandler: DebounceClickHandler, itemClickedListener: (SoraTransaction) -> Unit) {
            eventItemRoot.setOnClickListener(DebounceClickListener(debounceClickHandler) {
                itemClickedListener(soraTransaction)
            })

            eventItemDescriptionTextView.text = soraTransaction.recipientWithPrefix

            if (Transaction.Type.OUTGOING != soraTransaction.type && Transaction.Type.WITHDRAW != soraTransaction.type) {
                val plusSign = SpannableString("+")
                val secondaryColor = ContextCompat.getColor(itemView.context, R.color.secondaryGreen)
                plusSign.setSpan(ForegroundColorSpan(secondaryColor), 0, plusSign.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                val amountText = "$plusSign ${soraTransaction.amountFormatted}"
                eventItemAmountTextView.text = amountText
            } else {
                val amountText = "- ${soraTransaction.amountFormatted}"
                eventItemAmountTextView.text = amountText
            }

            val icon = when (Transaction.Status.valueOf(soraTransaction.status.toUpperCase())) {
                Transaction.Status.PENDING -> R.drawable.icon_pending
                Transaction.Status.REJECTED -> R.drawable.icon_wrong
                else -> 0
            }

            eventItemStatusImageView.setImageResource(icon)
        }
    }

    class EventHeaderViewHolder(view: View) : EventViewHolder(view) {

        private val eventItemDayTextView: TextView = view.findViewById(R.id.eventItemDayTextView)

        fun bind(item: EventHeader) {
            eventItemDayTextView.text = item.title
        }
    }
}