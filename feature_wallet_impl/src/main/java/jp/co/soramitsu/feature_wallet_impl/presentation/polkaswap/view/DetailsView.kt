/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import jp.co.soramitsu.common.util.ext.showOrGone
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.ViewDetailsBinding
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.model.DetailsSection

class DetailsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val binding = ViewDetailsBinding.inflate(LayoutInflater.from(context), this)

    private var expanded: Boolean = false

    init {
        binding.detailsIcon.setOnClickListener {
            expanded = !expanded
            binding.sections.showOrGone(expanded && binding.sections.childCount > 0)
            updateDetailsTitleColor()
            updateChevronIcon()
        }

        updateDetailsTitleColor()
    }

    fun setData(items: List<DetailsSection>) {
        binding.sections.removeAllViews()
        binding.sections.run {
            items.forEach { section ->
                addView(
                    DetailsSectionView(context).apply {
                        setData(section.title, section.items)
                    }
                )
            }
        }
        binding.sections.showOrGone(expanded && items.isNotEmpty())
    }

    private fun updateChevronIcon() {
        val icon = if (expanded) {
            ContextCompat.getDrawable(context, R.drawable.ic_neu_chevron_up)
        } else {
            ContextCompat.getDrawable(context, R.drawable.ic_neu_chevron_down)
        }
        binding.detailsIcon.setImageDrawable(icon)
    }

    private fun updateDetailsTitleColor() {
        val color = if (expanded) {
            ContextCompat.getColor(context, R.color.neu_brand_polkaswap)
        } else {
            ContextCompat.getColor(context, R.color.neu_disabled_text_grey_2)
        }

        binding.detailsTitle.setTextColor(color)
    }
}
