/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.send.gas.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.util.ext.hide
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.send.gas.model.GasEstimationItem

class SelectGasAdapter(
    private val debounceClickHandler: DebounceClickHandler,
    var selectedGasEstimationItem: GasEstimationItem.Type = GasEstimationItem.Type.REGULAR,
    private val itemClickListener: (GasEstimationItem) -> Unit
) : ListAdapter<GasEstimationItem, GasEstimationViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): GasEstimationViewHolder {
        return GasEstimationViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.item_gas, viewGroup, false))
    }

    override fun onBindViewHolder(gasEstimationViewHolder: GasEstimationViewHolder, position: Int) {
        gasEstimationViewHolder.bind(getItem(position), selectedGasEstimationItem, debounceClickHandler, itemClickListener)
    }
}

class GasEstimationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val gasValueTitle: TextView = itemView.findViewById(R.id.minerFeeTitle)
    private val gasValueText: TextView = itemView.findViewById(R.id.minerFeeText)
    private val selectedPinIcon: ImageView = itemView.findViewById(R.id.minerFeeChoosenIcon)

    fun bind(gasEstimationItem: GasEstimationItem, selectedGasEstimationItem: GasEstimationItem.Type, debounceClickHandler: DebounceClickHandler, itemClickListener: (GasEstimationItem) -> Unit) {
        with(itemView) {
            if (gasEstimationItem.type == selectedGasEstimationItem) {
                selectedPinIcon.show()
            } else {
                selectedPinIcon.hide()
            }

            gasValueTitle.text = gasEstimationItem.titleString
            gasValueText.text = gasEstimationItem.amountInEthFormatted

            setOnClickListener(DebounceClickListener(debounceClickHandler) {
                itemClickListener(gasEstimationItem)
            })
        }
    }
}

object DiffCallback : DiffUtil.ItemCallback<GasEstimationItem>() {
    override fun areItemsTheSame(oldItem: GasEstimationItem, newItem: GasEstimationItem): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: GasEstimationItem, newItem: GasEstimationItem): Boolean {
        return oldItem == newItem
    }
}