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

import jp.co.soramitsu.sora.testutil.NativeDisplayCapture

import android.Manifest
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.HardwareRenderer
import android.os.Build
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
                val shot = NativeDisplayCapture.capture()
                try {
                    val dir = requireNotNull(context.getExternalFilesDir("neon-review"))
                    File(dir, "qr-scanner-light-api${android.os.Build.VERSION.SDK_INT}.png").outputStream().use {
                        check(shot.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Scanner capture PNG could not be written" }
                    }
                } finally {
                    shot.recycle()
                }
                scenario.onActivity { it.finish() }
            }
        } finally {
            instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(previousNightMode) }
        }
    }
}
