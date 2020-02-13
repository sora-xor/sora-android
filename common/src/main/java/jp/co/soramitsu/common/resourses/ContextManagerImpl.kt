package jp.co.soramitsu.common.resourses

import android.content.Context
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.di.app.SHARED_PREFERENCES_FILE
import jp.co.soramitsu.common.util.SingletonHolder
import java.util.Locale
import javax.inject.Singleton

@Singleton
class ContextManagerImpl private constructor(
    private var context: Context,
    private val languagesHolder: LanguagesHolder
) : ContextManager {

    companion object : SingletonHolder<ContextManagerImpl, Context, LanguagesHolder>(::ContextManagerImpl)

    override fun getContext(): Context {
        return context
    }

    override fun setLocale(context: Context): Context {
        return updateResources(context)
    }

    override fun getLocale(): Locale {
        return if (Locale.getDefault().displayLanguage != "ba") Locale.getDefault() else Locale("ru")
    }

    private fun updateResources(context: Context): Context {
        val prefs = Preferences(context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE))

        val currentLanguage = if (prefs.getCurrentLanguage().isEmpty()) {
            val currentLocale = Locale.getDefault()
            if (languagesHolder.getLanguages().map { it.iso }.contains(currentLocale.language)) {
                currentLocale.language
            } else {
                languagesHolder.getEnglishLang().iso
            }
        } else {
            prefs.getCurrentLanguage()
        }

        prefs.saveCurrentLanguage(currentLanguage)

        val locale = Locale(currentLanguage)
        Locale.setDefault(locale)

        val configuration = context.resources.configuration
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)

        this.context = context.createConfigurationContext(configuration)

        return this.context
    }

    override fun getLanguages(): LanguagesHolder {
        return languagesHolder
    }
}