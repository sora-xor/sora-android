package jp.co.soramitsu.common.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isNotEmpty
import jp.co.soramitsu.common.R
import kotlin.math.abs

/** Supports both pointer dragging and keyboard/TalkBack activation of the reorder menu. */
class DragHandleView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatImageView(context, attrs) {
    private var startDrag: (() -> Unit)? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var pointerActive = false
    private var pointerMoved = false
    private var downX = 0f
    private var downY = 0f

    fun bindReordering(onStartDrag: () -> Unit, canMove: (Int) -> Boolean, move: (Int) -> Boolean) {
        startDrag = onStartDrag
        setOnClickListener {
            val menu = PopupMenu(context, this)
            if (canMove(-1)) menu.menu.add(0, -1, 0, R.string.asset_move_up)
            if (canMove(1)) menu.menu.add(0, 1, 1, R.string.asset_move_down)
            menu.setOnMenuItemClickListener { item -> canMove(item.itemId) && move(item.itemId) }
            if (menu.menu.isNotEmpty()) menu.show()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) {
            pointerActive = false
        } else {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pointerActive = true
                    pointerMoved = false
                    downX = event.rawX
                    downY = event.rawY
                    startDrag?.invoke()
                }
                MotionEvent.ACTION_MOVE -> recordMovement(event)
                MotionEvent.ACTION_POINTER_DOWN -> pointerMoved = true
                MotionEvent.ACTION_CANCEL -> pointerActive = false
                MotionEvent.ACTION_UP -> {
                    recordMovement(event)
                    val wasTap = pointerActive && !pointerMoved
                    pointerActive = false
                    // Clear View's pressed/delayed-click state without letting it turn a drag
                    // inside the handle's bounds into a click of its own.
                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    try { super.onTouchEvent(cancel) } finally { cancel.recycle() }
                    if (wasTap) performClick()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun recordMovement(event: MotionEvent) {
        if (pointerActive && (abs(event.rawX - downX) > touchSlop || abs(event.rawY - downY) > touchSlop)) {
            pointerMoved = true
        }
    }

    override fun performClick(): Boolean = super.performClick()
}
