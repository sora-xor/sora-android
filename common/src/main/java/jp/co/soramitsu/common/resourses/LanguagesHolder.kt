/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.resourses

import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.Flavor

/*
The locale tags must follow the BCP47 syntax, which is usually {language subtag}–{script subtag}–{country subtag}. Anything other than that will be filtered out by the system and won't be visible in the system settings.
https://android-developers.googleblog.com/2022/11/per-app-language-preferences-part-1.html
 */
class LanguagesHolder {

    companion object {
        private const val LANGUAGE_PART_INDEX = 0
        private const val COUNTRY_PART_INDEX = 1
    }

    private val russian = Language("ru", R.string.common_russian, R.string.common_russian_native)
    private val english = Language("en", R.string.common_english, R.string.common_english_native)
    private val spanish = Language("es", R.string.common_spanish, R.string.common_spanish_native)
    private val french = Language("fr", R.string.common_french, R.string.common_french_native)
    private val german = Language("de", R.string.common_german, R.string.common_german_native)
    private val norwegian =
        Language("nb", R.string.common_norwegian, R.string.common_norwegian_native)
    private val chinese = Language("zh-CN", R.string.common_chinese, R.string.common_chinese_native)
    private val indonesian =
        Language("in", R.string.common_indonesian, R.string.common_indonesian_native)
    private val turkish = Language("tr", R.string.common_turkish, R.string.common_turkish_native)
    private val arab = Language("ar", R.string.common_arab, R.string.common_arab_native)
    private val hebrew = Language("he", R.string.common_hebrew, R.string.common_hebrew_native)
    private val persian = Language("fa", R.string.common_persian, R.string.common_persian_native)
    private val serbian = Language("sr", R.string.common_serbian, R.string.common_serbian_native)
    private val vietnamese =
        Language("vi", R.string.common_vietnamese, R.string.common_vietnamese_native)
    private val malay = Language("ms-MY", R.string.common_malay, R.string.common_malay_native)

    private val bashkir = Language("ba", R.string.common_bashkir, R.string.common_bashkir_native)
    private val spanish_columbia =
        Language("es-CO", R.string.common_spanish_colombia, R.string.common_spanish_colombia_native)
    private val estonian = Language("et", R.string.common_estonian, R.string.common_estonian_native)
    private val finnish = Language("fi-FI", R.string.common_finnish, R.string.common_finnish_native)
    private val fillipino =
        Language("fil", R.string.common_filipino, R.string.common_filipino_native)
    private val croatian = Language("hr", R.string.common_croatian, R.string.common_croatian_native)
    private val italian = Language("it-IT", R.string.common_italian, R.string.common_italian_native)
    private val korean = Language("ko", R.string.common_korean, R.string.common_korean_native)
    private val swedish = Language("sv-SE", R.string.common_swedish, R.string.common_swedish_native)
    private val thai = Language("th", R.string.common_thai, R.string.common_thai_native)
    private val ukrainian =
        Language("uk", R.string.common_ukrainian, R.string.common_ukrainian_native)
    private val japanese = Language("ja", R.string.common_japanese, R.string.common_japanese_native)
    private val chinese_taiwan =
        Language("zh-TW", R.string.common_chinesetaiwan, R.string.common_chinese_taiwan_native)
    private val khmer = Language("km-KH", R.string.common_khmer, R.string.common_khmer_native)

    private val availableLanguages =
        mutableListOf(english, russian, spanish, french, german, norwegian, chinese, indonesian, turkish)

    private val nonProdLanguages =
        mutableListOf(arab, hebrew, persian, serbian, vietnamese, malay)

    fun getCurrentLocale(): Locale {
        return AppCompatDelegate.getApplicationLocales()[0] ?: Locale.getDefault()
    }

    private fun getCurrentLanguage(): String {
        val languageTag = getCurrentLocale().toLanguageTag()
        return if (languageTag == "id" || languageTag == "ind") { // iso code for Indonesian language differs between in, id and ind, so we map it to one value
            "in"
        } else {
            languageTag
        }
    }

    @MainThread
    fun setCurrentLanguage(l: String) {
        val locale = LocaleListCompat.forLanguageTags(l)
        AppCompatDelegate.setApplicationLocales(locale)
    }

    fun getLanguages(): Pair<List<Language>, Int> {
        val list = if (BuildUtils.isFlavors(Flavor.PROD)) availableLanguages else availableLanguages + nonProdLanguages
        val cur = getCurrentLanguage()
        val fullMatch = list.indexOfFirst { it.iso.equals(cur, true) }
        if (fullMatch != -1) return list to fullMatch
        val curTag = languageTag(cur)
        val tagMatch = list.indexOfFirst { languageTag(it.iso).equals(curTag, true) }
        if (tagMatch != -1) return list to tagMatch
        return list to ((list.indexOfFirst { it.iso.equals("en", true) }).takeIf { it != -1 } ?: 0)
    }

    private fun languageTag(l: String) = l.substringBefore("-").substringBefore("_")

    private fun mapLanguageToLocale(language: String): Locale {
        val codes = language.split("_")

        return if (hasCountryCode(codes)) {
            Locale(codes[LANGUAGE_PART_INDEX], codes[COUNTRY_PART_INDEX])
        } else {
            Locale(language)
        }
    }

    private fun hasCountryCode(codes: List<String>) = codes.size != 1
}
