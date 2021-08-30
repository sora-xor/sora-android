/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.cardview.widget.CardView
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.ViewAssetBinding

class AssetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr) {

    enum class State {
        NORMAL,
        ASSOCIATING,
        ERROR
    }

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
                setAssetIconViewBackgroundColor(assetIconViewBackgroundColor)
            }

            typedArray.recycle()
        }
    }

    fun setBalance(balance: String) {
        binding.balanceTv.text = balance
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

    fun setAssetIconViewBackgroundColor(color: Int) {
        binding.assetIconView.setBackgroundColor(color)
    }

    fun changeState(state: State) {
        when (state) {
            State.NORMAL -> showNormalState()
            State.ASSOCIATING -> showAssociatingState()
            State.ERROR -> showErrorState()
        }
    }

    private fun showNormalState() {
        binding.normalStateView.visibility = View.VISIBLE
        binding.associatingStateView.visibility = View.GONE
        binding.errorStateView.visibility = View.GONE
    }

    private fun showAssociatingState() {
        binding.normalStateView.visibility = View.GONE
        binding.associatingStateView.visibility = View.VISIBLE
        binding.errorStateView.visibility = View.GONE
    }

    private fun showErrorState() {
        binding.normalStateView.visibility = View.GONE
        binding.associatingStateView.visibility = View.GONE
        binding.errorStateView.visibility = View.VISIBLE
    }
}
