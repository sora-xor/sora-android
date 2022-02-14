/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.databinding.NeuToolBarBinding
import jp.co.soramitsu.common.util.ext.dpRes2px
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.setDrawableEnd
import jp.co.soramitsu.common.util.ext.show

class SoraNeuToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = NeuToolBarBinding.inflate(LayoutInflater.from(context), this)

    init {
        applyAttributes(attrs)
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.SoraNeuToolbar)

            val title = typedArray.getString(R.styleable.SoraNeuToolbar_titleText)
            title?.let { setTitle(it) }
            val rightIcon = typedArray.getDrawable(R.styleable.SoraNeuToolbar_iconRight)
            rightIcon?.let { setRightIconDrawable(it) }

            val action = typedArray.getString(R.styleable.SoraNeuToolbar_textRight)
            action?.let { setTextRight(it) }

            val homeButtonIcon =
                typedArray.getDrawable(R.styleable.SoraNeuToolbar_homeButtonIcon)
            homeButtonIcon?.let { setHomeButtonIcon(it) }

            val homeButtonVisible =
                typedArray.getBoolean(R.styleable.SoraNeuToolbar_homeButtonVisible, true)
            setHomeButtonVisibility(homeButtonVisible)

            typedArray.recycle()
        }
    }

    fun setHomeButtonIcon(icon: Drawable) {
        binding.backImg.setImageDrawable(icon)
    }

    fun setTextRight(action: String) {
        binding.rightImg.gone()

        binding.rightText.show()
        binding.rightText.text = action
    }

    fun setTitle(title: String) {
        binding.titleTv.text = title
    }

    fun setTitle(title: Int) {
        binding.titleTv.setText(title)
    }

    fun showHomeButton() {
        binding.backImg.show()
    }

    fun hideHomeButton() {
        binding.backImg.gone()
    }

    fun setHomeButtonListener(listener: (View) -> Unit) {
        binding.backImg.setOnClickListener(listener)
    }

    fun setRightIconDrawable(assetIconDrawable: Drawable, padding: Boolean = false) {
        binding.rightText.gone()

        binding.rightImg.show()
        binding.rightImg.setImageDrawable(assetIconDrawable)
        binding.rightImg.setPadding(if (padding) context.dpRes2px(R.dimen.x1_5) else 0)
    }

    fun setRightActionClickListener(listener: (View) -> Unit) {
        binding.rightImg.setOnClickListener(listener)
        binding.rightText.setOnClickListener(listener)
    }

    fun setHomeButtonVisibility(visible: Boolean) {
        binding.backImg.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setRightTextButtonEnabled() {
        binding.rightText.isEnabled = true
        binding.rightText.setTextColor(ContextCompat.getColor(context, R.color.black))
    }

    fun setRightTextButtonDisabled() {
        binding.rightText.isEnabled = false
        binding.rightText.setTextColor(ContextCompat.getColor(context, R.color.grey_400))
    }

    fun setTitleIcon(@DrawableRes res: Int) {
        binding.titleTv.setDrawableEnd(res, 30, 30)
    }
}
