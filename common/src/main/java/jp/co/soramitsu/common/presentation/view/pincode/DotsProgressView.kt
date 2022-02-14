/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.view.pincode

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import jp.co.soramitsu.common.R

class DotsProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    companion object {
        const val MAX_PROGRESS = 4
    }

    private var circles: Array<View?>

    private var emptyDrawable: Drawable
    private var filledDrawable: Drawable

    private val completeListener: () -> Unit = {}

    init {
        val itemWidth = context.resources.getDimensionPixelSize(R.dimen.uikit_dot_progress_view_dot_width_default)
        val itemHeight = context.resources.getDimensionPixelSize(R.dimen.uikit_dot_progress_view_dot_height_default)
        val itemMargin = context.resources.getDimensionPixelOffset(R.dimen.uikit_dot_progress_view_dot_margin_default)

        emptyDrawable = ContextCompat.getDrawable(context, R.drawable.ic_dot_unchecked)!!
        filledDrawable = ContextCompat.getDrawable(context, R.drawable.ic_dot_checked)!!

        circles = arrayOfNulls(MAX_PROGRESS)

        for (i in 0 until MAX_PROGRESS) {
            val circle = View(context)
            val params = LayoutParams(itemWidth, itemHeight)
            params.setMargins(0, itemMargin, 0, 0)
            circle.layoutParams = params
            addView(circle)
            circles[i] = circle
        }

        circles.reverse()

        setProgress(0)
    }

    fun setProgress(currentProgress: Int) {
        for (circle in circles) {
            circle?.background = emptyDrawable
        }
        if (currentProgress == 0) {
            return
        }
        for (i in 0 until currentProgress) {
            circles[i]?.background = filledDrawable
        }
        if (currentProgress >= MAX_PROGRESS) {
            completeListener()
        }
    }
}
