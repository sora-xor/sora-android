package jp.co.soramitsu.common.presentation.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.Dimension
import androidx.core.content.ContextCompat
import jp.co.soramitsu.common.R

private const val DEFAULT_PERCENTAGE = 50f

class VsLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var leftPaint: Paint = createPaint(R.color.defaultLeftColor)
    private var rightPaint: Paint = createPaint(R.color.defaultRightColor)

    private var delimiterPaint: Paint = createPaint(R.color.defaultDelimiterColor)

    private var lineHeight: Float = getDimen(R.dimen.defaultLineHeight)

    private var delimiterHeight = calculateDelimiterHeight()

    private var delimiterWidth = getDimen(R.dimen.delimiterWidth)

    private var primaryPercentage: Float = DEFAULT_PERCENTAGE

    /**
     * Percentage of primary part
     * Should be in range 0..100
     */
    var percentage: Float
        get() = primaryPercentage
        set(value) {
            primaryPercentage = value

            invalidate()
        }

    init {
        attrs?.let {
            initWithAttrs(it, defStyleAttr)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val desiredHeight = delimiterHeight.toInt()
        val desiredWidth = suggestedMinimumWidth

        val resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec)
        val resolvedWidth = resolveSize(desiredWidth, widthMeasureSpec)

        setMeasuredDimension(resolvedWidth, resolvedHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val radius = lineHeight / 2

        val centerY = measuredHeight / 2
        val centerX = measuredWidth / 2

        val lineTopY = centerY - lineHeight / 2
        val lineBottomY = centerY + lineHeight / 2

        canvas.drawRoundRect(0f, lineTopY, measuredWidth.toFloat(), lineBottomY, radius, radius, leftPaint)

        val primaryPartWidth = measuredWidth * (primaryPercentage / 100)

        canvas.drawRoundRect(measuredWidth - primaryPartWidth, lineTopY, measuredWidth.toFloat(), lineBottomY, radius, radius, rightPaint)

        val delimiterTopY = centerY - delimiterHeight / 2
        val delimiterBottomY = centerY + delimiterHeight / 2
        val delimiterLeftX = centerX - delimiterWidth / 2
        val delimiterRightX = centerX + delimiterWidth / 2

        canvas.drawRect(delimiterLeftX, delimiterTopY, delimiterRightX, delimiterBottomY, delimiterPaint)
    }

    private fun initWithAttrs(attrs: AttributeSet, defStyleAttr: Int) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.VsLineView, defStyleAttr, 0)

        val leftColor = typedArray.getColor(R.styleable.VsLineView_leftColor, getColor(R.color.defaultLeftColor))
        leftPaint = createPaint(leftColor)

        val rightColor = typedArray.getColor(R.styleable.VsLineView_rightColor, getColor(R.color.defaultRightColor))
        rightPaint = createPaint(rightColor)

        val delimiterColor = typedArray.getColor(R.styleable.VsLineView_delimiterColor, getColor(R.color.defaultDelimiterColor))
        delimiterPaint = createPaint(delimiterColor)

        lineHeight = typedArray.getDimension(R.styleable.VsLineView_lineHeight, getDimen(R.dimen.defaultLineHeight))
        delimiterHeight = calculateDelimiterHeight()

        val percentage = typedArray.getFloat(R.styleable.VsLineView_primaryPercentage, DEFAULT_PERCENTAGE)
        primaryPercentage = percentage

        typedArray.recycle()
    }

    private fun createPaint(fillColor: Int): Paint {
        return Paint().apply {
            color = fillColor
        }
    }

    private fun calculateDelimiterHeight(): Float {
        return lineHeight + 2 * getDimen(R.dimen.delimiterExtraSpace).toInt()
    }

    @ColorInt
    private fun getColor(@ColorRes colorRes: Int) = ContextCompat.getColor(context, colorRes)

    @Dimension
    private fun getDimen(@DimenRes dimenRes: Int) = resources.getDimension(dimenRes)
}
