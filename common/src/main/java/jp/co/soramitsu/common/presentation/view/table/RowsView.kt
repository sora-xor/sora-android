/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.view.table

import android.content.Context
import android.text.SpannableString
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StyleRes
import androidx.core.content.res.use
import androidx.core.widget.TextViewCompat
import jp.co.soramitsu.common.R

class RowsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.rowsViewStyle,
    defStyleRes: Int = R.style.Widget_Soramitsu_RowsView
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {

    private var rowsCount: Int = 0
    private var styleTitle: Int = R.style.TextAppearance_Soramitsu_Neu_Light_11
    private var styleText1: Int = R.style.TextAppearance_Soramitsu_Neu_Semibold_15
    private var styleText2: Int = R.style.TextAppearance_Soramitsu_Neu_Semibold_15
    private var paddingRowTop: Int = 0
    private var paddingRowBottom: Int = 0
    private var paddingRowStart: Int = 0
    private var paddingRowEnd: Int = 0

    init {
        val typedArray = context.theme.obtainStyledAttributes(
            attrs, R.styleable.RowsView, defStyleAttr, defStyleRes
        )
        typedArray.use {
            rowsCount = it.getInt(R.styleable.RowsView_rowsCount, 1)
            val rowType = it.getInt(R.styleable.RowsView_rowType, 0)
            paddingRowTop = it.getDimension(R.styleable.RowsView_rowsTopPadding, 16f).toInt()
            paddingRowBottom = it.getDimension(R.styleable.RowsView_rowsBottomPadding, 24f).toInt()
            styleTitle = it.getResourceId(
                R.styleable.RowsView_rowTitleTextAppearance,
                R.style.TextAppearance_Soramitsu_Neu_Light_11
            )
            styleText1 = it.getResourceId(
                R.styleable.RowsView_rowText1TextAppearance,
                R.style.TextAppearance_Soramitsu_Neu_Semibold_15
            )
            styleText2 = it.getResourceId(
                R.styleable.RowsView_rowText2TextAppearance,
                R.style.TextAppearance_Soramitsu_Neu_Semibold_15
            )
            inflateRows(RowType.values()[rowType], rowsCount)
        }
    }

    enum class RowType(@LayoutRes val layout: Int) {
        LINE(R.layout.view_row_type_line), TRIANGLE(R.layout.view_row_type_triangle)
    }

    private fun invalidateView() {
        this.removeAllViews()
        rowsCount = 0
    }

    private fun getRowAt(index: Int): View? = runCatching { getChildAt(index) }.getOrNull()

    fun setTextAppearance(@StyleRes title: Int, @StyleRes t1: Int, @StyleRes t2: Int = t1) {
        styleTitle = title
        styleText1 = t1
        styleText2 = t2
    }

    fun setRowPadding(
        @DimenRes start: Int,
        @DimenRes top: Int,
        @DimenRes end: Int,
        @DimenRes bottom: Int
    ) {
        paddingRowTop = resources.getDimension(top).toInt()
        paddingRowBottom = resources.getDimension(bottom).toInt()
        paddingRowStart = resources.getDimension(start).toInt()
        paddingRowEnd = resources.getDimension(end).toInt()
    }

    fun inflateRows(rowType: RowType, count: Int) {
        invalidateView()
        repeat(count) {
            val container = LayoutInflater.from(context).inflate(rowType.layout, this, false)
            val titleView = container.findViewById<TextView>(R.id.tvRowLineTitle)
            TextViewCompat.setTextAppearance(titleView, styleTitle)
            val text1View = container.findViewById<TextView>(R.id.tvRowLineText1)
            TextViewCompat.setTextAppearance(text1View, styleText1)
            val text2View = container.findViewById<TextView>(R.id.tvRowLineText2)
            TextViewCompat.setTextAppearance(text2View, styleText2)
            container.setPaddingRelative(
                paddingRowStart,
                paddingRowTop,
                paddingRowEnd,
                paddingRowBottom
            )
            addView(container)
        }
        rowsCount = count
    }

    fun inflateAndAddRow(
        rowType: RowType,
        titleText: String,
        text1: String,
        text2: String? = null
    ) {
        val container = LayoutInflater.from(context).inflate(rowType.layout, this, false)
        val titleView = container.findViewById<TextView>(R.id.tvRowLineTitle)
        TextViewCompat.setTextAppearance(titleView, styleTitle)
        titleView.text = titleText
        val text1View = container.findViewById<TextView>(R.id.tvRowLineText1)
        TextViewCompat.setTextAppearance(text1View, styleText1)
        text1View.text = text1
        val text2View = container.findViewById<TextView>(R.id.tvRowLineText2)
        TextViewCompat.setTextAppearance(text2View, styleText2)
        text2View.text = text2
        container.setPaddingRelative(
            paddingRowStart,
            paddingRowTop,
            paddingRowEnd,
            paddingRowBottom
        )
        addView(container)
        rowsCount++
    }

    fun updateValuesInRow(
        rowIndex: Int,
        titleText: String,
        text1: String,
        text2: String? = null,
        @DrawableRes image: Int? = null
    ) {
        val container = getRowAt(rowIndex) ?: return
        val titleView = container.findViewById<TextView>(R.id.tvRowLineTitle)
        titleView.text = titleText
        titleView.setCompoundDrawablesRelativeWithIntrinsicBounds(
            0, 0, image ?: 0, 0
        )
        val text1View = container.findViewById<TextView>(R.id.tvRowLineText1)
        text1View.text = text1
        val text2View = container.findViewById<TextView>(R.id.tvRowLineText2)
        text2View.text = text2
    }

    fun updateValuesInRow(
        rowIndex: Int,
        titleText: String,
        text1: SpannableString,
        text2: SpannableString? = null,
        @DrawableRes image: Int? = null
    ) {
        val container = getRowAt(rowIndex) ?: return
        val titleView = container.findViewById<TextView>(R.id.tvRowLineTitle)
        titleView.text = titleText
        titleView.setCompoundDrawablesRelativeWithIntrinsicBounds(
            0, 0, image ?: 0, 0
        )
        val text1View = container.findViewById<TextView>(R.id.tvRowLineText1)
        text1View.text = text1
        val text2View = container.findViewById<TextView>(R.id.tvRowLineText2)
        text2View.text = text2
    }

    fun setOnClickListener(rowIndex: Int, listener: (title: String) -> Unit) {
        val container = getRowAt(rowIndex) ?: return
        container.setOnClickListener {
            listener.invoke(container.findViewById<TextView>(R.id.tvRowLineTitle).text.toString())
        }
    }
}
