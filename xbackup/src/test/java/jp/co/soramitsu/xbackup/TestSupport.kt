/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0
*/

package jp.co.soramitsu.xbackup

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import java.io.File
import java.nio.file.Files

/**
 * The Context xbackup's JVM tests use. android.jar stubs return defaults, so the
 * directories and preferences xbackup reads are supplied here. DataStore keeps one
 * instance per process, so every test shares this object.
 */
internal object TestContext : ContextWrapper(null) {
    private val root: File = Files.createTempDirectory("xbackup-test").toFile().also { it.deleteOnExit() }
    val preferences = mutableMapOf<String, FakeSharedPreferences>()

    override fun getApplicationContext(): Context = this

    override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }

    override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        preferences.getOrPut(name) { FakeSharedPreferences() }
}

/** In-memory SharedPreferences; apply and commit both write at once. */
internal class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String, defValue: String?): String? = values[key] as String? ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        values[key] as MutableSet<String>? ?: defValues

    override fun getInt(key: String, defValue: Int): Int = values[key] as Int? ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values[key] as Long? ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = values[key] as Float? ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as Boolean? ?: defValue

    override fun contains(key: String): Boolean = key in values

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        private fun put(key: String, value: Any?): SharedPreferences.Editor {
            changes[key] = value
            return this
        }

        override fun putString(key: String, value: String?) = put(key, value)

        override fun putStringSet(key: String, values: MutableSet<String>?) = put(key, values)

        override fun putInt(key: String, value: Int) = put(key, value)

        override fun putLong(key: String, value: Long) = put(key, value)

        override fun putFloat(key: String, value: Float) = put(key, value)

        override fun putBoolean(key: String, value: Boolean) = put(key, value)

        override fun remove(key: String): SharedPreferences.Editor {
            removals += key
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearAll) values.clear()
            removals.forEach(values::remove)
            changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
        }
    }
}

/** A launcher that only records what it was asked to start. */
internal class RecordingLauncher : ActivityResultLauncher<Intent>() {
    val launched = mutableListOf<Intent>()

    override fun launch(input: Intent, options: ActivityOptionsCompat?) {
        launched += input
    }

    override fun unregister() = Unit

    override val contract: ActivityResultContract<Intent, *>
        get() = throw UnsupportedOperationException()
}
