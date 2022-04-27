/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.events

import android.graphics.drawable.Animatable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.presentation.AssetBalanceData
import jp.co.soramitsu.common.presentation.AssetBalanceStyle
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.hide
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.common.util.ext.setBalance
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.common.util.ext.showOrHide
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.EventSectionHeaderBinding
import jp.co.soramitsu.feature_wallet_impl.databinding.EventSectionItemInBinding
import jp.co.soramitsu.feature_wallet_impl.databinding.EventSectionItemOutBinding
import jp.co.soramitsu.feature_wallet_impl.databinding.EventSectionLiquidityAddBinding
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
            is EventUiModel.EventTxUiModel.EventTransferInUiModel -> R.layout.event_section_item_in
            is EventUiModel.EventTxUiModel.EventTransferOutUiModel -> R.layout.event_section_item_out
            is EventUiModel.EventTxUiModel.EventLiquiditySwapUiModel -> R.layout.event_section_liquidity_swap
            is EventUiModel.EventTxUiModel.EventLiquidityAddUiModel -> R.layout.event_section_liquidity_add
            null -> R.layout.event_section_header
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
            R.layout.event_section_liquidity_add -> EventItemViewHolder.EventLiquidityAddViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.event_section_liquidity_add, parent, false)
            )
            R.layout.event_section_item_out -> EventItemViewHolder.EventTransactionOutViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.event_section_item_out, parent, false)
            )
            else -> EventItemViewHolder.EventTransactionInViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.event_section_item_in, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: EventItemViewHolder, position: Int) {
        val eventUiModel = getItem(position)
        if (holder is EventItemViewHolder.EventTimeSeparatorViewHolder && (eventUiModel is EventUiModel.EventTimeSeparatorUiModel || eventUiModel == null)) {
            holder.bind(eventUiModel)
        } else if (holder is EventItemViewHolder.EventTransactionInViewHolder && eventUiModel is EventUiModel.EventTxUiModel.EventTransferInUiModel) {
            holder.bind(eventUiModel, debounceClickHandler, clickListener)
        } else if (holder is EventItemViewHolder.EventTransactionOutViewHolder && eventUiModel is EventUiModel.EventTxUiModel.EventTransferOutUiModel) {
            holder.bind(eventUiModel, debounceClickHandler, clickListener)
        } else if (holder is EventItemViewHolder.EventLiquiditySwapViewHolder && eventUiModel is EventUiModel.EventTxUiModel.EventLiquiditySwapUiModel) {
            holder.bind(eventUiModel, debounceClickHandler, clickListener)
        } else if (holder is EventItemViewHolder.EventLiquidityAddViewHolder && eventUiModel is EventUiModel.EventTxUiModel.EventLiquidityAddUiModel) {
            holder.bind(eventUiModel, debounceClickHandler, clickListener)
        }
    }
}

object EventUiModelDiffCallback : DiffUtil.ItemCallback<EventUiModel>() {
    override fun areContentsTheSame(oldItem: EventUiModel, newItem: EventUiModel): Boolean {
        return oldItem == newItem
    }

    override fun areItemsTheSame(oldItem: EventUiModel, newItem: EventUiModel): Boolean {
        val isSameItem =
            oldItem is EventUiModel.EventTxUiModel && newItem is EventUiModel.EventTxUiModel && oldItem.txHash == newItem.txHash
        val isSameSeparatorItem =
            oldItem is EventUiModel.EventTimeSeparatorUiModel && newItem is EventUiModel.EventTimeSeparatorUiModel && oldItem.title == newItem.title
        return isSameSeparatorItem || isSameItem
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
            viewBinding.tvAddAmount1.showOrHide(soraTransaction.success != false)
            viewBinding.tvAddAmount2.showOrHide(soraTransaction.success != false)
            viewBinding.tvFailed.showOrHide(soraTransaction.success == false)
            if (soraTransaction.success != false) {
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
            if (soraTransaction.pending) {
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
            if (soraTransaction.success == false) {
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
            if (soraTransaction.pending) {
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
            if (soraTransaction.success == false) {
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
            if (soraTransaction.pending) {
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
            if (soraTransaction.success == false) {
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
            if (soraTransaction.pending) {
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
