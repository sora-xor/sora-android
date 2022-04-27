/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.core.widget.TextViewCompat
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.databinding.AssetCardViewBinding
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.setImageTint2
import jp.co.soramitsu.common.util.ext.show

class SelectTokenCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.selectTokenCardViewStyle,
    defStyleRes: Int = R.style.Widget_Soramitsu_SelectTokenCardView,
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val binding = AssetCardViewBinding.inflate(LayoutInflater.from(context), this)

    private var openIcon: Drawable? = null
    private var closeIcon: Drawable? = null
    private var selectedBackgroundTintColor: Int? = null
    private var notSelectedBackgroundTintColor: Int? = null
    private var selectedIconTintColor: Int? = null
    private var notSelectedIconTintColor: Int? = null
    private var textAppearanceSelected: Int? = null
    private var textAppearanceNotSelected: Int? = null

    init {
        val typedArray = context.theme.obtainStyledAttributes(
            attrs, R.styleable.SelectTokenCardView, defStyleAttr, defStyleRes
        )
        typedArray.use {
            background = it.getDrawable(R.styleable.SelectTokenCardView_cardBackground)
            val selectedBackgroundTintColorRes = it.getResourceId(
                R.styleable.SelectTokenCardView_cardBackgroundColorTintSelected,
                R.attr.flatAboveBackground
            )
            val notSelectedBackgroundTintColorRes = it.getResourceId(
                R.styleable.SelectTokenCardView_cardBackgroundColorTintNotSelected,
                R.attr.polkaswapMainColor
            )
            selectedBackgroundTintColor =
                ContextCompat.getColor(context, selectedBackgroundTintColorRes)
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    notSelectedBackgroundTintColorRes
                ).also { color ->
                    notSelectedBackgroundTintColor = color
                }
            )
            val selectedIconTintColorRes = it.getResourceId(
                R.styleable.SelectTokenCardView_cardIconTintSelected,
                R.attr.flatAboveBackground
            )
            val notSelectedIconTintColorRes = it.getResourceId(
                R.styleable.SelectTokenCardView_cardIconTintNotSelected,
                R.attr.iconTintAboveBackground
            )
            selectedIconTintColor = ContextCompat.getColor(context, selectedIconTintColorRes)
            binding.chevronIcon.setImageTint2(
                ContextCompat.getColor(context, notSelectedIconTintColorRes).also { color ->
                    notSelectedIconTintColor = color
                }
            )
            val p =
                it.getDimension(R.styleable.SelectTokenCardView_cardTokenIconPadding, 8f).toInt()
            binding.icon.setPadding(p)
            textAppearanceSelected = it.getResourceId(
                R.styleable.SelectTokenCardView_cardTextAppearance,
                R.style.TextAppearance_Soramitsu_Neu_ExtraBold_20
            )
            textAppearanceNotSelected = it.getResourceId(
                R.styleable.SelectTokenCardView_cardTextAppearanceNotSelected,
                R.style.TextAppearance_Soramitsu_Neu_ExtraBold_15
            )
            TextViewCompat.setTextAppearance(
                binding.text,
                textAppearanceNotSelected!!
            )
            binding.icon.gone()
            openIcon = it.getDrawable(R.styleable.SelectTokenCardView_cardOpenIcon)
            closeIcon = it.getDrawable(R.styleable.SelectTokenCardView_cardCloseIcon)
            resetChevron()
        }
    }

    fun setAsset(token: Token) {
        textAppearanceSelected?.let {
            TextViewCompat.setTextAppearance(
                binding.text,
                it
            )
        }
        binding.text.maxLines = 1
        binding.text.isSingleLine = true
        binding.text.setPadding(0)
        selectedBackgroundTintColor?.let {
            backgroundTintList = ColorStateList.valueOf(it)
        }
        selectedIconTintColor?.let {
            binding.chevronIcon.setImageTint2(it)
        }
        binding.chevronIcon.isVisible = isEnabled
        binding.text.text = token.symbol
        binding.icon.setImageResource(token.icon)
        binding.icon.show()
    }

    fun resetChevron() {
        binding.chevronIcon.setImageDrawable(openIcon)
    }

    fun setClickListener(listener: () -> Unit) {
        setOnClickListener {
            binding.chevronIcon.setImageDrawable(closeIcon)
            listener()
        }
    }
}
