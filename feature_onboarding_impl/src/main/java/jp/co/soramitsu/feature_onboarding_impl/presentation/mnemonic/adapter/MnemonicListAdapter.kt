/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.mnemonic.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.presentation.mnemonic.model.MnemonicWord

class MnemonicListAdapter : ListAdapter<MnemonicWord, MnemonicListItemViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): MnemonicListItemViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_mnemonic_word, viewGroup, false)
        return MnemonicListItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: MnemonicListItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class MnemonicListItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {

    private val itemIndex: TextView = itemView.findViewById(R.id.indexText)
    private val itemWord: TextView = itemView.findViewById(R.id.wordText)

    @SuppressLint("SetTextI18n")
    fun bind(word: MnemonicWord) {
        itemIndex.text = word.index.toString()
        itemWord.text = word.word
    }
}

object DiffCallback : DiffUtil.ItemCallback<MnemonicWord>() {
    override fun areItemsTheSame(oldItem: MnemonicWord, newItem: MnemonicWord): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: MnemonicWord, newItem: MnemonicWord): Boolean {
        return oldItem == newItem
    }
}
