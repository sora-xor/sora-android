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

package jp.co.soramitsu.common.presentation.compose.neon

import android.graphics.Bitmap
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.random.Random
import jp.co.soramitsu.common.presentation.compose.theme.neon.FxPolicy
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.presentation.compose.theme.neon.NeonTokens
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon

/*
 * SORA Neon effect modifiers (design spec 4). Everything here is static and drawn from cached
 * brushes; nothing uses RenderEffect or a backdrop blur, so it renders the same on API 26+.
 */

/**
 * Static aurora behind all content (design spec 4.2.1): the canvas colour, three faint radial
 * washes (two in Safe Mode) and a 4% grain. Brushes are rebuilt only when the size changes.
 * This replaces the full-screen `bg_image` bitmap (about 19.5 MB decoded at xxxhdpi).
 */
fun Modifier.neonAurora(n: NeonTokens, fx: FxPolicy, safe: Boolean = false): Modifier =
    drawWithCache {
        val w = size.width
        val h = size.height
        fun blob(c: Color, x: Float, y: Float, r: Float) = Brush.radialGradient(
            colors = listOf(c, c.copy(alpha = 0f)),
            center = Offset(x * w, y * h),
            radius = (r * w).coerceAtLeast(1f),
        )
        val blobs = if (safe) {
            listOf(blob(n.safeAuroraCyan, .15f, -.05f, .9f), blob(n.safeAuroraMint, .85f, .25f, .8f))
        } else {
            listOf(
                blob(n.auroraCyan, .15f, -.05f, .9f),
                blob(n.auroraMagenta, 1f, .20f, .8f),
                blob(n.auroraRed, .30f, .32f, .7f),
            )
        }
        val grain = if (fx.ambientDetail) {
            ShaderBrush(ImageShader(NeonGrain.bitmap, TileMode.Repeated, TileMode.Repeated))
        } else {
            null
        }
        onDrawBehind {
            drawRect(n.canvas)
            blobs.forEach { drawRect(it) }
            grain?.let { drawRect(it, alpha = n.grainAlpha) }
        }
    }

/** Full-size aurora layer. Put it first in a `Box`; it is decorative and has no semantics. */
@Composable
fun NeonBackground(modifier: Modifier = Modifier, safe: Boolean = false) {
    val n = MaterialTheme.neon
    val fx = LocalFx.current
    Box(
        modifier
            .fillMaxSize()
            .clearAndSetSemantics { }
            .neonAurora(n, fx, safe),
    )
}

/**
 * Restrained 24 dp grid for hero surfaces only (Splash, Welcome, HeroCard; design spec 4.2.3).
 * Fades radially from the top-end corner through a brush, so there is no mask or offscreen layer.
 */
fun Modifier.neonGrid(n: NeonTokens, enabled: Boolean, cell: Dp = 24.dp): Modifier =
    if (!enabled) {
        this
    } else {
        drawWithCache {
            val center = Offset(size.width * .85f, 0f)
            val radius = (size.width * 1.1f).coerceAtLeast(1f)
            val minor = Brush.radialGradient(listOf(n.gridMinor, n.gridMinor.copy(alpha = 0f)), center, radius)
            val major = Brush.radialGradient(listOf(n.gridMajor, n.gridMajor.copy(alpha = 0f)), center, radius)
            val step = cell.toPx().coerceAtLeast(1f)
            val xs = generateSequence(0f) { it + step }.takeWhile { it <= size.width }.toList()
            val ys = generateSequence(0f) { it + step }.takeWhile { it <= size.height }.toList()
            onDrawBehind {
                xs.forEachIndexed { i, x ->
                    drawLine(if (i % 4 == 0) major else minor, Offset(x + .5f, 0f), Offset(x + .5f, size.height), 1f)
                }
                ys.forEachIndexed { i, y ->
                    drawLine(if (i % 4 == 0) major else minor, Offset(0f, y + .5f), Offset(size.width, y + .5f), 1f)
                }
            }
        }
    }

/**
 * "Fake glass" for cards and panels (design spec 4.3.1): translucent fill, a top sheen and a
 * gradient hairline, with no runtime blur. Falls back to the opaque `glass.solid` on MINIMAL,
 * under high contrast, or when [opaqueColor] is given (secrets and review surfaces).
 */
