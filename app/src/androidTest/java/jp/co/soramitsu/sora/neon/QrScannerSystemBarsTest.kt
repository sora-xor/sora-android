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

import android.Manifest
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.HardwareRenderer
import android.os.Build
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import jp.co.soramitsu.feature_assets_api.presentation.QrScanPurpose
import jp.co.soramitsu.feature_assets_impl.presentation.screens.scan.QRCodeScannerActivity
import jp.co.soramitsu.feature_assets_impl.presentation.screens.scan.QrScannerContractFactoryImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The QR scanner is always dark (camera under a dark scrim). With the light app theme, AppTheme
 * asked for dark status-bar icons, which drew on the dark scrim (about 1.3-1.5:1), and before
 * Android 15 a light status-bar strip sat above the scanner. The scanner now draws edge to edge
 * with light system-bar icons in every theme. This launches the real activity in light mode,
 * checks the system-bar appearance and saves a screenshot for review.
 */
@RunWith(AndroidJUnit4::class)
class QrScannerSystemBarsTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun scannerUsesLightSystemBarIconsInLightTheme() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        // Instrumented processes may skip hardware drawing; the screenshot needs the overlay.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.runOnMainSync { HardwareRenderer.setDrawingEnabled(true) }
        }
        val previousNightMode = AppCompatDelegate.getDefaultNightMode()
        instrumentation.runOnMainSync {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        try {
            val intent = QrScannerContractFactoryImpl()
                .create(QrScanPurpose.GENERAL)
                .createIntent(context, Unit)
            ActivityScenario.launchActivityForResult<QRCodeScannerActivity>(intent).use { scenario ->
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val night = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    assertEquals("the scanner must run in the light app theme", Configuration.UI_MODE_NIGHT_NO, night)
                    val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    assertFalse("dark status-bar icons over the dark scanner", bars.isAppearanceLightStatusBars)
                    assertFalse("dark navigation-bar icons over the dark scanner", bars.isAppearanceLightNavigationBars)
                }
                SystemClock.sleep(1500)
                instrumentation.uiAutomation.takeScreenshot()?.let { shot ->
                    context.getExternalFilesDir("neon-review")?.let { dir ->
                        File(dir, "qr-scanner-light-api${android.os.Build.VERSION.SDK_INT}.png").outputStream().use {
                            shot.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    }
                }
                scenario.onActivity { it.finish() }
            }
        } finally {
            instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(previousNightMode) }
        }
    }
}
