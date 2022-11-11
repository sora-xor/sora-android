/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.resourses

import android.content.Context
import java.util.Locale

object ContextManager {

    private val LANGUAGE_PART_INDEX = 0
    private val COUNTRY_PART_INDEX = 1
    private val prefsLanguage = "sora_prefs"
    private val prefCurrentLanguage = "current_language"

    lateinit var context: Context

    fun setLocale(context: Context): Context {
        this.context = context
        updateResources()
        return this.context
    }

    fun getLocale(): Locale {
        return if (Locale.getDefault().displayLanguage != "ba") Locale.getDefault() else Locale("ru")
    }

    private fun updateResources() {
        if (!::context.isInitialized) {
            return
        }

        val currentLanguageNullable = getCurrentLanguage()
        val currentLanguage = if (currentLanguageNullable.isNullOrEmpty()) {
            val currentLocale = Locale.getDefault()
            val result = if (LanguagesHolder.getLanguages().map { it.iso }.contains(currentLocale.language)) {
                currentLocale.language
            } else {
                LanguagesHolder.getEnglishLang().iso
            }
            setCurrentLanguage(result)
        } else {
            currentLanguageNullable
        }

        val locale = mapLanguageToLocale(currentLanguage)
        Locale.setDefault(locale)

        val configuration = context.resources.configuration
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)

        val newContext = context.createConfigurationContext(configuration)
        this.context = newContext
    }

    fun getCurrentLanguage(): String? {
        val prefs = if (::context.isInitialized) {
            context.getSharedPreferences(prefsLanguage, Context.MODE_PRIVATE)
        } else {
            null
        }
        return prefs?.getString(prefCurrentLanguage, null)
    }

    fun setCurrentLanguage(l: String): String {
        val prefs = if (::context.isInitialized) {
            context.getSharedPreferences(prefsLanguage, Context.MODE_PRIVATE)
        } else {
            null
        }
        prefs?.edit()?.putString(prefCurrentLanguage, l)?.apply()
        updateResources()
        return l
    }

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