fun Modifier.neonGlass(
    shape: Shape,
    n: NeonTokens,
    fx: FxPolicy,
    border: Brush? = n.borderGlass,
    opaqueColor: Color? = null,
): Modifier {
    val opaque = opaqueColor != null || fx.opaqueGlass || fx.highContrast
    val fill = opaqueColor ?: if (opaque) n.glassSolid else n.glassFill
    val shadow = n.cardShadow
    return this
        .then(if (shadow != null && fx.shadows) Modifier.dropShadow(shape, shadow) else Modifier)
        .clip(shape)
        .background(fill)
        .then(if (opaque) Modifier else Modifier.background(n.glassHighlight))
        .then(if (border != null) Modifier.neonBorder(shape, border, fx) else Modifier)
}

/**
 * Gradient hairline (design spec 4.4). 1 dp, 2 dp under high contrast. The brush runs corner to
 * corner and is never mirrored in RTL.
 */
fun Modifier.neonBorder(shape: Shape, brush: Brush, fx: FxPolicy, width: Dp = 1.dp): Modifier =
    border(if (fx.highContrast) width * 2 else width, brush, shape)

/**
 * Android chrome (design spec 4.3.2): bars stay transparent at the top of a scroll and fade to
 * the 94% chrome tint with a bottom hairline once content scrolls under them. [visibility] is
 * read in the draw phase only, so animating it does not recompose.
 */
fun Modifier.neonChrome(n: NeonTokens, visibility: () -> Float): Modifier = drawBehind {
    val a = visibility().coerceIn(0f, 1f)
    if (a > 0f) {
        drawRect(n.chromeTint, alpha = a)
        drawRect(
            brush = n.borderChromeTop,
            topLeft = Offset(0f, size.height - 1f),
            size = Size(size.width, 1f),
            alpha = a,
        )
    }
}

/**
 * The HeroCard silhouette: rounded corners with a 14 dp 45-degree cut at the top-right corner
 * (design spec 6). The cut is a brand mark and is not mirrored in RTL.
 */
class ChamferShape(private val radius: Dp = 28.dp, private val cut: Dp = 14.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        val w = size.width
        val h = size.height
        return Outline.Generic(
            Path().apply {
                moveTo(r, 0f)
                lineTo(w - c, 0f)
                lineTo(w, c)
                lineTo(w, h - r)
                arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
                lineTo(r, h)
                arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
                lineTo(0f, r)
                arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
                close()
            },
        )
    }

    override fun equals(other: Any?): Boolean =
        other is ChamferShape && other.radius == radius && other.cut == cut

    override fun hashCode(): Int = 31 * radius.hashCode() + cut.hashCode()
}

/**
 * Dialog and sheet windows (design spec 4.3.2): a cross-window blur behind the window on FULL
 * tier devices (API 31+ with cross-window blur enabled), otherwise a plain dim. Only the top-most
 * window should call this.
 */
fun Window.applyNeonBlurBehind(fx: FxPolicy, dark: Boolean) {
    val blurred = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && fx.realBlur && applyBlurBehindApi31(this)
    if (!blurred) clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    setDimAmount(
        when {
            blurred && dark -> .40f
            blurred -> .24f
            dark -> .55f
            else -> .32f
        },
    )
}

@RequiresApi(Build.VERSION_CODES.S)
private fun applyBlurBehindApi31(window: Window): Boolean {
    if (!window.windowManager.isCrossWindowBlurEnabled) return false
    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    window.attributes = window.attributes.apply {
        blurBehindRadius = (24 * window.context.resources.displayMetrics.density).roundToInt()
    }
    return true
}

/**
 * 128 x 128 monochrome noise used as the aurora grain. Generated once per process from a fixed
 * seed instead of shipping an image asset (16 K pixels, about 64 KB).
 */
internal object NeonGrain {

    private const val SIZE = 128

    val bitmap: ImageBitmap by lazy {
        val random = Random(SEED)
        val pixels = IntArray(SIZE * SIZE) {
            val v = random.nextInt(256)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        Bitmap.createBitmap(pixels, SIZE, SIZE, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    private const val SEED = 0x50AA
}
