/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.resourses

import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.Flavor

object LanguagesHolder {

    private val RUSSIAN = Language("ru", R.string.common_russian, R.string.common_russian_native)
    private val ENGLISH = Language("en", R.string.common_english, R.string.common_english_native)
    private val BASHKIR = Language("ba", R.string.common_bashkir, R.string.common_bashkir_native)
    private val SPANISH = Language("es", R.string.common_spanish, R.string.common_spanish_native)
    private val SPANISH_COLUMBIA = Language("es_CO", R.string.common_spanish_colombia, R.string.common_spanish_colombia_native)
    private val ESTONIAN = Language("et", R.string.common_estonian, R.string.common_estonian_native)
    private val FINNISH = Language("fi_FI", R.string.common_finnish, R.string.common_finnish_native)
    private val FILLIPINO = Language("fil", R.string.common_filipino, R.string.common_filipino_native)
    private val CROATIAN = Language("hr", R.string.common_croatian, R.string.common_croatian_native)
    private val INDONESIAN = Language("id", R.string.common_indonesian, R.string.common_indonesian_native)
    private val ITALIAN = Language("it_IT", R.string.common_italian, R.string.common_italian_native)
    private val GERMAN = Language("de_DE", R.string.common_german, R.string.common_german_native)
    private val NORWEGIAN = Language("no", R.string.common_norwegian, R.string.common_norwegian_native)
    private val KOREAN = Language("ko", R.string.common_korean, R.string.common_korean_native)
    private val MALAY = Language("ms_MY", R.string.common_malay, R.string.common_malay_native)
    private val SWEDISH = Language("sv_SE", R.string.common_swedish, R.string.common_swedish_native)
    private val THAI = Language("th", R.string.common_thai, R.string.common_thai_native)
    private val UKRAINIAN = Language("uk", R.string.common_ukrainian, R.string.common_ukrainian_native)
    private val FRENCH = Language("fr", R.string.common_french, R.string.common_french_native)
    private val JAPANESE = Language("ja", R.string.common_japanese, R.string.common_japanese_native)
    private val CHINESE = Language("zh_CN", R.string.common_chinese, R.string.common_chinese_native)
    private val CHINESE_TAIWAN = Language("zh_TW", R.string.common_chinesetaiwan, R.string.common_chinese_taiwan_native)
    private val KHMER = Language("km_KH", R.string.common_khmer, R.string.common_khmer_native)
    private val ARAB = Language("ar", R.string.common_arab, R.string.common_arab_native)
    private val TURKISH = Language("tr", R.string.common_turkish, R.string.common_turkish_native)

    private val availableLanguages = mutableListOf(RUSSIAN, ENGLISH, SPANISH, FRENCH, GERMAN, NORWEGIAN, CHINESE, INDONESIAN, TURKISH)

    fun getEnglishLang(): Language = ENGLISH

    fun getLanguages(): List<Language> = if (BuildUtils.isFlavors(Flavor.DEVELOP, Flavor.SORALUTION)) availableLanguages + ARAB else availableLanguages
}
