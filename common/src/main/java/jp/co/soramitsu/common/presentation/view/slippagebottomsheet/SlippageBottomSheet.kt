/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.view.slippagebottomsheet

import android.content.Context
import android.view.LayoutInflater
import androidx.core.widget.doOnTextChanged
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.databinding.BottomSheetSlippageBinding
import jp.co.soramitsu.common.util.ext.onDoneClicked
import jp.co.soramitsu.common.util.ext.openSoftKeyboard

class SlippageBottomSheet(
    context: Context,
    private val currentSlippage: Float,
    private val onSlippageSelected: (slippage: Float) -> Unit
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    private val min: Float = 0.0F
    private val minValue: Float = 0.01F
    private val max: Float = 10F
    private val minFail: Float = 0.1F
    private val maxFrontrun: Float = 5.0F

    init {
        val binding = BottomSheetSlippageBinding.inflate(LayoutInflater.from(context), null, false)
            .also {
                setContentView(it.root)
            }

        binding.slippageToleranceInputWrapper.setOnClickListener {
            context.openSoftKeyboard(binding.slippageToleranceInput)
        }

        binding.slippageToleranceInput.doOnTextChanged { text, _, _, _ ->
            if (text.toString().isNotEmpty()) {
                val slippage = checkInput(text.toString(), min) {
                    binding.slippageToleranceInput.setText(it)
                }
                when {
                    slippage >= maxFrontrun -> {
                        binding.slippageToleranceWarningTitle.setText(R.string.polkaswap_slippage_frontrun)
                        binding.slippageToleranceInput.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_neu_alert_blue_24, 0, 0, 0)
                    }
                    slippage <= minFail -> {
                        binding.slippageToleranceWarningTitle.setText(R.string.polkaswap_slippage_mayfail)
                        binding.slippageToleranceInput.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_neu_alert_blue_24, 0, 0, 0)
                    }
                    else -> {
                        binding.slippageToleranceWarningTitle.text = ""
                        binding.slippageToleranceInput.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                    }
                }
            }
        }

        binding.slippageToleranceInput.setText("$currentSlippage")

        binding.slippageToleranceInput.onDoneClicked {
            val value = binding.slippageToleranceInput.text.toString()
            if (value.isNotEmpty()) {
                onSlippageSelected(checkInput(value, minValue))
            } else {
                onSlippageSelected(currentSlippage)
            }
            dismiss()
        }

        binding.firstChip.setOnClickListener {
            onSlippageSelected(0.1f)
            dismiss()
        }

        binding.secondChip.setOnClickListener {
            onSlippageSelected(0.5f)
            dismiss()
        }

        binding.lastChip.setOnClickListener {
            onSlippageSelected(1.0f)
            dismiss()
        }
    }

    private fun checkInput(text: String, m: Float, onUpdate: ((v: String) -> Unit)? = null): Float {
        val slippage = text.let {
            if (it[0].isDigit()) it else "0$it"
        }.toFloatOrNull()
        if (slippage == null) {
            onUpdate?.invoke(currentSlippage.toString())
            return currentSlippage
        }
        if (slippage < m) {
            onUpdate?.invoke(m.toString())
            return m
        }
        if (slippage > max) {
            onUpdate?.invoke(max.toString())
            return max
        }

        return slippage
    }
}
