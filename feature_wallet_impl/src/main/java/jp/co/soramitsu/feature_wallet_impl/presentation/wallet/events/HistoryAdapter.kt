/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.events

import android.graphics.drawable.Animatable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.presentation.AssetBalanceData
import jp.co.soramitsu.common.presentation.AssetBalanceStyle
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.getColorAttr
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.hide
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.common.util.ext.setBalance
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.common.util.ext.showOrHide
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionStatus
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.EventSectionHeaderBinding
import jp.co.soramitsu.feature_wallet_impl.databinding.EventSectionItemInBinding
import jp.co.soramitsu.feature_wallet_impl.databinding.EventSectionItemOutBinding
import jp.co.soramitsu.feature_wallet_impl.databinding.EventSectionItemReferralBinding
import jp.co.soramitsu.feature_wallet_impl.databinding.EventSectionLiquidityAddBinding
import jp.co.soramitsu.feature_wallet_impl.databinding.EventSectionLiquiditySwapBinding
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.EventUiModel

class HistoryAdapter(
    private val debounceClickHandler: DebounceClickHandler,
    private val clickListener: (String) -> Unit
) :
    RecyclerView.Adapter<EventItemViewHolder>() {

    private val items = mutableListOf<EventUiModel>()

    fun update(newEvents: List<EventUiModel>) {
        val result = DiffUtil.calculateDiff(EventUiModelDiffCallback(items, newEvents))
        items.clear()
        items.addAll(newEvents)
        result.dispatchUpdatesTo(this)
    }

    fun itemsCount() = items.size

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is EventUiModel.EventTimeSeparatorUiModel -> R.layout.event_section_header
            is EventUiModel.EventTxUiModel.EventTransferInUiModel -> R.layout.event_section_item_in
            is EventUiModel.EventTxUiModel.EventTransferOutUiModel -> R.layout.event_section_item_out
            is EventUiModel.EventTxUiModel.EventLiquiditySwapUiModel -> R.layout.event_section_liquidity_swap
            is EventUiModel.EventTxUiModel.EventLiquidityAddUiModel -> R.layout.event_section_liquidity_add
            is EventUiModel.EventUiLoading -> R.layout.event_section_load_state
            is EventUiModel.EventTxUiModel.EventReferralProgramUiModel -> R.layout.event_section_item_referral
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
            R.layout.event_section_liquidity_add -> EventItemViewHolder.EventLiquidityAddViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.event_section_liquidity_add, parent, false)
            )
            R.layout.event_section_item_out -> EventItemViewHolder.EventTransactionOutViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.event_section_item_out, parent, false)
            )
            R.layout.event_section_item_in -> EventItemViewHolder.EventTransactionInViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.event_section_item_in, parent, false)
            )
            R.layout.event_section_item_referral -> EventItemViewHolder.EventTransactionReferralViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.event_section_item_referral, parent, false)
            )
            R.layout.event_section_load_state -> EventItemViewHolder.EventItemLoadingViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.event_section_load_state, parent, false)
            )
            else -> throw IllegalArgumentException("Unsupported view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: EventItemViewHolder, position: Int) {
        when (holder) {
            is EventItemViewHolder.EventTimeSeparatorViewHolder -> {
                holder.bind(items[position])
            }
            is EventItemViewHolder.EventTransactionInViewHolder -> {
                holder.bind(
                    items[position] as EventUiModel.EventTxUiModel.EventTransferInUiModel,
                    debounceClickHandler,
                    clickListener
                )
            }
            is EventItemViewHolder.EventTransactionOutViewHolder -> {
                holder.bind(
                    items[position] as EventUiModel.EventTxUiModel.EventTransferOutUiModel,
                    debounceClickHandler,
                    clickListener
                )
            }
            is EventItemViewHolder.EventTransactionReferralViewHolder -> {
                holder.bind(
                    items[position] as EventUiModel.EventTxUiModel.EventReferralProgramUiModel,
                    debounceClickHandler,
                    clickListener,
                )
            }
            is EventItemViewHolder.EventLiquiditySwapViewHolder -> {
                holder.bind(
                    items[position] as EventUiModel.EventTxUiModel.EventLiquiditySwapUiModel,
                    debounceClickHandler,
                    clickListener
                )
            }
            is EventItemViewHolder.EventLiquidityAddViewHolder -> {
                holder.bind(
                    items[position] as EventUiModel.EventTxUiModel.EventLiquidityAddUiModel,
                    debounceClickHandler,
                    clickListener
                )
            }
            else -> {}
        }
    }
}

