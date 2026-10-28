/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.sora.neon

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import jp.co.soramitsu.common.R as CommonR

/**
 * The "Transaction submitted" toast (common custom_toast.xml) must be readable in both themes.
 * a5 first made its text follow the theme while the card stayed #FAF6F6, which drew near-white
 * text on a near-white card in dark mode (1.01:1). This inflates the real layout under the app
 * theme, reads the resolved card and text colours, and saves a rendering for review.
 */
@RunWith(AndroidJUnit4::class)
class NeonToastThemeTest {

    private val target: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun themed(night: Boolean): Context {
        val config = Configuration(target.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return ContextThemeWrapper(target.createConfigurationContext(config), CommonR.style.AppTheme)
    }

    private fun luminance(color: Int): Double {
        fun ch(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * ch(Color.red(color)) + 0.7152 * ch(Color.green(color)) + 0.0722 * ch(Color.blue(color))
    }

    private fun contrast(a: Int, b: Int): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    private fun check(night: Boolean) {
        val label = if (night) "dark" else "light"
        var ratio = 0.0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val ctx = themed(night)
            val toast = LayoutInflater.from(ctx).inflate(CommonR.layout.custom_toast, null) as TextView
            val card = ((toast.background as LayerDrawable).getDrawable(0) as GradientDrawable)
            val fill = requireNotNull(card.color).defaultColor
            ratio = contrast(toast.currentTextColor, fill)

            toast.measure(
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            toast.layout(0, 0, toast.measuredWidth, toast.measuredHeight)
            val bitmap = Bitmap.createBitmap(toast.measuredWidth, toast.measuredHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(ContextCompat.getColor(ctx, CommonR.color.neon_canvas))
            toast.draw(Canvas(bitmap))
            target.getExternalFilesDir("neon-review")?.let { dir ->
                File(dir, "toast-$label.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        assertTrue("$label toast text is %.2f:1".format(ratio), ratio >= 4.5)
    }

    @Test
    fun toastIsReadableInLightTheme() = check(night = false)

    @Test
    fun toastIsReadableInDarkTheme() = check(night = true)
}
