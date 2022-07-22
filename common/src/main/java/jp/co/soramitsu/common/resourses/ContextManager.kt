/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.resourses

import android.content.Context
import java.lang.ref.WeakReference
import java.util.Locale

object ContextManager {

    private val LANGUAGE_PART_INDEX = 0
    private val COUNTRY_PART_INDEX = 1
    private val prefsLanguage = "sora_prefs"
    private val prefCurrentLanguage = "current_language"
    private lateinit var lc: WeakReference<Context>

    fun setLocale(context: Context): Context {
        lc = WeakReference(context)
        return updateResources(context)
    }

    fun getLocale(): Locale {
        return if (Locale.getDefault().displayLanguage != "ba") Locale.getDefault() else Locale("ru")
    }

    private fun updateResources(context: Context): Context {
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

        val c = context.createConfigurationContext(configuration)
        lc = WeakReference(c)

        return c
    }

    fun getCurrentLanguage(): String? {
        val prefs = lc.get()?.getSharedPreferences(prefsLanguage, Context.MODE_PRIVATE)
        return prefs?.getString(prefCurrentLanguage, null)
    }

    fun setCurrentLanguage(l: String): String {
        val prefs = lc.get()?.getSharedPreferences(prefsLanguage, Context.MODE_PRIVATE)
        prefs?.edit()?.putString(prefCurrentLanguage, l)?.apply()
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
