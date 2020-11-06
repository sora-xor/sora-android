/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.detail.project.gallery

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

const val DURATION_FORMAT = "%02d:%02d"

class DurationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatTextView(context, attrs, defStyle) {

    fun setDuration(duration: Int) {
        text = formatToTime(duration)
    }

    private fun formatToTime(duration: Int): String {
        val minutes = duration / 60
        val seconds = duration % 60
        return String.format(DURATION_FORMAT, minutes, seconds)
    }
}