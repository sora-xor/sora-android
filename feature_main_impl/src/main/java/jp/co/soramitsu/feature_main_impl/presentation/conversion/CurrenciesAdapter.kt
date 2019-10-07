/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.conversion

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jakewharton.rxbinding2.view.RxView
import io.reactivex.subjects.PublishSubject
import jp.co.soramitsu.feature_information_api.domain.model.Currency
import jp.co.soramitsu.feature_main_impl.R

class CurrenciesAdapter(
    private val context: Context,
    private val currencies: MutableList<Currency>
) : RecyclerView.Adapter<CurrenciesAdapter.CurrencyViewHolder>() {

    val itemViewClickSubject = PublishSubject.create<Currency>()

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): CurrencyViewHolder {
        val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_currency, viewGroup, false)
        return CurrencyViewHolder(v)
    }

    override fun onBindViewHolder(currencyViewHolder: CurrencyViewHolder, i: Int) {
        currencyViewHolder.bind(currencies[i])
    }

    override fun getItemCount(): Int {
        return currencies.size
    }

    fun setCurrencies(newCurrencies: List<Currency>) {
        currencies.clear()
        currencies.addAll(newCurrencies)
        this.notifyDataSetChanged()
    }

    inner class CurrencyViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private var currencyInfo: TextView = itemView.findViewById(R.id.currency_text)
        private var divider: View = itemView.findViewById(R.id.divider)

        fun bind(currency: Currency) {
            val currencyInfoText = currency.name + ", " + currency.symbol
            currencyInfo.text = currencyInfoText

            if (currency.isSelected) {
                currencyInfo.setTextColor(context.resources.getColor(R.color.lightRed))
                currencyInfo.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.icon_checked, 0)
            } else {
                currencyInfo.setTextColor(context.resources.getColor(R.color.lightBlack))
                currencyInfo.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            }

            divider.visibility = if (adapterPosition == itemCount - 1) View.GONE else View.VISIBLE

            RxView.clicks(currencyInfo)
                .map { currency }
                .subscribe(itemViewClickSubject)
        }
    }
}