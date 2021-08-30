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
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.databinding.ToolBarBinding
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show

class SoraToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = ToolBarBinding.inflate(LayoutInflater.from(context), this)

    init {
        applyAttributes(attrs)
        binding.titleTv.doOnLayout { t ->
            binding.vWidthHalf.doOnLayout { h ->
                (t as? TextView)?.maxWidth = h.width * 2
            }
        }
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.SoraToolbar)

            val title = typedArray.getString(R.styleable.SoraToolbar_titleText)
            title?.let { setTitle(it) }

            val rightIcon = typedArray.getDrawable(R.styleable.SoraToolbar_iconRight)
            rightIcon?.let { setRightIconDrawable(it) }

            val action = typedArray.getString(R.styleable.SoraToolbar_textRight)
            action?.let { setTextRight(it) }

            val homeButtonIcon = typedArray.getDrawable(R.styleable.SoraToolbar_homeButtonIcon)
            homeButtonIcon?.let { setHomeButtonIcon(it) }

            val homeButtonVisible =
                typedArray.getBoolean(R.styleable.SoraToolbar_homeButtonVisible, true)
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

    fun setRightIconDrawable(assetIconDrawable: Drawable) {
        binding.rightText.gone()

        binding.rightImg.show()
        binding.rightImg.setImageDrawable(assetIconDrawable)
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
        binding.titleTv.setCompoundDrawablesRelativeWithIntrinsicBounds(res, 0, 0, 0)
    }
}
