/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.send.gas

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.SeekBar
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.send.gas.adapter.SelectGasAdapter
import jp.co.soramitsu.feature_wallet_impl.presentation.send.gas.model.GasEstimationItem
import jp.co.soramitsu.feature_wallet_impl.presentation.send.gas.model.SelectGasDialogInitialData
import kotlinx.android.synthetic.main.gas_bottom_dialog.advancedSettingsSwitch
import kotlinx.android.synthetic.main.gas_bottom_dialog.estimatedTimeText
import kotlinx.android.synthetic.main.gas_bottom_dialog.gasLimitErrorText
import kotlinx.android.synthetic.main.gas_bottom_dialog.gasLimitInput
import kotlinx.android.synthetic.main.gas_bottom_dialog.gasLimitSeekBar
import kotlinx.android.synthetic.main.gas_bottom_dialog.gasLimitSetRecommended
import kotlinx.android.synthetic.main.gas_bottom_dialog.gasLimitTitle
import kotlinx.android.synthetic.main.gas_bottom_dialog.gasLimitWrapper
import kotlinx.android.synthetic.main.gas_bottom_dialog.gasPriceDivider
import kotlinx.android.synthetic.main.gas_bottom_dialog.gasPriceErrorText
import kotlinx.android.synthetic.main.gas_bottom_dialog.gasPriceInput
import kotlinx.android.synthetic.main.gas_bottom_dialog.gasPriceSetRecommended
import kotlinx.android.synthetic.main.gas_bottom_dialog.gasPriceTitle
import kotlinx.android.synthetic.main.gas_bottom_dialog.gasPriceWrapper
import kotlinx.android.synthetic.main.gas_bottom_dialog.gasRecyclerView
import kotlinx.android.synthetic.main.gas_bottom_dialog.minerFeeTv
import java.math.BigInteger

class GasSelectBottomSheetDialog(
    context: Context,
    debounceClickHandler: DebounceClickHandler,
    private val selectGasDialogInitialData: SelectGasDialogInitialData,
    itemClickListener: (GasEstimationItem, BigInteger) -> Unit,
    gasLimitAndPriceInputChanged: (BigInteger, BigInteger) -> Unit,
    advancedStateDisabledEvent: () -> Unit
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    private lateinit var selectGasAdapter: SelectGasAdapter

    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.gas_bottom_dialog, null))

        advancedSettingsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showAdvancedSettings()
                val gasLimit = gasLimitInput.text.toString().toBigIntegerOrNull() ?: selectGasDialogInitialData.defaultGasLimit
                val gasPrice = gasPriceInput.text.toString().toBigIntegerOrNull() ?: selectGasDialogInitialData.defaultGasPrice
                gasLimitAndPriceInputChanged(gasLimit, gasPrice)
            } else {
                advancedStateDisabledEvent()
                hideAdvancedSettings()
                gasLimitAndPriceInputChanged(selectGasDialogInitialData.gasEstimationItems[selectGasAdapter.selectedGasEstimationItem.ordinal].amount, selectGasDialogInitialData.defaultGasPrice)
            }
        }

        gasPriceInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(afterChangeEvent: Editable?) {
                val gasLimit = gasLimitInput.text.toString().toBigIntegerOrNull() ?: selectGasDialogInitialData.gasEstimationItems[selectGasAdapter.selectedGasEstimationItem.ordinal].amount
                val gasPrice = afterChangeEvent.toString().toBigIntegerOrNull() ?: BigInteger.ZERO
                gasLimitAndPriceInputChanged(gasLimit, gasPrice)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }
        })

        gasLimitInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(afterChangeEvent: Editable?) {
                gasLimitInput.tag = 1
                gasLimitInput.removeTextChangedListener(this)

                val gasPrice = gasPriceInput.text.toString().toBigIntegerOrNull() ?: selectGasDialogInitialData.defaultGasPrice
                val gasLimit = afterChangeEvent.toString().toBigIntegerOrNull() ?: BigInteger.ZERO
                gasLimitSeekBar.progress = gasLimit.toInt()
                gasLimitErrorText.gone()
                gasLimitAndPriceInputChanged(gasLimit, gasPrice)

                gasLimitInput.addTextChangedListener(this)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }
        })

        gasLimitSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (gasLimitInput.tag == 0) {
                    val gasLimit = progress.toString()
                    gasLimitInput.setText(gasLimit)
                    gasLimitInput.setSelection(gasLimitInput.length())
                }
                gasLimitInput.tag = 0
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

        gasLimitSetRecommended.setOnClickListener {
            val gasLimit = selectGasDialogInitialData.defaultGasLimit.toString()
            gasLimitInput.setText(gasLimit)
            gasLimitInput.setSelection(gasLimitInput.length())
        }

        gasPriceSetRecommended.setOnClickListener {
            val gasPrice = selectGasDialogInitialData.defaultGasPrice.toString()
            gasPriceInput.setText(gasPrice)
        }

        selectGasAdapter = SelectGasAdapter(debounceClickHandler) {
            selectGasAdapter.selectedGasEstimationItem = it.type
            submitGasInEth(it.amountInEthFormatted)
            submitEstimationTime(it.timeFormatted)
            itemClickListener(it, selectGasDialogInitialData.defaultGasPrice)
            selectGasAdapter.notifyDataSetChanged()
        }

        selectGasAdapter.submitList(selectGasDialogInitialData.gasEstimationItems)
        gasRecyclerView.adapter = selectGasAdapter

        gasLimitSeekBar.max = selectGasDialogInitialData.gasEstimationItems[GasEstimationItem.Type.FAST.ordinal].amount.toInt()

        submitGasInEth(selectGasDialogInitialData.gasEstimationItems[GasEstimationItem.Type.REGULAR.ordinal].amountInEthFormatted)

        submitEstimationTime(selectGasDialogInitialData.gasEstimationItems[GasEstimationItem.Type.REGULAR.ordinal].timeFormatted)
    }

    fun submitGasInEth(gasInEth: String) {
        minerFeeTv.text = gasInEth
    }

    private fun submitEstimationTime(timeInMinutes: String) {
        estimatedTimeText.text = context.getString(R.string.transaction_fee_estimated_time, timeInMinutes)
    }

    private fun showAdvancedSettings() {
        gasPriceTitle.show()
        gasPriceWrapper.show()
        gasPriceDivider.show()
        gasLimitTitle.show()
        gasLimitWrapper.show()
        gasLimitSeekBar.show()
        gasRecyclerView.gone()
    }

    private fun hideAdvancedSettings() {
        gasPriceTitle.gone()
        gasPriceWrapper.gone()
        gasPriceDivider.gone()
        gasLimitTitle.gone()
        gasLimitWrapper.gone()
        gasLimitSeekBar.gone()
        gasLimitErrorText.gone()
        gasPriceErrorText.gone()
        gasRecyclerView.show()
    }

    fun showGasLimitError(it: String) {
        gasLimitErrorText.show()
        gasLimitErrorText.text = it
    }

    fun showGasPriceError(it: String) {
        gasPriceErrorText.show()
        gasPriceErrorText.text = it
    }
}