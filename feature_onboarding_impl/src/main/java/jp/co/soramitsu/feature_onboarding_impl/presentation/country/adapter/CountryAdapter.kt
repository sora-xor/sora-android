/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.country.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.feature_account_api.domain.model.Country
import jp.co.soramitsu.feature_onboarding_impl.R
import kotlinx.android.synthetic.main.item_country.view.countryNameTv

class CountryAdapter(
    private val clickListener: (Country) -> Unit
) : ListAdapter<Country, CountryViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CountryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_country, parent, false)
        return CountryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CountryViewHolder, position: Int) {
        holder.bind(getItem(position), clickListener)
    }
}

object DiffCallback : DiffUtil.ItemCallback<Country>() {

    override fun areItemsTheSame(oldItem: Country, newItem: Country): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Country, newItem: Country): Boolean {
        return oldItem == newItem
    }
}

class CountryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    fun bind(country: Country, clickListener: (Country) -> Unit) {
        with(itemView) {
            countryNameTv.text = country.name
            setOnClickListener { clickListener(country) }
        }
    }
}