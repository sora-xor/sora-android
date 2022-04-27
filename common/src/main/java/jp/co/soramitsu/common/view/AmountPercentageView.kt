/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.databinding.ViewAmountPercentageBinding

class AmountPercentageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    private companion object {
        const val PERCENT_100 = 100
        const val PERCENT_75 = 75
        const val PERCENT_50 = 50
        const val PERCENT_25 = 25
        const val PERCENTAGE_FORMAT = "%d%%"
    }

    private val binding = ViewAmountPercentageBinding.inflate(LayoutInflater.from(context), this)

    private var optionClickListener: ((Int) -> Unit)? = null
    private var doneClickListener: (() -> Unit)? = null

    init {
        setUpOptions()

        binding.doneButton.setOnClickListener {
            doneClickListener?.invoke()
        }

        setBackgroundColor(ContextCompat.getColor(context, R.color.neu_color_100))
    }

    private fun setUpOptions() {
        binding.percent100.run {
            text = PERCENTAGE_FORMAT.format(PERCENT_100)
            setOnClickListener {
                optionClickListener?.invoke(PERCENT_100)
            }
        }

        binding.percent75.run {
            text = PERCENTAGE_FORMAT.format(PERCENT_75)
            setOnClickListener {
                optionClickListener?.invoke(PERCENT_75)
            }
        }

        binding.percent50.run {
            text = PERCENTAGE_FORMAT.format(PERCENT_50)
            setOnClickListener {
                optionClickListener?.invoke(PERCENT_50)
            }
        }

        binding.percent25.run {
            text = PERCENTAGE_FORMAT.format(PERCENT_25)
            setOnClickListener {
                optionClickListener?.invoke(PERCENT_25)
            }
        }
    }

    fun setOnOptionClickListener(listener: (Int) -> Unit) {
        optionClickListener = listener
    }

    fun setOnDoneButtonClickListener(listener: () -> Unit) {
        doneClickListener = listener
    }
}
