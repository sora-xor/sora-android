/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.presentation.AssetBalanceData
import jp.co.soramitsu.common.presentation.AssetBalanceStyle
import jp.co.soramitsu.common.util.ext.getColorAttr
import jp.co.soramitsu.common.util.ext.setBalance
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.ViewAssetBinding

class AssetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val binding = ViewAssetBinding.inflate(LayoutInflater.from(context), this)

    init {
        applyAttributes(attrs)
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AssetView)

            val assetIcon = typedArray.getDrawable(R.styleable.AssetView_assetIcon)
            assetIcon?.let { setAssetIconDrawable(it) }

            val assetBalanceText = typedArray.getString(R.styleable.AssetView_assetBalanceText)
            assetBalanceText?.let { setBalance(it) }

            val assetNameText = typedArray.getString(R.styleable.AssetView_assetName)
            assetNameText?.let { setAssetFirstName(it) }

            val assetBlockChainNameText =
                typedArray.getString(R.styleable.AssetView_assetBlockChainName)
            assetBlockChainNameText?.let { setAssetLastName(it) }

            val assetIconViewBackgroundColor =
                typedArray.getColor(R.styleable.AssetView_assetIconViewBackgroundColor, 0)
            if (assetIconViewBackgroundColor != 0) {
                // setAssetIconViewBackgroundColor(assetIconViewBackgroundColor)
            }

            typedArray.recycle()
            setBackgroundColor(getColorAttr(R.attr.baseBackground))
        }
    }

    fun setBalance(balance: String) {
        binding.balanceTv.setBalance(
            AssetBalanceData(
                amount = balance,
                style = AssetBalanceStyle(
                    intStyle = R.style.TextAppearance_Soramitsu_Neu_Bold_18,
                    decStyle = R.style.TextAppearance_Soramitsu_Neu_Bold_13
                )
            )
        )
    }

    fun setAssetFirstName(assetName: String) {
        binding.assetFirstNameTv.text = assetName
    }

    fun setAssetLastName(blockChainName: String) {
        binding.assetLastNameTv.text = blockChainName
    }

    fun setAssetIconDrawable(assetIconDrawable: Drawable) {
        binding.iconImg.setImageDrawable(assetIconDrawable)
    }

    fun setAssetIconResource(assetIconRes: Int) {
        binding.iconImg.setImageResource(assetIconRes)
    }
}
