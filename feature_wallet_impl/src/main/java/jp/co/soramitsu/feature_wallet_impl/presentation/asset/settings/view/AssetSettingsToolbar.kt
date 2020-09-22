/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.android.synthetic.main.view_asset_settings_toolbar.view.leftActionTv
import kotlinx.android.synthetic.main.view_asset_settings_toolbar.view.rightActionTv
import kotlinx.android.synthetic.main.view_asset_settings_toolbar.view.titleTv

class AssetSettingsToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    init {
        View.inflate(context, R.layout.view_asset_settings_toolbar, this)
        applyAttributes(attrs)
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AssetSettingsToolbar)

            val title = typedArray.getString(R.styleable.AssetSettingsToolbar_titleText)
            title?.let { setTitle(it) }

            val leftActionText = typedArray.getString(R.styleable.AssetSettingsToolbar_leftActionText)
            leftActionText?.let { setLeftActionText(it) }

            val rightActionText = typedArray.getString(R.styleable.AssetSettingsToolbar_rightActionText)
            rightActionText?.let { setRightActionText(it) }

            typedArray.recycle()
        }
    }

    fun setTitle(title: String) {
        titleTv.text = title
    }

    fun setLeftActionText(text: String) {
        leftActionTv.text = text
    }

    fun setRightActionText(text: String) {
        rightActionTv.text = text
    }

    fun setRightActionEnabled(enabled: Boolean) {
        rightActionTv.isEnabled = enabled
    }

    fun setLeftActionClickListener(clickListener: () -> Unit) {
        leftActionTv.setOnClickListener { clickListener() }
    }

    fun setRightActionClickListener(clickListener: () -> Unit) {
        rightActionTv.setOnClickListener { clickListener() }
    }
}