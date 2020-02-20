/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.view

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import java.math.BigDecimal
import java.text.DecimalFormat

class CurrencyEditText(context: Context, attrs: AttributeSet) : AppCompatEditText(context, attrs) {

    private val zero = "0"
    private val doubleZero = "00"
    private var decimalSymbol = '.'
    private var groupingSymbol = ' '
    private val textWatcherListener = object : TextWatcher {

        var lastEdited = ""

        override fun afterTextChanged(s: Editable) {}
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            lastEdited = s.toString()
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            if (s.isEmpty()) {
                return
            }

            removeTextChangedListener(this)

            var newAmount = s.toString()

            if (newAmount.startsWith(decimalSymbol)) {
                newAmount = "$zero$newAmount"
                setText(newAmount)
                setSelection(newAmount.length)
                addTextChangedListener(this)
                return
            }

            val newAmountComponents = newAmount.split(decimalSymbol)

            if (newAmountComponents.size > 2 || newAmountComponents.size == 2 && newAmountComponents[1].length > 2) {
                setText(lastEdited)
                setSelection(getSelection(start, count, 0))
                addTextChangedListener(this)
                return
            }

            val formattedString = format(newAmount)

            setText(formattedString)

            var groupingDiff = formattedString.length - newAmount.length
            if (groupingDiff < 0) groupingDiff = 0
            setSelection(getSelection(start, count, groupingDiff))

            addTextChangedListener(this)
        }

        private fun getSelection(start: Int, count: Int, groupingDiff: Int): Int {
            return if ((start + count + groupingDiff) > length()) {
                length()
            } else {
                start + count + groupingDiff
            }
        }

        private fun format(
            amountString: String
        ): String {
            val formatter = DecimalFormat("###,###.##")
            val decimalFormatSymbols = formatter.decimalFormatSymbols
            decimalFormatSymbols.groupingSeparator = groupingSymbol
            decimalFormatSymbols.decimalSeparator = decimalSymbol
            formatter.decimalFormatSymbols = decimalFormatSymbols
            var formattedString = formatter.format(getBigDecimal(amountString))

            if (amountString.endsWith(decimalSymbol))
                formattedString += decimalSymbol
            else if (amountString.endsWith("$decimalSymbol$zero"))
                formattedString += "$decimalSymbol$zero"
            else if (amountString.endsWith("$decimalSymbol$doubleZero")) {
                formattedString += "$decimalSymbol$doubleZero"
            } else {
                val parts = amountString.split(decimalSymbol)
                if (parts.size == 2) {
                    when {
                        parts[1].endsWith(zero) -> formattedString += zero
                    }
                }
            }

            return formattedString
        }
    }

    init {
        addTextChangedListener(textWatcherListener)
    }

    private fun getBigDecimal(num: String): BigDecimal {
        return BigDecimal(num.replace(groupingSymbol.toString(), ""))
    }

    fun getBigDecimal(): BigDecimal? {
        if (text.isNullOrEmpty() || text?.first() == '.')
            return null

        return BigDecimal(text.toString().replace(groupingSymbol.toString(), ""))
    }
}