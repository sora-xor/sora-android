/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.hide.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.android.synthetic.main.view_asset_hiding.view.assetFirstNameTv
import kotlinx.android.synthetic.main.view_asset_hiding.view.assetLastNameTv
import kotlinx.android.synthetic.main.view_asset_hiding.view.balanceTv
import kotlinx.android.synthetic.main.view_asset_hiding.view.displayedCb
import kotlinx.android.synthetic.main.view_asset_hiding.view.iconImg

class AssetHidingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        View.inflate(context, R.layout.view_asset_hiding, this)
    }

    fun setBalance(balance: String) {
        balanceTv.text = balance
    }

    fun setAssetFirstName(assetName: String) {
        assetFirstNameTv.text = assetName
    }

    fun setAssetLastName(blockChainName: String) {
        assetLastNameTv.text = blockChainName
    }

    fun setAssetIconDrawable(assetIconDrawable: Drawable) {
        iconImg.setImageDrawable(assetIconDrawable)
    }

    fun setAssetIconResource(assetIconRes: Int) {
        iconImg.setImageResource(assetIconRes)
    }

    fun setCheckChangeListener(checkChangeListener: (Boolean) -> Unit) {
        displayedCb.setOnCheckedChangeListener { buttonView, isChecked ->
            checkChangeListener(isChecked)
        }
    }
}