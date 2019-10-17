/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import android.content.Context
import android.content.SharedPreferences
import javax.inject.Inject

class PrefsUtil @Inject constructor(
    context: Context,
    private val encryptionUtil: EncryptionUtil
) {
    companion object {
        private const val SHARED_PREFERENCES_FILE = "sora_prefs"
    }

    private val preferences: SharedPreferences

    init {
        preferences = context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
    }

    fun putString(field: String, value: String) {
        preferences
            .edit()
            .putString(field, value)
            .apply()
    }

    fun putEncryptedString(field: String, value: String) {
        putString(field, encryptionUtil.encrypt(value))
    }

    fun getString(field: String): String {
        return preferences.getString(field, "") ?: ""
    }

    fun getDecryptedString(field: String): String {
        val encryptedString = getString(field)

        return if (encryptedString!!.isEmpty()) "" else encryptionUtil.decrypt(encryptedString)
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
}
