/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.pool

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.pool.model.PoolModel
import java.math.BigDecimal

class PoolAdapter(
    private val debounceClickHandler: DebounceClickHandler,
    private val onCollapseTriggered: () -> Unit,
    private val onAddLiquidity: (Token, Token) -> Unit,
    private val onRemoveLiquidity: (Token, Token) -> Unit,
    private val numbersFormatter: NumbersFormatter
) : ListAdapter<PoolModel, PoolViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): PoolViewHolder {
        return PoolViewHolder(
            LayoutInflater.from(viewGroup.context).inflate(R.layout.item_pool, viewGroup, false),
            onCollapseTriggered,
            numbersFormatter
        )
    }

    override fun onBindViewHolder(poolViewHolder: PoolViewHolder, position: Int) {
        poolViewHolder.bind(getItem(position), debounceClickHandler, onAddLiquidity, onRemoveLiquidity)
    }
}

class PoolViewHolder(
    itemView: View,
    val onCollapseTriggered: () -> Unit,
    val numbersFormatter: NumbersFormatter
) : RecyclerView.ViewHolder(itemView) {

    private companion object {
        private const val POOL_SHARE_PRECISION = 8
    }

    private val chevronIconButton: ImageView = itemView.findViewById(R.id.chevronIconButton)
    private val token1ImageView: ImageView = itemView.findViewById(R.id.token1Icon)
    private val token2ImageView: ImageView = itemView.findViewById(R.id.token2Icon)
    private val poolToken: TextView = itemView.findViewById(R.id.poolText)

    private val expandedGroup: Group = itemView.findViewById(R.id.grPooledItemExpandedState)

    private val shareValueText: TextView = itemView.findViewById(R.id.shareValue)
    private val token1PooledValue: TextView = itemView.findViewById(R.id.token1PooledValue)
    private val token1PooledTitle: TextView = itemView.findViewById(R.id.token1Pooled)
    private val token2PooledTitle: TextView = itemView.findViewById(R.id.token2Pooled)
    private val token2PooledValue: TextView = itemView.findViewById(R.id.token2PooledValue)
    private val apyTitle: TextView = itemView.findViewById(R.id.apyText)
    private val apyValue: TextView = itemView.findViewById(R.id.apyValue)
    private val apyDivider: View = itemView.findViewById(R.id.divider1_2)

    private val addButton: Button = itemView.findViewById(R.id.addButton)
    private val removeButton: Button = itemView.findViewById(R.id.removeButton)

    private val selectablePartWrapper: ConstraintLayout = itemView.findViewById(R.id.selectablePartWrapper)

    private fun collapseView() {
        expandedGroup.gone()
        apyTitle.gone()
        apyValue.gone()
        apyDivider.gone()
        chevronIconButton.tag = false
        chevronIconButton.setImageResource(R.drawable.ic_neu_chevron_down)
    }

    private fun expandView() {
        expandedGroup.show()
        chevronIconButton.tag = true
        chevronIconButton.setImageResource(R.drawable.ic_neu_chevron_up)

        if ((apyTitle.tag as Boolean)) {
            apyTitle.show()
            apyValue.show()
            apyDivider.show()
        }
    }

    private fun handleClick() {
        if (chevronIconButton.tag == true) {
            collapseView()
            onCollapseTriggered()
        } else {
            expandView()
        }
    }

    fun bind(
        pool: PoolModel,
        debounceClickHandler: DebounceClickHandler,
        onAddLiquidity: (Token, Token) -> Unit,
        onRemoveLiquidity: (Token, Token) -> Unit
    ) {
        token1ImageView.setImageResource(pool.tokenFrom.icon)
        token2ImageView.setImageResource(pool.tokenTo.icon)

        val poolText = "${pool.tokenFrom.symbol}-${pool.tokenTo.symbol}"
        poolToken.text = poolText

        shareValueText.text = "${numbersFormatter.formatBigDecimal(BigDecimal(pool.poolShare))}%"

        token1PooledTitle.text =
            "${pool.tokenFrom.symbol} ${itemView.resources.getString(R.string.pool_token_pooled)}"
        token1PooledValue.text =
            numbersFormatter.formatBigDecimal(pool.tokenFromPooled, POOL_SHARE_PRECISION)

        token2PooledTitle.text =
            "${pool.tokenTo.symbol} ${itemView.resources.getString(R.string.pool_token_pooled)}"
        token2PooledValue.text =
            numbersFormatter.formatBigDecimal(pool.tokenToPooled, POOL_SHARE_PRECISION)

        if (pool.strategicBonusApy != null) {
            apyValue.text = "${
            numbersFormatter.formatBigDecimal(
                pool.strategicBonusApy,
                POOL_SHARE_PRECISION
            )}%"
            apyTitle.tag = true
        } else {
            apyTitle.tag = false
            apyValue.gone()
            apyTitle.gone()
            apyDivider.gone()
        }

        selectablePartWrapper.setDebouncedClickListener(debounceClickHandler) {
            handleClick()
        }

        chevronIconButton.setDebouncedClickListener(debounceClickHandler) {
            handleClick()
        }

        addButton.setDebouncedClickListener(debounceClickHandler) {
            onAddLiquidity(pool.tokenFrom, pool.tokenTo)
        }

        removeButton.setDebouncedClickListener(debounceClickHandler) {
            onRemoveLiquidity(pool.tokenFrom, pool.tokenTo)
        }
    }
}

object DiffCallback : DiffUtil.ItemCallback<PoolModel>() {
    override fun areItemsTheSame(oldItem: PoolModel, newItem: PoolModel): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: PoolModel, newItem: PoolModel): Boolean {
        return oldItem == newItem
    }
}