private class EventUiModelDiffCallback(val old: List<EventUiModel>, val new: List<EventUiModel>) :
    DiffUtil.Callback() {
    override fun getNewListSize(): Int = new.size
    override fun getOldListSize(): Int = old.size
    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return old[oldItemPosition] == new[newItemPosition]
    }

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = old[oldItemPosition]
        val newItem = new[newItemPosition]
        if (oldItem is EventUiModel.EventUiLoading && newItem is EventUiModel.EventUiLoading) return true
        if (oldItem is EventUiModel.EventTimeSeparatorUiModel && newItem is EventUiModel.EventTimeSeparatorUiModel && oldItem.title == newItem.title) return true
        return oldItem is EventUiModel.EventTxUiModel && newItem is EventUiModel.EventTxUiModel && oldItem.txHash == newItem.txHash
    }
}

private val positiveBalanceStyle = AssetBalanceStyle(
    intStyle = R.style.TextAppearance_Soramitsu_Neu_Bold_14,
    decStyle = R.style.TextAppearance_Soramitsu_Neu_Semibold_11,
    color = R.attr.balanceColorPositive,
    tickerStyle = R.style.TextAppearance_Soramitsu_Neu_Bold_15,
    tickerColor = R.attr.balanceColorDefault
)

private val negativeBalanceStyle = AssetBalanceStyle(
    intStyle = R.style.TextAppearance_Soramitsu_Neu_Bold_14,
    decStyle = R.style.TextAppearance_Soramitsu_Neu_Semibold_11,
    color = R.attr.balanceColorNegative,
    tickerStyle = R.style.TextAppearance_Soramitsu_Neu_Bold_15,
    tickerColor = R.attr.balanceColorDefault
)

private val neutralBalanceStyle = AssetBalanceStyle(
    intStyle = R.style.TextAppearance_Soramitsu_Neu_Bold_14,
    decStyle = R.style.TextAppearance_Soramitsu_Neu_Semibold_11,
    tickerStyle = R.style.TextAppearance_Soramitsu_Neu_Bold_15
)

