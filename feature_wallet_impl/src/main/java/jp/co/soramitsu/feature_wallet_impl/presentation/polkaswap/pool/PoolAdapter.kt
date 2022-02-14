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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.Group
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.pool.model.PoolModel

class PoolAdapter(
    private val debounceClickHandler: DebounceClickHandler,
) : ListAdapter<PoolModel, PoolViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): PoolViewHolder {
        return PoolViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.item_pool, viewGroup, false))
    }

    override fun onBindViewHolder(poolViewHolder: PoolViewHolder, position: Int) {
        poolViewHolder.bind(getItem(position), debounceClickHandler)
    }
}

class PoolViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val chevronIcon: ImageView = itemView.findViewById(R.id.chevronIcon)
    private val token1ImageView: ImageView = itemView.findViewById(R.id.token1Icon)
    private val token2ImageView: ImageView = itemView.findViewById(R.id.token2Icon)
    private val poolToken: TextView = itemView.findViewById(R.id.poolText)

    private val expandedGroup: Group = itemView.findViewById(R.id.grPooledItemExpandedState)

    private val shareValueText: TextView = itemView.findViewById(R.id.shareValue)
    private val token1PooledIcon: ImageView = itemView.findViewById(R.id.token1PooledIcon)
    private val token1PooledValue: TextView = itemView.findViewById(R.id.token1PooledValue)
    private val token1PooledTitle: TextView = itemView.findViewById(R.id.token1Pooled)
    private val token2PooledIcon: ImageView = itemView.findViewById(R.id.token2PooledIcon)
    private val token2PooledTitle: TextView = itemView.findViewById(R.id.token2Pooled)
    private val token2PooledValue: TextView = itemView.findViewById(R.id.token2PooledValue)

    private val addButton: Button = itemView.findViewById(R.id.addButton)
    private val removeButton: Button = itemView.findViewById(R.id.removeButton)

    private val selectablePartWrapper: LinearLayout = itemView.findViewById(R.id.selectablePartWrapper)

    private fun collapseView() {
        expandedGroup.gone()
        chevronIcon.tag = false
        chevronIcon.setImageResource(R.drawable.ic_chevron_down_circled_32)
    }

    private fun expandView() {
        expandedGroup.show()
        chevronIcon.tag = true
        chevronIcon.setImageResource(R.drawable.ic_chevron_up_circled_32)
    }

    fun bind(pool: PoolModel, debounceClickHandler: DebounceClickHandler) {
        token1ImageView.setImageResource(pool.token1IconResource)
        token2ImageView.setImageResource(pool.token2IconResource)

        val poolText = "${pool.token1Name}-${pool.token2Name}"
        poolToken.text = poolText

        shareValueText.text = "${pool.poolShare}%"

        token1PooledIcon.setImageResource(pool.token1IconResource)
        token1PooledTitle.text = "${pool.token1Name} ${itemView.resources.getString(R.string.pool_token_pooled)}"
        token1PooledValue.text = pool.token1Pooled

        token2PooledIcon.setImageResource(pool.token2IconResource)
        token2PooledTitle.text = "${pool.token2Name} ${itemView.resources.getString(R.string.pool_token_pooled)}"
        token2PooledValue.text = pool.token2Pooled

        selectablePartWrapper.setDebouncedClickListener(debounceClickHandler) {
            if (chevronIcon.tag == true) {
                collapseView()
            } else {
                expandView()
            }
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
