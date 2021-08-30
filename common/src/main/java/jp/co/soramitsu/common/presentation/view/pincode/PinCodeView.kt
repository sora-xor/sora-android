/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.view.pincode

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatButton
import jp.co.soramitsu.common.databinding.UikitViewPinCodeBinding

class PinCodeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    var pinCodeListener: (String) -> Unit = {}
    var deleteClickListener: () -> Unit = {}
    var fingerprintClickListener: () -> Unit = {}
    private val pinCodeNumberClickListener = OnClickListener {
        pinCodeListener((it as AppCompatButton).text.toString())
    }

    private val pinCodeDeleteClickListener = OnClickListener {
        deleteClickListener()
    }

    private val pinCodeFingerprintClickListener = OnClickListener {
        fingerprintClickListener()
    }
    private val binding = UikitViewPinCodeBinding.inflate(LayoutInflater.from(context), this)

    init {
        orientation = VERTICAL
        binding.btn1.setOnClickListener(pinCodeNumberClickListener)
        binding.btn2.setOnClickListener(pinCodeNumberClickListener)
        binding.btn3.setOnClickListener(pinCodeNumberClickListener)
        binding.btn4.setOnClickListener(pinCodeNumberClickListener)
        binding.btn5.setOnClickListener(pinCodeNumberClickListener)
        binding.btn6.setOnClickListener(pinCodeNumberClickListener)
        binding.btn7.setOnClickListener(pinCodeNumberClickListener)
        binding.btn8.setOnClickListener(pinCodeNumberClickListener)
        binding.btn9.setOnClickListener(pinCodeNumberClickListener)
        binding.btn0.setOnClickListener(pinCodeNumberClickListener)
        binding.btnDelete.setOnClickListener(pinCodeDeleteClickListener)
        binding.fingerprintBtn.setOnClickListener(pinCodeFingerprintClickListener)
    }

    fun changeDeleteButtonVisibility(isVisible: Boolean) {
        if (isVisible) {
            binding.btnDelete.animate()
                .withStartAction { binding.btnDelete.visibility = View.VISIBLE }
                .alpha(1.0f)
                .start()
        } else {
            binding.btnDelete.animate()
                .withEndAction { binding.btnDelete.visibility = View.INVISIBLE }
                .alpha(0.0f)
                .start()
        }
    }

    fun changeFingerPrintButtonVisibility(isVisible: Boolean) {
        if (isVisible) {
            binding.fingerprintBtn.visibility = View.VISIBLE
        } else {
            binding.fingerprintBtn.visibility = View.INVISIBLE
        }
    }
}