sealed class EventItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    class EventItemLoadingViewHolder(view: View) : EventItemViewHolder(view)

    class EventLiquidityAddViewHolder(view: View) : EventItemViewHolder(view) {

        private val viewBinding = EventSectionLiquidityAddBinding.bind(view)
        fun bind(
            soraTransaction: EventUiModel.EventTxUiModel.EventLiquidityAddUiModel,
            debounceClickHandler: DebounceClickHandler,
            itemClickedListener: (String) -> Unit
        ) {
            viewBinding.rootEvent.setDebouncedClickListener(debounceClickHandler) {
                itemClickedListener.invoke(soraTransaction.txHash)
            }
            val (title, prefix, balanceStyle) = if (soraTransaction.add)
                Triple(
                    R.string.common_add,
                    "-",
                    neutralBalanceStyle
                ) else Triple(R.string.common_remove, "+", positiveBalanceStyle)
            viewBinding.eventItemTitleTextView.setText(title)
            viewBinding.tvAddAmount1.showOrHide(soraTransaction.status != TransactionStatus.REJECTED)
            viewBinding.tvAddAmount2.showOrHide(soraTransaction.status != TransactionStatus.REJECTED)
            viewBinding.tvFailed.showOrHide(soraTransaction.status == TransactionStatus.REJECTED)
            if (soraTransaction.status != TransactionStatus.REJECTED) {
                viewBinding.tvAddAmount1.setBalance(
                    AssetBalanceData(
                        amount = "$prefix${soraTransaction.amount1.first}",
                        ticker = soraTransaction.amount1.second,
                        style = balanceStyle
                    )
                )
                viewBinding.tvAddAmount2.setBalance(
                    AssetBalanceData(
                        amount = "$prefix${soraTransaction.amount2.first}",
                        ticker = soraTransaction.amount2.second,
                        style = balanceStyle
                    )
                )
            }
            viewBinding.tvHistorySwapDate.text = soraTransaction.dateTime
            viewBinding.eventStatusIconImageView2.setImageResource(soraTransaction.icon1)
            viewBinding.eventStatusIconImageView3.setImageResource(soraTransaction.icon2)
            if (soraTransaction.status == TransactionStatus.PENDING) {
                viewBinding.eventStatusIconImageView.hide()
                viewBinding.eventStatusIconImageViewSp.show()
                viewBinding.eventStatusIconImageViewSp.drawable.safeCast<Animatable>()?.start()
            } else {
                viewBinding.eventStatusIconImageView.show()
                viewBinding.eventStatusIconImageViewSp.hide()
            }
        }
    }

    class EventLiquiditySwapViewHolder(view: View) : EventItemViewHolder(view) {

        private val viewBinding = EventSectionLiquiditySwapBinding.bind(view)
        fun bind(
            soraTransaction: EventUiModel.EventTxUiModel.EventLiquiditySwapUiModel,
            debounceClickHandler: DebounceClickHandler,
            itemClickedListener: (String) -> Unit
        ) {
            viewBinding.rootEvent.setDebouncedClickListener(debounceClickHandler) {
                itemClickedListener.invoke(soraTransaction.txHash)
            }
            if (soraTransaction.status == TransactionStatus.REJECTED) {
                viewBinding.tvAmountFrom.hide()
                viewBinding.tvAmountTo.hide()
                viewBinding.tvFailed.show()
            } else {
                viewBinding.tvAmountFrom.show()
                viewBinding.tvAmountTo.show()
                viewBinding.tvFailed.hide()
                viewBinding.tvAmountFrom.setBalance(
                    AssetBalanceData(
                        amount = soraTransaction.amountFrom.first,
                        ticker = soraTransaction.amountFrom.second,
                        style = neutralBalanceStyle
                    )
                )
                viewBinding.tvAmountTo.setBalance(
                    AssetBalanceData(
                        amount = soraTransaction.amountTo.first,
                        ticker = soraTransaction.amountTo.second,
                        style = positiveBalanceStyle
                    )
                )
            }
            viewBinding.tvHistorySwapDate.text = soraTransaction.dateTime
            viewBinding.eventStatusIconImageView2.setImageResource(soraTransaction.iconFrom)
            viewBinding.eventStatusIconImageView3.setImageResource(soraTransaction.iconTo)
            if (soraTransaction.status == TransactionStatus.PENDING) {
                viewBinding.eventStatusIconImageView.hide()
                viewBinding.eventStatusIconImageViewSp.show()
                viewBinding.eventStatusIconImageViewSp.drawable.safeCast<Animatable>()?.start()
            } else {
                viewBinding.eventStatusIconImageView.show()
                viewBinding.eventStatusIconImageViewSp.hide()
            }
        }
    }

    class EventTransactionInViewHolder(containerView: View) : EventItemViewHolder(containerView) {

        private val viewBinding = EventSectionItemInBinding.bind(containerView)

        fun bind(
            soraTransaction: EventUiModel.EventTxUiModel.EventTransferInUiModel,
            debounceClickHandler: DebounceClickHandler,
            itemClickedListener: (String) -> Unit
        ) {
            viewBinding.rootEvent.setDebouncedClickListener(debounceClickHandler) {
                itemClickedListener(soraTransaction.txHash)
            }

            viewBinding.eventItemDateTextView.text = soraTransaction.peerAddress
            viewBinding.tvHistoryTransferDate.text = soraTransaction.dateTime
            viewBinding.ivHistoryTransferToken.setImageResource(soraTransaction.tokenIcon)
            if (soraTransaction.status == TransactionStatus.REJECTED) {
                viewBinding.eventItemFailedTextView.show()
                viewBinding.eventItemAmountTextView.gone()
            } else {
                viewBinding.eventItemFailedTextView.gone()
                viewBinding.eventItemAmountTextView.show()
                viewBinding.eventItemAmountTextView.setBalance(
                    AssetBalanceData(
                        amount = soraTransaction.amountFormatted.first,
                        ticker = soraTransaction.amountFormatted.second,
                        style = positiveBalanceStyle
                    )
                )
            }
            if (soraTransaction.status == TransactionStatus.PENDING) {
                viewBinding.eventStatusIconImageView.hide()
                viewBinding.eventStatusIconImageViewSp.show()
                viewBinding.eventStatusIconImageViewSp.drawable.safeCast<Animatable>()?.start()
            } else {
                viewBinding.eventStatusIconImageView.show()
                viewBinding.eventStatusIconImageViewSp.hide()
            }
        }
    }

    class EventTransactionReferralViewHolder(containerView: View) :
        EventItemViewHolder(containerView) {

        private val viewBinding = EventSectionItemReferralBinding.bind(containerView)

        fun bind(
            soraTransaction: EventUiModel.EventTxUiModel.EventReferralProgramUiModel,
            debounceClickHandler: DebounceClickHandler,
            itemClickedListener: (String) -> Unit
        ) {
            viewBinding.rootEvent.setDebouncedClickListener(debounceClickHandler) {
                itemClickedListener(soraTransaction.txHash)
            }

            viewBinding.eventStatusIconImageView.setImageResource(soraTransaction.tokenIcon)
            viewBinding.eventItemDateTextView.setText(soraTransaction.description)
            viewBinding.tvHistoryTransferDate.text = soraTransaction.dateTime
            if (soraTransaction.status == TransactionStatus.REJECTED) {
                viewBinding.eventItemFailedTextView.show()
                viewBinding.eventItemAmountTextView.gone()
            } else {
                viewBinding.eventItemFailedTextView.gone()
                viewBinding.eventItemAmountTextView.show()
                if (soraTransaction.amountFormatted != null) {
                    viewBinding.tvReferralAddress.gone()
                    viewBinding.eventItemAmountTextView.setBalance(
                        AssetBalanceData(
                            amount = soraTransaction.amountFormatted.first,
                            ticker = soraTransaction.amountFormatted.second,
                            style = if (soraTransaction.plusAmount) positiveBalanceStyle else neutralBalanceStyle
                        )
                    )
                } else if (soraTransaction.referral != null) {
                    viewBinding.tvReferralAddress.show()
                    viewBinding.tvReferralAddress.text = soraTransaction.referral.second
                    val text = SpannableString(soraTransaction.referral.first)
                    text.setSpan(
                        ForegroundColorSpan(
                            viewBinding.eventItemAmountTextView.getColorAttr(
                                if (soraTransaction.plusAmount)
                                    neutralBalanceStyle.color
                                else
                                    positiveBalanceStyle.color
                            )
                        ),
                        0,
                        text.length,
                        SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    viewBinding.eventItemAmountTextView.setText(text, TextView.BufferType.SPANNABLE)
                }
            }
            if (soraTransaction.status == TransactionStatus.PENDING) {
                viewBinding.eventStatusIconImageView.hide()
                viewBinding.eventStatusIconImageViewSp.show()
                viewBinding.eventStatusIconImageViewSp.drawable.safeCast<Animatable>()?.start()
            } else {
                viewBinding.eventStatusIconImageView.show()
                viewBinding.eventStatusIconImageViewSp.hide()
            }
        }
    }

    class EventTransactionOutViewHolder(containerView: View) : EventItemViewHolder(containerView) {

        private val viewBinding = EventSectionItemOutBinding.bind(containerView)

        fun bind(
            soraTransaction: EventUiModel.EventTxUiModel.EventTransferOutUiModel,
            debounceClickHandler: DebounceClickHandler,
            itemClickedListener: (String) -> Unit
        ) {
            viewBinding.rootEvent.setDebouncedClickListener(debounceClickHandler) {
                itemClickedListener(soraTransaction.txHash)
            }

            viewBinding.eventItemDateTextView.text = soraTransaction.peerAddress
            viewBinding.tvHistoryTransferDate.text = soraTransaction.dateTime
            viewBinding.ivHistoryTransferToken.setImageResource(soraTransaction.tokenIcon)
            if (soraTransaction.status == TransactionStatus.REJECTED) {
                viewBinding.eventItemFailedTextView.show()
                viewBinding.eventItemAmountTextView.gone()
            } else {
                viewBinding.eventItemFailedTextView.gone()
                viewBinding.eventItemAmountTextView.show()
                viewBinding.eventItemAmountTextView.setBalance(
                    AssetBalanceData(
                        amount = soraTransaction.amountFormatted.first,
                        ticker = soraTransaction.amountFormatted.second,
                        style = negativeBalanceStyle
                    )
                )
            }
            if (soraTransaction.status == TransactionStatus.PENDING) {
                viewBinding.eventStatusIconImageView.hide()
                viewBinding.eventStatusIconImageViewSp.show()
                viewBinding.eventStatusIconImageViewSp.drawable.safeCast<Animatable>()?.start()
            } else {
                viewBinding.eventStatusIconImageView.show()
                viewBinding.eventStatusIconImageViewSp.hide()
            }
        }
    }

    class EventTimeSeparatorViewHolder(containerView: View) : EventItemViewHolder(containerView) {
        private val viewBinding = EventSectionHeaderBinding.bind(containerView)

        fun bind(item: EventUiModel?) {
            item?.safeCast<EventUiModel.EventTimeSeparatorUiModel>()?.let {
                viewBinding.eventItemDayTextView.text = it.title
            }
        }
    }
}
