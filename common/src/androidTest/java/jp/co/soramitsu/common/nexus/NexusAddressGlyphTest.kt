package jp.co.soramitsu.common.nexus

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.presentation.compose.theme.tokens.paragraphM
import jp.co.soramitsu.common.presentation.compose.theme.tokens.paragraphS
import jp.co.soramitsu.common.presentation.compose.theme.tokens.textM
import jp.co.soramitsu.common.presentation.compose.theme.tokens.textXS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SORA Nexus and Taira addresses mix Base58 with halfwidth katakana, and every one starts
 * "sorauﾛ1" or "testuﾛ1". The halfwidth RO (U+FF9B) is a narrow square in every font, so it was
 * taken for a missing-glyph box. Inter, the wallet's text font, has no katakana: the system CJK
 * font draws them, and must keep drawing all of them in each style the address screens use.
 */
@RunWith(AndroidJUnit4::class)
class NexusAddressGlyphTest {

    private val resolver = createFontFamilyResolver(InstrumentationRegistry.getInstrumentation().targetContext)

    // Receive on / Send on rows, the network's receive sheet and details, the send dialog and
    // transfer rows (Material body text), and the Connect screen's payload.
    private val addressStyles = mapOf(
        "textXS" to textXS,
        "paragraphS" to paragraphS,
        "paragraphM" to paragraphM,
        "textM" to textM,
        "monospace" to TextStyle(fontFamily = FontFamily.Monospace),
    )

    private fun paint(style: TextStyle) = Paint().apply {
        typeface = resolver.resolve(style.fontFamily, style.fontWeight ?: FontWeight.Normal).value as Typeface
        textSize = 64f
    }

    @Test
    fun everyAddressCharacterHasAGlyphInEveryAddressStyle() {
        val missing = addressStyles.flatMap { (name, style) ->
            val paint = paint(style)
            IrohaAddressCodec.alphabet.filterNot { paint.hasGlyph(it.toString()) }.map { "$name: $it" }
        }

        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun theHalfwidthRoIsDrawnAsItselfAndNotAsTheMissingGlyphBox() {
        val paint = paint(textXS)
        val unassigned = "͸"
        // The check can fail: this device does report a character that no font has.
        assertFalse(paint.hasGlyph(unassigned))

        val ro = draw(paint, "ﾛ")
        val missingGlyph = draw(paint, unassigned)
        try {
            assertFalse(ro.sameAs(missingGlyph))
        } finally {
            ro.recycle()
            missingGlyph.recycle()
        }
    }

    private fun draw(paint: Paint, text: String): Bitmap =
        Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply {
                drawColor(Color.WHITE)
                drawText(text, 16f, 72f, paint)
            }
        }
}
