/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.feature_wallet_impl.databinding.ViewDetailsItemBinding
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.model.DetailsItem

class DetailsItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val binding = ViewDetailsItemBinding.inflate(LayoutInflater.from(context), this)

    fun setData(detailsItem: DetailsItem) {
        binding.run {
            title.text = detailsItem.title
            value.text = detailsItem.value
        }
    }
}
