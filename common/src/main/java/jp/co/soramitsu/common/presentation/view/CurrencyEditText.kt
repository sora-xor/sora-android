/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.view

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import jp.co.soramitsu.common.domain.SuspendableProperty
import jp.co.soramitsu.common.util.DECIMAL_SEPARATOR
import jp.co.soramitsu.common.util.GROUPING_SEPARATOR
import jp.co.soramitsu.common.util.ext.decimalPartSized
import jp.co.soramitsu.common.view.ViewHelper
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import java.math.BigDecimal
import java.text.DecimalFormat
import java.util.Locale

class CurrencyEditText(context: Context, attrs: AttributeSet) : AppCompatEditText(context, attrs) {

    private val zero = "0"
    private val doubleZero = "00"

    private val state = SuspendableProperty<BigDecimal?>(1)
    private var lastEdited = ""

    val integerPartLength: Int = 27
    var decimalPartLength: Int = 2

    private val textWatcherListener = object : TextWatcher {

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

            if (newAmount.startsWith(DECIMAL_SEPARATOR)) {
                newAmount = "$zero$newAmount"
                setValues(newAmount) { newAmount.length }
                return
            }

            val newAmountComponents = newAmount.trim().split(DECIMAL_SEPARATOR)

            if (newAmountComponents.isNotEmpty() &&
                newAmountComponents[0].length > integerPartLength &&
                !newAmount.endsWith(DECIMAL_SEPARATOR)
            ) {
                setValues(lastEdited) { getSelection(start, count, 0) }
                return
            }

            if (newAmountComponents.size > 2 || newAmountComponents.size == 2 && newAmountComponents[1].length > decimalPartLength) {
                setValues(lastEdited) { getSelection(start, count, 0) }
                return
            }

            val formattedString = format(newAmount)
            val lengthDifference = formattedString.length - newAmount.length
            setValues(formattedString) {
                getSelection(
                    start,
                    count,
                    lengthDifference
                )
            }
        }

        private fun setValues(value: String, selectionIndex: () -> Int) {
            setText(value.decimalPartSized(DECIMAL_SEPARATOR.toString()))
            state.set(getBigDecimal(value))
            setSelection(selectionIndex.invoke().coerceAtLeast(0))
            addTextChangedListener(this)
        }

        private fun getSelection(start: Int, count: Int, groupingDiff: Int): Int {
            return if ((start + count + groupingDiff) > length()) {
                length()
            } else {
                start + count + groupingDiff
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addTextChangedListener(textWatcherListener)
    }

    override fun onDetachedFromWindow() {
        removeTextChangedListener(textWatcherListener)
        super.onDetachedFromWindow()
    }

    init {
        isSaveEnabled = false
    }

    private fun format(amountString: String): String {
        val formatter = DecimalFormat()
        val decimalFormatSymbols = formatter.decimalFormatSymbols
        decimalFormatSymbols.groupingSeparator = GROUPING_SEPARATOR
        decimalFormatSymbols.decimalSeparator = DECIMAL_SEPARATOR
        formatter.decimalFormatSymbols = decimalFormatSymbols
        formatter.maximumFractionDigits = decimalPartLength
        var formattedString = formatter.format(getBigDecimal(amountString))

        when {
            amountString.endsWith(DECIMAL_SEPARATOR) -> formattedString += DECIMAL_SEPARATOR
            amountString.endsWith("$DECIMAL_SEPARATOR$zero") -> formattedString += "$DECIMAL_SEPARATOR$zero"
            amountString.endsWith("$DECIMAL_SEPARATOR$doubleZero") -> {
                formattedString += "$DECIMAL_SEPARATOR$doubleZero"
            }
            else -> {
                val parts = amountString.split(DECIMAL_SEPARATOR)
                if (parts.size == 2) {
                    when {
                        parts[1].endsWith(zero) -> formattedString = amountString
                    }
                }
            }
        }

        return formattedString
    }

    private fun getBigDecimal(num: String): BigDecimal {
        return BigDecimal(num.replace(GROUPING_SEPARATOR.toString(), ""))
    }

    private fun getBigDecimal(): BigDecimal? {
        if (text.isNullOrEmpty() || text?.first() == '.')
            return null
        return getBigDecimal(text.toString())
    }

    fun asFlowCurrency() = state.observe().filterNotNull().debounce(ViewHelper.debounce).distinctUntilChanged()
    fun asFlowCurrency2() = state.observe().filterNotNull()

    fun setValue(s: String) {
        if (s == text.toString()) return
        if (getBigDecimal() == getBigDecimal(s)) return
        removeTextChangedListener(textWatcherListener)
        val text = s.decimalPartSized(DECIMAL_SEPARATOR.toString())
        setText(text)

        if (Locale.getDefault().toString() == "ar")
            setSelection(text.length)

        addTextChangedListener(textWatcherListener)
    }
}
