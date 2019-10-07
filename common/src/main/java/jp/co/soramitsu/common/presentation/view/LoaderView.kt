/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.view

import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import jp.co.soramitsu.common.R

class LoaderView(
    private val context: AppCompatActivity
) {

    private val loader: CardView = context.findViewById(R.id.loading)
    private val content: FrameLayout = context.findViewById(R.id.content)

    fun showLoading() {
        loader.visibility = View.VISIBLE
        lockUi()
        fadeUi()
    }

    fun hideLoading() {
        loader.visibility = View.GONE
        unlockUi()
        unfadeUi()
    }

    private fun lockUi() {
        context.window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    private fun unlockUi() {
        context.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    private fun fadeUi() {
        content.alpha = 0.3f
    }

    private fun unfadeUi() {
        content.alpha = 1f
    }
}