/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.mnemonic_confirmation.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.databinding.ViewMnemonicWordBinding

class MnemonicWordView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val viewBinding = ViewMnemonicWordBinding.inflate(LayoutInflater.from(context), this)

    init {
        applyAttributes(attrs)
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.MnemonicWordView)

            val word = typedArray.getString(R.styleable.MnemonicWordView_wordText)
            word?.let { setWord(it) }

            typedArray.recycle()
        }
    }

    fun setWord(word: String) {
        viewBinding.wordTv.text = word
    }

    fun enable() {
        this.isEnabled = true
        viewBinding.wordTv.setTextColor(ContextCompat.getColor(context, R.color.grey_900))
    }

    fun disable() {
        this.isEnabled = false
        viewBinding.wordTv.setTextColor(ContextCompat.getColor(context, R.color.grey_400))
    }
}
