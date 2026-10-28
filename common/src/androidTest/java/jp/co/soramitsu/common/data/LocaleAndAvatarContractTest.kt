package jp.co.soramitsu.common.data

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAddress
import org.junit.*
import org.junit.Assert.*
import java.util.Locale

class LocaleAndAvatarContractTest {
    private val original = Locale.getDefault()
    @After fun restoreLocale() { Locale.setDefault(original) }
    @Test fun exactAndRegionalSupportedLocalesSelectTheirOwnLanguage() {
        for ((tag, expected) in listOf("fr" to "fr", "fr-CA" to "fr", "zh-CN" to "zh-CN", "id" to "in")) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            val holder = LanguagesHolder(); val (languages, selected) = holder.getLanguages()
            assertEquals(expected, languages[selected].iso)
            assertEquals(Locale.getDefault(), holder.getCurrentLocale())
        }
    }
    @Test fun unsupportedLocaleFallsBackToEnglishWithoutOfferingDevelopmentLanguages() {
        Locale.setDefault(Locale.forLanguageTag("zz-ZZ"))
        val (languages, selected) = LanguagesHolder().getLanguages()
        assertEquals("en", languages[selected].iso)
        assertFalse(languages.any { it.iso == "akk" || it.iso == "egy-Egyp" })
    }
    @Test fun publicAccountAvatarIsDeterministicAndHasTheRequestedPixelDimensions() {
        val resources = ResourceManager()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            resources.onActivityCreated(object : android.app.Activity() {
                override fun getResources() = instrumentation.targetContext.resources
            }, null)
        }
        val generator = AccountAvatarGenerator(resources)
        val address = ByteArray(32) { (it + 1).toByte() }.toAddress(69.toShort())
        val first = generator.createAvatar(address, 32); val second = generator.createAvatar(address, 32)
        val pixels = resources.dp2px(32)
        assertEquals(pixels, first.intrinsicWidth); assertEquals(pixels, first.intrinsicHeight)
        fun bitmap(picture: android.graphics.drawable.PictureDrawable): Bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888).also {
            picture.setBounds(0, 0, pixels, pixels); picture.draw(Canvas(it))
        }
        val left = bitmap(first); val right = bitmap(second)
        try { assertTrue(left.sameAs(right)) } finally { left.recycle(); right.recycle() }
    }
}
