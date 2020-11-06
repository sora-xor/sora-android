package jp.co.soramitsu.feature_onboarding_impl.presentation.country.adapter

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.util.ext.toPx

class CountryItemDecoration(
    context: Context,
    private val divider: Drawable
) : RecyclerView.ItemDecoration() {

    companion object {
        private const val PADDING_BY_SIDE_DP = 20
    }

    private val leftRightPaddingDp: Int = PADDING_BY_SIDE_DP.toPx(context).toInt()

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val dividerLeft = parent.paddingLeft + leftRightPaddingDp
        val dividerRight = parent.width - parent.paddingRight - leftRightPaddingDp

        val childCount = parent.childCount
        for (i in 0..childCount - 2) {
            val child = parent.getChildAt(i)

            val params = child.layoutParams as RecyclerView.LayoutParams

            val dividerTop = child.bottom + params.bottomMargin
            val dividerBottom = dividerTop + divider.intrinsicHeight

            divider.setBounds(dividerLeft, dividerTop, dividerRight, dividerBottom)
            divider.draw(canvas)
        }
    }
}