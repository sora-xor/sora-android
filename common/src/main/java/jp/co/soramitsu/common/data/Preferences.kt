package jp.co.soramitsu.common.data

import android.content.SharedPreferences

class Preferences(
    private val preferences: SharedPreferences
) {

    companion object {
        private const val KEY_CURRENT_LANGUAGE = "current_language"
    }

    fun putString(field: String, value: String) {
        preferences
            .edit()
            .putString(field, value)
            .apply()
    }

    fun getString(field: String): String {
        return preferences.getString(field, "") ?: ""
    }

    @JvmOverloads fun getBoolean(field: String, defaultValue: Boolean = false): Boolean {
        return preferences.getBoolean(field, defaultValue)
    }

    fun putBoolean(field: String, value: Boolean) {
        preferences.edit().putBoolean(field, value).apply()
    }

    fun getInt(field: String, defaultValue: Int): Int {
        return preferences.getInt(field, defaultValue)
    }

    fun putInt(field: String, value: Int) {
        preferences.edit().putInt(field, value).apply()
    }

    fun getLong(field: String, defaultValue: Long): Long {
        return preferences.getLong(field, defaultValue)
    }

    fun putLong(field: String, value: Long) {
        preferences.edit().putLong(field, value).apply()
    }

    fun getFloat(field: String, defaultValue: Float): Float {
        return preferences.getFloat(field, defaultValue)
    }

    fun putFloat(field: String, value: Float) {
        preferences.edit().putFloat(field, value).apply()
    }

    fun getDouble(field: String, defaultValue: Double): Double {
        return java.lang.Double.longBitsToDouble(preferences.getLong(field, java.lang.Double.doubleToLongBits(defaultValue)))
    }

    fun putDouble(field: String, value: Double) {
        preferences.edit().putLong(field, java.lang.Double.doubleToRawLongBits(value)).apply()
    }

    fun clearAll() {
        preferences.edit().clear().apply()
    }

    fun getCurrentLanguage(): String {
        return preferences.getString(KEY_CURRENT_LANGUAGE, "") ?: ""
    }

    fun saveCurrentLanguage(language: String) {
        preferences.edit().putString(KEY_CURRENT_LANGUAGE, language).apply()
    }
}