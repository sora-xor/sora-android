package jp.co.soramitsu.common.presentation.view.slippagebottomsheet

import android.content.Context
import android.view.LayoutInflater
import androidx.core.widget.doOnTextChanged
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.databinding.BottomSheetSlippageBinding
import jp.co.soramitsu.common.util.ext.onDoneClicked
import java.math.BigDecimal

class SlippageBottomSheet(
    context: Context,
    currentSlippage: Float,
    val slippageSetted: (slippage: Float) -> Unit
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    private val MIN = BigDecimal.ZERO
    private val MAX = BigDecimal.TEN

    init {
        val binding = BottomSheetSlippageBinding.inflate(LayoutInflater.from(context), null, false)
            .also {
                setContentView(it.root)
            }

        binding.slippageToleranceInput.setText(currentSlippage.toString())

        binding.slippageToleranceInput.doOnTextChanged { text, _, _, _ ->
            if (text.toString().isNotEmpty()) {
                val slippage = text.toString().toBigDecimal()
                if (slippage < MIN) {
                    binding.slippageToleranceInput.setText(MIN.toString())
                }

                if (slippage > MAX) {
                    binding.slippageToleranceInput.setText(MAX.toString())
                }
            }
        }

        binding.slippageToleranceInput.onDoneClicked {
            if (binding.slippageToleranceInput.text.toString().isNotEmpty()) {
                slippageSetted(binding.slippageToleranceInput.text.toString().toFloat())
            }

            dismiss()
        }

        binding.firstChip.setOnClickListener {
            slippageSetted(0.1f)
            dismiss()
        }

        binding.secondChip.setOnClickListener {
            slippageSetted(0.5f)
            dismiss()
        }

        binding.lastChip.setOnClickListener {
            slippageSetted(1.0f)
            dismiss()
        }
    }
}
