/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.view

import android.content.Context
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.use
import androidx.core.text.underline
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.databinding.ViewAboutCellBinding
import jp.co.soramitsu.common.util.ext.removeWebPrefix
import jp.co.soramitsu.common.util.ext.setImageTint

class AboutCellView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.aboutCellViewStyle,
    defStyleRes: Int = R.style.Widget_Soramitsu_AboutCellView
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val binding = ViewAboutCellBinding.inflate(LayoutInflater.from(context), this)

    init {
        val typedArray = context.theme.obtainStyledAttributes(
            attrs, R.styleable.AboutCellView, defStyleAttr, defStyleRes
        )
        typedArray.use {
            val title = it.getString(R.styleable.AboutCellView_cellTitleText).orEmpty()
            binding.tvTitle.text = title
            TextViewCompat.setTextAppearance(
                binding.tvTitle,
                it.getResourceId(
                    R.styleable.AboutCellView_cellTitleTextAppearance,
                    R.style.TextAppearance_Soramitsu_Neu_Regular_15
                )
            )
            val description = it.getString(R.styleable.AboutCellView_cellDescriptionText)
            if (description.isNullOrEmpty()) {
                binding.tvDescription.isGone = true
            } else {
                binding.tvDescription.isVisible = true
                TextViewCompat.setTextAppearance(
                    binding.tvDescription,
                    it.getResourceId(
                        R.styleable.AboutCellView_cellDescriptionTextAppearance,
                        R.style.TextAppearance_Soramitsu_Neu_Light_13
                    )
                )
                setDescription(description)
            }
            binding.ivLeftIcon.setImageResource(
                it.getResourceId(
                    R.styleable.AboutCellView_cellLeftIcon,
                    R.drawable.ic_0x0200000000000000000000000000000000000000000000000000000000000000
                )
            )
            val tint = it.getResourceId(R.styleable.AboutCellView_cellLeftIconTint, 0)
            if (tint != 0) {
                binding.ivLeftIcon.setImageTint(tint)
            }
        }
    }

    fun setDescription(s: String) {
        binding.tvDescription.text =
            SpannableStringBuilder().underline { append(s.removeWebPrefix()) }
    }
}
