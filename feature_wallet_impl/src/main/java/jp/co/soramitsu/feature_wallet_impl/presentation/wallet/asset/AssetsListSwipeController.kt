package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.asset

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class AssetsListSwipeController(
    private val iconSize: Int,
    private val onSwiped: (Int) -> Unit,
    private val allowToSwipe: (Int) -> Boolean,
    private val onSwipedPartly: (Int) -> Unit,
    private val getIcon: (Int) -> Drawable?,
    private val swiping: (Boolean) -> Unit,
) : ItemTouchHelper.Callback() {

    private var swipeBack: Boolean = false
    private var currentPosition: Int? = null
    private var currentIcon: Drawable? = null
    private var swipedPartlySent: Boolean = true

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        return makeMovementFlags(0, ItemTouchHelper.START or ItemTouchHelper.END)
    }

    override fun convertToAbsoluteDirection(flags: Int, layoutDirection: Int): Int =
        if (swipeBack && currentPosition?.let { allowToSwipe.invoke(it) } == false) {
            swipeBack = false
            0
        } else {
            super.convertToAbsoluteDirection(flags, layoutDirection)
        }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        onSwiped.invoke(viewHolder.adapterPosition)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        currentPosition = null
        currentIcon = null
        swipedPartlySent = false
        swiping.invoke(false)
        super.clearView(recyclerView, viewHolder)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        currentIcon = getIcon(viewHolder.adapterPosition)
        drawIcons(c, viewHolder)
        currentPosition = viewHolder.adapterPosition
        if (actionState == ACTION_STATE_SWIPE) {
            swiping.invoke(true)
            setOnItemSwipeListener(recyclerView)
            if (abs(dX) > 0.6 * viewHolder.itemView.width && !swipedPartlySent) {
                swipedPartlySent = true
                onSwipedPartly.invoke(viewHolder.adapterPosition)
            }
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setOnItemSwipeListener(
        recyclerView: RecyclerView,
    ) {
        recyclerView.setOnTouchListener { _, event ->
            swipeBack =
                event?.action == MotionEvent.ACTION_CANCEL || event?.action == MotionEvent.ACTION_UP
            false
        }
    }

    private fun drawIcons(canvas: Canvas, viewHolder: RecyclerView.ViewHolder) {
        val itemView = viewHolder.itemView
        val iconSizeHalf = iconSize / 2
        val centerY = itemView.top + itemView.height / 2
        val centerX = itemView.right - itemView.width / 2

        val rect = Rect(-iconSizeHalf, -iconSizeHalf, iconSizeHalf, iconSizeHalf)
        currentIcon?.bounds = rect

        canvas.save()
        canvas.translate(centerX.toFloat(), centerY.toFloat())
        currentIcon?.draw(canvas)
        canvas.restore()
    }
}
