/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.AssetBalanceData
import jp.co.soramitsu.common.presentation.AssetBalanceStyle
import jp.co.soramitsu.common.presentation.FiatBalanceData
import jp.co.soramitsu.common.presentation.FiatBalanceStyle
import jp.co.soramitsu.common.util.ext.setBalance
import jp.co.soramitsu.common.util.ext.setFiatBalance
import jp.co.soramitsu.common.util.ext.showOrGone
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.ViewAssetAmountInputBinding
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

class AssetAmountInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val binding =
        ViewAssetAmountInputBinding.inflate(LayoutInflater.from(context), this)

    private var focusChangeListener: OnFocusChangeListener? = null
    private var isBalanceVisible: Boolean = false

    init {
        applyAttributes(attrs)

        binding.assetAmountInput.setOnFocusChangeListener { v, hasFocus ->
            focusChangeListener?.onFocusChange(v, hasFocus)
        }
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AssetAmountInputView)

            typedArray.getString(R.styleable.AssetAmountInputView_title)
                ?.let { title ->
                    applyTitle(title)
                }

            typedArray.getString(R.styleable.AssetAmountInputView_assetBalance)
                ?.let { balance ->
                    setAssetBalance(balance)
                }

            typedArray.getString(R.styleable.AssetAmountInputView_fiatBalance)
                ?.let { balance ->
                    setFiatBalance(balance)
                }

            typedArray.getBoolean(R.styleable.AssetAmountInputView_tokenSelectorEnabled, true)
                .also { enabled ->
                    applyTokenSelectorEnabled(enabled)
                }

            typedArray.getBoolean(R.styleable.AssetAmountInputView_isBalanceVisible, true)
                .also { isVisible -> isBalanceVisible = isVisible }

            typedArray.recycle()
        }
    }

    private fun applyTitle(title: String) {
        binding.amountTitle.text = title
    }

    fun setAssetBalance(balance: String) {
        binding.assetBalanceValue.setBalance(
            AssetBalanceData(
                amount = balance,
                style = AssetBalanceStyle(
                    intStyle = R.style.TextAppearance_Soramitsu_Neu_Regular_15,
                    decStyle = R.style.TextAppearance_Soramitsu_Neu_Regular_11
                )
            )
        )
    }

    private fun setFiatBalance(balance: String) {
        binding.fiatBalanceValue.setFiatBalance(
            FiatBalanceData(
                amount = balance,
                symbol = "$",
                style = FiatBalanceStyle(
                    intStyle = R.style.TextAppearance_Soramitsu_Neu_Regular_15,
                    decStyle = R.style.TextAppearance_Soramitsu_Neu_Regular_11
                )
            )
        )
    }

    private fun setIsInputEnabled(isInputEnabled: Boolean) {
        binding.assetAmountInput.isEnabled = isInputEnabled
    }

    fun setOnChooseTokenListener(listener: () -> Unit) {
        binding.tokenCard.setClickListener(listener)
    }

    private fun applyTokenSelectorEnabled(enabled: Boolean) {
        binding.tokenCard.isEnabled = enabled
    }

    fun setAsset(token: Token) {
        binding.tokenCard.setAsset(token)
        binding.assetAmountInput.isEnabled = true

        binding.balanceTitle.showOrGone(isBalanceVisible)
        binding.assetBalanceValue.showOrGone(isBalanceVisible)
        binding.fiatBalanceValue.showOrGone(isBalanceVisible)
    }

    fun setInput(amount: String) {
        binding.assetAmountInput.setValue(amount)
    }

    fun setPrecision(precision: Int) {
        binding.assetAmountInput.decimalPartLength = precision
    }

    fun setFocusChangeListener(focusChangeListener: OnFocusChangeListener?) {
        this.focusChangeListener = focusChangeListener
    }

    fun clearInputFocus() {
        binding.assetAmountInput.clearFocus()
    }

    fun subscribeInput(): Flow<BigDecimal> =
        binding.assetAmountInput
            .asFlowCurrency()
}
