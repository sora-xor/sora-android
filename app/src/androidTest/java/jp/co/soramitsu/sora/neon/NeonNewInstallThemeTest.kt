/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.sora.neon

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.domain.DarkThemeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NeonNewInstallThemeTest {
    @Test fun firstInstallIsDarkButAnyHistoricalDataKeepsItsPreviousDefault() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = instrumentation.targetContext
        val oldMode = AppCompatDelegate.getDefaultNightMode()
        try {
            for (evidence in listOf<String?>(null, "databases/app.db", "shared_prefs/sora_prefs.xml",
                "files/datastore/sora_prefs_datastore.preferences_pb", "no_backup/preserved-generation")) {
                val id = UUID.randomUUID().toString()
                val root = File(base.cacheDir, "neon-theme-$id").apply { mkdirs() }
                val prefName = "neon-theme-test-$id"
                val wrapper = object : ContextWrapper(base) {
                    override fun getApplicationInfo() = ApplicationInfo(base.applicationInfo).apply { dataDir = root.path }
                    override fun getFilesDir() = File(root, "files").apply { mkdirs() }
                    override fun getNoBackupFilesDir() = File(root, "no_backup").apply { mkdirs() }
                    override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefName, mode)
                }
                val original = evidence?.let { File(root, it).apply { parentFile!!.mkdirs(); writeText("public fixture") } }
                try {
                    instrumentation.runOnMainSync { DarkThemeManager.captureNewInstallDefault(wrapper) }
                    val mirror = base.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    assertEquals(evidence == null, mirror.getBoolean("new_install_dark_default", false))
                    original?.let { assertEquals("public fixture", it.readText()) }
                    // Removing old app files later must never reclassify this upgraded install.
                    original?.delete()
                    instrumentation.runOnMainSync { DarkThemeManager.captureNewInstallDefault(wrapper) }
                    assertEquals(evidence == null, mirror.getBoolean("new_install_dark_default", false))
                    // The user's explicit light preference always wins on the next cold start.
                    mirror.edit().putInt("night_mode", AppCompatDelegate.MODE_NIGHT_NO).commit()
                    instrumentation.runOnMainSync { DarkThemeManager.applyCachedNightMode(wrapper) }
                    assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode())
                } finally { root.deleteRecursively(); base.deleteSharedPreferences(prefName) }
            }
        } finally { instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(oldMode) } }
    }
}
