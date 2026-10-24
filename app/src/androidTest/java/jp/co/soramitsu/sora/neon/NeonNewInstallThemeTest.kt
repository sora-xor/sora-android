/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
