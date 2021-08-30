/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.events

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior

class LockBottomSheetBehavior<V : View> @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BottomSheetBehavior<V>(context, attrs) {

    companion object {
        fun <V : View> fromView(view: V): LockBottomSheetBehavior<V> {
            val params = view.layoutParams
            if (params !is CoordinatorLayout.LayoutParams) {
                throw IllegalArgumentException("The view is not a child of CoordinatorLayout")
            } else {
                val behavior = params.behavior
                return behavior as? LockBottomSheetBehavior<V>
                    ?: throw IllegalArgumentException("The view is not associated with BottomSheetBehavior")
            }
        }
    }

    var isDialogDraggable = true

    override fun onInterceptTouchEvent(parent: CoordinatorLayout, child: V, event: MotionEvent): Boolean {
        return if (isDialogDraggable) super.onInterceptTouchEvent(parent, child, event) else false
    }

    override fun onTouchEvent(parent: CoordinatorLayout, child: V, event: MotionEvent): Boolean {
        return if (isDialogDraggable) super.onTouchEvent(parent, child, event) else false
    }

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ): Boolean {
        return if (isDialogDraggable) super.onStartNestedScroll(
            coordinatorLayout,
            child,
            directTargetChild,
            target,
            axes,
            type
        ) else false
    }

    override fun onNestedPreFling(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        target: View,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        return if (isDialogDraggable) super.onNestedPreFling(coordinatorLayout, child, target, velocityX, velocityY) else false
    }
}
