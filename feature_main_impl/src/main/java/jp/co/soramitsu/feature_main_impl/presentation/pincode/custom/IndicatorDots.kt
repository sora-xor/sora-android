/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode.custom

import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.IntDef
import androidx.core.view.ViewCompat
import jp.co.soramitsu.feature_main_impl.R

class IndicatorDots @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val DEFAULT_PIN_LENGTH = 4
    }

    private var mDotDiameter: Int = 0
    private var mDotSpacing: Int = 0
    private var mFillDrawable: Int = 0
    private var mEmptyDrawable: Int = 0
    private var mPinLength: Int = 0
    private var mIndicatorType: Int = 0
    private var mPreviousLength: Int = 0

    var pinLength: Int
        get() = mPinLength
        set(pinLength) {
            this.mPinLength = pinLength
            removeAllViews()
            initView(context)
        }

    init {

        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.PinLockView)

        try {
            mDotDiameter = typedArray.getDimension(R.styleable.PinLockView_dotDiameter,
                ResourceUtils.getDimensionInPx(getContext(), R.dimen.default_dot_diameter)).toInt()
            mDotSpacing = typedArray.getDimension(R.styleable.PinLockView_dotSpacing,
                ResourceUtils.getDimensionInPx(getContext(), R.dimen.default_dot_spacing)).toInt()
            mFillDrawable = typedArray.getResourceId(R.styleable.PinLockView_dotFilledBackground,
                R.drawable.dot_filled)
            mEmptyDrawable = typedArray.getResourceId(R.styleable.PinLockView_dotEmptyBackground,
                R.drawable.dot_empty)
            mPinLength = typedArray.getInt(R.styleable.PinLockView_pinLength, DEFAULT_PIN_LENGTH)
            mIndicatorType = typedArray.getInt(R.styleable.PinLockView_indicatorType,
                IndicatorType.FIXED)
        } finally {
            typedArray.recycle()
        }

        initView(context)
    }

    private fun initView(context: Context) {
        ViewCompat.setLayoutDirection(this, ViewCompat.LAYOUT_DIRECTION_LTR)
        if (mIndicatorType == 0) {
            for (i in 0 until mPinLength) {
                val dot = View(context)
                emptyDot(dot)

                val params = LayoutParams(mDotDiameter, mDotDiameter)
                params.setMargins(mDotSpacing, 0, mDotSpacing, 0)
                dot.layoutParams = params

                addView(dot)
            }
        } else if (mIndicatorType == 2) {
            layoutTransition = LayoutTransition()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // If the indicator type is not fixed
        if (mIndicatorType != 0) {
            val params = this.layoutParams
            params.height = mDotDiameter
            requestLayout()
        }
    }

    fun updateDot(length: Int) {
        if (mIndicatorType == 0) {
            if (length > 0) {
                if (length > mPreviousLength) {
                    fillDot(getChildAt(length - 1))
                } else {
                    emptyDot(getChildAt(length))
                }
                mPreviousLength = length
            } else {
                // When {@code mPinLength} is 0, we need to reset all the views back to empty
                for (i in 0 until childCount) {
                    val v = getChildAt(i)
                    emptyDot(v)
                }
                mPreviousLength = 0
            }
        } else {
            if (length > 0) {
                if (length > mPreviousLength) {
                    val dot = View(context)
                    fillDot(dot)

                    val params = LayoutParams(mDotDiameter,
                        mDotDiameter)
                    params.setMargins(mDotSpacing, 0, mDotSpacing, 0)
                    dot.layoutParams = params

                    addView(dot, length - 1)
                } else {
                    removeViewAt(length)
                }
                mPreviousLength = length
            } else {
                removeAllViews()
                mPreviousLength = 0
            }
        }
    }

    private fun emptyDot(dot: View) {
        dot.setBackgroundResource(mEmptyDrawable)
    }

    private fun fillDot(dot: View) {
        dot.setBackgroundResource(mFillDrawable)
    }

    @IntDef(IndicatorType.FIXED, IndicatorType.FILL, IndicatorType.FILL_WITH_ANIMATION)
    annotation class IndicatorType {
        companion object {
            const val FIXED = 0
            const val FILL = 1
            const val FILL_WITH_ANIMATION = 2
        }
    }
}