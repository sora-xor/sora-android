/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.events

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.feature_wallet_impl.R

class EventItemDecoration(context: Context) : RecyclerView.ItemDecoration() {

    private val divider: Drawable = context.getDrawable(R.drawable.divider)!!

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDraw(c, parent, state)
        val dividerLeft = parent.paddingLeft
        val dividerRight = parent.width - parent.paddingRight

        val childCount = parent.childCount
        for (i in 0..childCount - 2) {
            val child = parent.getChildAt(i)
            val nextChild = parent.getChildAt(i + 1)

            if (child.id == R.id.rootEvent && nextChild != null && nextChild.id == R.id.rootEvent) {
                val params = child.layoutParams as RecyclerView.LayoutParams

                val dividerTop = child.bottom + params.bottomMargin
                val dividerBottom = dividerTop + divider.intrinsicHeight

                divider.setBounds(dividerLeft, dividerTop, dividerRight, dividerBottom)
                divider.draw(c)
            }
        }
    }
}
