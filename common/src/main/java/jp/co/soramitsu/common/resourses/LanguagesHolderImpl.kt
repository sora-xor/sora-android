package jp.co.soramitsu.common.resourses

import jp.co.soramitsu.common.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanguagesHolderImpl @Inject constructor() : LanguagesHolder {

    companion object {
        private val RUSSIAN = Language("ru", R.string.common_russian, R.string.common_russian_native)
        private val ENGLISH = Language("en", R.string.common_english, R.string.common_english_native)
        private val BASHKIR = Language("ba", R.string.common_bashkir, R.string.common_bashkir_native)
        private val SPANISH = Language("es", R.string.common_spanish, R.string.common_spanish_native)

        private val availableLanguages = mutableListOf(RUSSIAN, ENGLISH, SPANISH, BASHKIR)
    }

    override fun getEnglishLang(): Language {
        return ENGLISH
    }

    override fun getLanguages(): List<Language> {
        return availableLanguages
    }
}