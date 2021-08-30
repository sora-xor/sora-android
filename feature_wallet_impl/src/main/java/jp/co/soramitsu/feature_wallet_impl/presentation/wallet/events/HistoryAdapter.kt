package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.events

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationSet
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.ViewAnimations
import jp.co.soramitsu.common.util.ext.doAnimation
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.hide
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.common.util.ext.showOrHide
import jp.co.soramitsu.common.util.ext.truncateUserAddress
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.EventSectionHeaderBinding
import jp.co.soramitsu.feature_wallet_impl.databinding.EventSectionItemBinding
import jp.co.soramitsu.feature_wallet_impl.databinding.EventSectionLiquiditySwapBinding
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.EventUiModel

class HistoryAdapter(
    private val debounceClickHandler: DebounceClickHandler,
    private val clickListener: (String) -> Unit
) :
    PagingDataAdapter<EventUiModel, EventItemViewHolder>(EventUiModelDiffCallback) {

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is EventUiModel.EventTimeSeparatorUiModel -> R.layout.event_section_header
            is EventUiModel.EventTxUiModel.EventTransferUiModel -> R.layout.event_section_item
            is EventUiModel.EventTxUiModel.EventLiquiditySwapUiModel -> R.layout.event_section_liquidity_swap
            else -> throw IllegalStateException("Unknown view type ${getItem(position)?.javaClass?.simpleName}")
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): EventItemViewHolder {
        return when (viewType) {
            R.layout.event_section_header -> EventItemViewHolder.EventTimeSeparatorViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.event_section_header, parent, false)
            )
            R.layout.event_section_liquidity_swap -> EventItemViewHolder.EventLiquiditySwapViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.event_section_liquidity_swap, parent, false)
            )
            else -> EventItemViewHolder.EventTransactionViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.event_section_item, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: EventItemViewHolder, position: Int) {
        val eventUiModel = getItem(position) ?: return
        if (holder is EventItemViewHolder.EventTimeSeparatorViewHolder && eventUiModel is EventUiModel.EventTimeSeparatorUiModel) {
            holder.bind(eventUiModel)
        } else if (holder is EventItemViewHolder.EventTransactionViewHolder && eventUiModel is EventUiModel.EventTxUiModel.EventTransferUiModel) {
            holder.bind(eventUiModel, debounceClickHandler, clickListener)
        } else if (holder is EventItemViewHolder.EventLiquiditySwapViewHolder && eventUiModel is EventUiModel.EventTxUiModel.EventLiquiditySwapUiModel) {
            holder.bind(eventUiModel, debounceClickHandler, clickListener)
        }
    }
}

object EventUiModelDiffCallback : DiffUtil.ItemCallback<EventUiModel>() {
    override fun areContentsTheSame(oldItem: EventUiModel, newItem: EventUiModel): Boolean {
        return oldItem == newItem
    }

    override fun areItemsTheSame(oldItem: EventUiModel, newItem: EventUiModel): Boolean {
        val isSameEventItem =
            oldItem is EventUiModel.EventTxUiModel.EventTransferUiModel && newItem is EventUiModel.EventTxUiModel.EventTransferUiModel && oldItem.id == newItem.id
        val isSameSwapItem =
            oldItem is EventUiModel.EventTxUiModel.EventLiquiditySwapUiModel && newItem is EventUiModel.EventTxUiModel.EventLiquiditySwapUiModel && oldItem.txHash == newItem.txHash
        val isSameSeparatorItem =
            oldItem is EventUiModel.EventTimeSeparatorUiModel && newItem is EventUiModel.EventTimeSeparatorUiModel && oldItem.title == newItem.title
        return isSameEventItem || isSameSeparatorItem || isSameSwapItem
    }
}

sealed class EventItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    class EventLiquiditySwapViewHolder(view: View) : EventItemViewHolder(view) {

