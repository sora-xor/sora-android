/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import jp.co.soramitsu.feature_wallet_impl.databinding.ViewPoolIconsBinding

class PoolIconsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val binding = ViewPoolIconsBinding.inflate(LayoutInflater.from(context), this)

    fun setIcons(
        @DrawableRes underlayIconRes: Int,
        @DrawableRes overlayIconRes: Int,
    ) {
        binding.underlayIcon.setImageDrawable(ContextCompat.getDrawable(context, underlayIconRes))
        binding.overlayIcon.setImageDrawable(ContextCompat.getDrawable(context, overlayIconRes))
    }
}
