/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.sora.splash

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
import java.io.File
import jp.co.soramitsu.sora.databinding.ActivitySplashBinding
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import jp.co.soramitsu.common.R as CommonR

/**
 * The splash recovery panel must be readable in both themes. Before SORA Neon the night panel
 * drew #2D2926 text on #26262D (1.04:1) and its buttons fell back to Material purple. This
 * inflates the real layout under the app theme in light and dark, checks every text colour
 * against the colour it sits on, and saves a rendering for review.
 */
@RunWith(AndroidJUnit4::class)
class SplashRecoveryPanelThemeTest {

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

    private fun textViews(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { textViews(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun check(night: Boolean) {
        val label = if (night) "dark" else "light"
        var failures = emptyList<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val ctx = themed(night)
            val binding = ActivitySplashBinding.inflate(LayoutInflater.from(ctx))
            binding.animationView.visibility = View.GONE
            binding.walletRecoveryPanel.visibility = View.VISIBLE
            binding.continueLegacyWalletButton.visibility = View.VISIBLE
            binding.walletRecoveryCodeTextView.text =
                ctx.getString(jp.co.soramitsu.sora.R.string.wallet_upgrade_recovery_code, "SOURCE_CHANGED")

            val surface = ContextCompat.getColor(ctx, CommonR.color.neon_surface)
            failures = textViews(binding.walletRecoveryPanel).mapNotNull { view ->
                val background = (view as? MaterialButton)?.backgroundTintList
                    ?.getColorForState(view.drawableState, surface)
                    ?.takeIf { Color.alpha(it) == 255 }
                    ?: surface
                val ratio = contrast(view.currentTextColor, background)
                if (ratio < 4.5) "$label '${view.text}' is %.2f:1".format(ratio) else null
            }

            val width = 1080
            val height = 2400
            val root = binding.root
            root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            target.getExternalFilesDir("neon-review")?.let { dir ->
                File(dir, "splash-recovery-panel-$label.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun recoveryPanelIsReadableInLightTheme() = check(night = false)

    @Test
    fun recoveryPanelIsReadableInDarkTheme() = check(night = true)
}