        private val viewBinding = EventSectionLiquiditySwapBinding.bind(view)
        private val rotateAnimation: AnimationSet = ViewAnimations.rotateAnimation
        fun bind(
            soraTransaction: EventUiModel.EventTxUiModel.EventLiquiditySwapUiModel,
            debounceClickHandler: DebounceClickHandler,
            itemClickedListener: (String) -> Unit
        ) {
            viewBinding.rootEvent.setDebouncedClickListener(debounceClickHandler) {
                itemClickedListener.invoke(soraTransaction.txHash)
            }
            if (soraTransaction.success == false) {
                viewBinding.tvAmountFrom.hide()
                viewBinding.tvAmountTo.hide()
                viewBinding.tvFullAmountTo.hide()
                viewBinding.tvFailed.show()
            } else {
                viewBinding.tvAmountFrom.show()
                viewBinding.tvAmountTo.show()
                viewBinding.tvFullAmountTo.show()
                viewBinding.tvFailed.hide()
                viewBinding.tvAmountFrom.text = soraTransaction.amountFrom
                viewBinding.tvAmountTo.text = soraTransaction.amountTo
                viewBinding.tvFullAmountTo.text = soraTransaction.amountFullTo
            }
            viewBinding.eventStatusIconImageView.setImageResource(soraTransaction.iconFrom)
            viewBinding.eventStatusIconImageView2.setImageResource(soraTransaction.iconTo)
            viewBinding.eventStatusIconImageViewSp.showOrHide(soraTransaction.pending)
            viewBinding.eventStatusIconImageViewSp.doAnimation(
                soraTransaction.pending,
                rotateAnimation
            )
        }
    }

    class EventTransactionViewHolder(containerView: View) : EventItemViewHolder(containerView) {

        private val viewBinding = EventSectionItemBinding.bind(containerView)
        private val rotateAnimation: AnimationSet = ViewAnimations.rotateAnimation

        fun bind(
            soraTransaction: EventUiModel.EventTxUiModel.EventTransferUiModel,
            debounceClickHandler: DebounceClickHandler,
            itemClickedListener: (String) -> Unit
        ) {
            viewBinding.rootEvent.setDebouncedClickListener(debounceClickHandler) {
                itemClickedListener(soraTransaction.id)
            }

            viewBinding.eventItemDateTextView.text = soraTransaction.title.truncateUserAddress()

            if (soraTransaction.amountFormatted.isNotEmpty()) {
                viewBinding.eventItemFailedTextView.gone()
                viewBinding.eventItemAmountTextView.show()
                viewBinding.eventItemAmountFull.show()
                if (soraTransaction.isIncoming) {
                    viewBinding.eventItemTitleTextView.setText(R.string.common_receive)
                    val amountText = "+ ${soraTransaction.amountFormatted}"
                    viewBinding.eventItemAmountTextView.setTextColor(
                        MaterialColors.getColor(
                            viewBinding.eventItemAmountTextView,
                            R.attr.statusSuccess
                        )
                    )
                    viewBinding.eventItemAmountTextView.text = amountText
                    viewBinding.eventItemAmountFull.text =
                        "+ %s".format(soraTransaction.amountFullFormatted)
                } else {
                    viewBinding.eventItemTitleTextView.setText(R.string.common_send)
                    val amountText = "− ${soraTransaction.amountFormatted}"
                    viewBinding.eventItemAmountTextView.setTextColor(
                        MaterialColors.getColor(
                            viewBinding.eventItemAmountTextView,
                            R.attr.contentPrimary
                        )
                    )
                    viewBinding.eventItemAmountTextView.text = amountText
                    viewBinding.eventItemAmountFull.text =
                        "- %s".format(soraTransaction.amountFullFormatted)
                }
            } else {
                viewBinding.eventItemFailedTextView.show()
                viewBinding.eventItemAmountTextView.gone()
                viewBinding.eventItemAmountFull.gone()
            }

            viewBinding.eventStatusIconImageView.setImageResource(soraTransaction.statusIconResource)
            viewBinding.eventStatusIconImageViewSp.showOrHide(soraTransaction.pending)
            viewBinding.eventStatusIconImageViewSp.doAnimation(
                soraTransaction.pending,
                rotateAnimation
            )
        }
    }

    class EventTimeSeparatorViewHolder(containerView: View) : EventItemViewHolder(containerView) {
        private val viewBinding = EventSectionHeaderBinding.bind(containerView)

        fun bind(item: EventUiModel.EventTimeSeparatorUiModel) {
            viewBinding.eventItemDayTextView.text = item.title
        }
    }
}
