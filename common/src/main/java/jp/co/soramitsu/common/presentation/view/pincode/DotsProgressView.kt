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
        const val OLD_PINCODE_LENGTH = 4
        const val PINCODE_LENGTH = 6
    }

    private lateinit var circles: Array<View?>

    private lateinit var emptyDrawable: Drawable
    private lateinit var filledDrawable: Drawable

    var maxProgress: Int = PINCODE_LENGTH
        set(value) {
            field = value
            setup()
        }

    private val completeListener: () -> Unit = {}

    init {
        setup()
    }

    private fun setup() {
        removeAllViews()
        val itemWidth = context.resources.getDimensionPixelSize(R.dimen.uikit_dot_progress_view_dot_width_default)
        val itemHeight = context.resources.getDimensionPixelSize(R.dimen.uikit_dot_progress_view_dot_height_default)
        val itemMargin = context.resources.getDimensionPixelOffset(R.dimen.uikit_dot_progress_view_dot_margin_default)

        emptyDrawable = ContextCompat.getDrawable(context, R.drawable.ic_dot_unchecked)!!
        filledDrawable = ContextCompat.getDrawable(context, R.drawable.ic_dot_checked)!!

        circles = arrayOfNulls(maxProgress)

        for (i in 0 until maxProgress) {
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
        if (currentProgress >= maxProgress) {
            completeListener()
        }
    }
}
