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

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.ViewRootForTest
import androidx.test.platform.graphics.HardwareRendererCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box
import jp.co.soramitsu.common.presentation.compose.neon.NeonBackground
import jp.co.soramitsu.feature_multiaccount_impl.presentation.enter_passphrase.EnterPassphraseScreen
import jp.co.soramitsu.feature_multiaccount_impl.presentation.RecoveryState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.RecoveryType
import jp.co.soramitsu.feature_multiaccount_impl.presentation.MnemonicConfirmationState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.mnemonic_confirmation.MnemonicConfirmationScreen
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.ExperimentalUnitApi
import jp.co.soramitsu.feature_multiaccount_impl.presentation.tutorial.TutorialScreen
import jp.co.soramitsu.feature_multiaccount_impl.presentation.TutorialScreenState
import jp.co.soramitsu.feature_main_impl.presentation.profile.appsettings.AppSettingsScreen
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup.BackupScreen
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.BackupScreenState
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeModeTags
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.presentation.compose.theme.neon.FxPolicy
import jp.co.soramitsu.common.presentation.compose.theme.neon.FxTier
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.util.PACKAGE_ID
import jp.co.soramitsu.feature_main_impl.presentation.pincode.PinCodeScreenState
import jp.co.soramitsu.feature_main_impl.presentation.pincode.PincodeScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real PIN screen, including compact layouts, maximum text, reduced motion and legacy length. */
@RunWith(AndroidJUnit4::class)
class NeonProductionScreensTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val minimal = FxPolicy(FxTier.MINIMAL, true, true, true)

    @Test fun receiveActionsRemainSeparateAndReachableOnCompactLargeTextScreens() {
        var fontScale by mutableStateOf(1f)
        var dark by mutableStateOf(true)
        var shared = 0
        var scanned = 0
        val address = "cnT65V7vJnPkR2Y2AxXwV1Bpcbab7vNaHGd3ZxXjrTpPuxvj2"
        val qr = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val state = jp.co.soramitsu.feature_assets_impl.presentation.components.compose.receiverequest.ReceiveTokenByQrScreenState(
            jp.co.soramitsu.common.presentation.compose.uikit.tokens.ScreenStatus.READY_TO_RENDER,
            qr, "My Wallet", android.graphics.drawable.BitmapDrawable(context.resources, qr), address,
        )
        rule.setContent {
            SoraAppTheme(darkTheme = dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalFx provides minimal) {
                    Box(Modifier.size(width = 320.dp, height = 520.dp)) {
                        NeonBackground()
                        jp.co.soramitsu.feature_assets_impl.presentation.components.compose.receiverequest.ReceiveTokenByQrScreen(
                            rememberScrollState(), state, {}, { shared++ }, { scanned++ }, {},
                        )
                    }
                }
            }
        }
        val share = context.getString(jp.co.soramitsu.common.R.string.common_share)
        val scan = context.getString(jp.co.soramitsu.common.R.string.commom_scan_qr)
        for (theme in listOf(true, false)) for (scale in listOf(1f, 2f)) {
            rule.runOnIdle { dark = theme; fontScale = scale }
            rule.onNodeWithText(address).performScrollTo().assertIsDisplayed()
            rule.onNodeWithText(share).performScrollTo().assertIsDisplayed().performClick()
            rule.onNodeWithText(scan).performScrollTo().assertIsDisplayed()
            val shareBounds = rule.onNodeWithText(share).fetchSemanticsNode().boundsInRoot
            val scanBounds = rule.onNodeWithText(scan).fetchSemanticsNode().boundsInRoot
            assertTrue("Share and Scan must not overlap", shareBounds.bottom <= scanBounds.top)
            rule.onNodeWithText(scan).performClick()
            capture("receive-actions-${if (theme) "dark" else "light"}-$scale")
        }
        rule.runOnIdle { assertEquals(4, shared); assertEquals(4, scanned) }
    }

    @Test fun keypadStaysReachableAtLargeTextAndDoesNotExposeDigitsToAccessibility() {
        var dark by mutableStateOf(true)
        var digits by mutableStateOf("")
        var count by mutableStateOf(6)
        rule.setContent {
            SoraAppTheme(darkTheme = dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f), LocalFx provides minimal) {
                    PincodeScreen(
                        PinCodeScreenState(toolbarTitleString = "Create a 6-digit PIN", maxDotsCount = count,
                            checkedDotsCount = digits.length, isBackButtonVisible = digits.isNotEmpty()),
                        onNumClick = { if (digits.length < count) digits += it }, onBiometricClick = {},
                        onDeleteClick = { digits = digits.dropLast(1) }, onWrongPinAnimationEnd = {}, onSwitchNode = {},
                    )
                }
            }
        }
        for (theme in listOf(true, false)) for (length in listOf(4, 6)) {
            rule.runOnIdle { dark = theme; count = length; digits = "" }
            for (digit in "123456".take(length)) {
                rule.onNodeWithTag("$PACKAGE_ID:id/$digit").performScrollTo().assertIsDisplayed().performClick()
            }
            val description = context.getString(jp.co.soramitsu.common.R.string.neon_pin_digits_entered, length, length)
            rule.onNodeWithContentDescription(description).assertExists()
            rule.onNodeWithText(digits).assertDoesNotExist()
            val zero = rule.onNodeWithTag("$PACKAGE_ID:id/0").performScrollTo().fetchSemanticsNode().boundsInRoot
            val delete = rule.onNodeWithTag("$PACKAGE_ID:id/Delete").fetchSemanticsNode().boundsInRoot
            assertTrue("Delete is separate from the zero key", delete.left >= zero.right)
            capture("pin-${if (theme) "dark" else "light"}-$length-large-text")
            rule.onNodeWithTag("$PACKAGE_ID:id/Delete").performClick()
            rule.runOnIdle { assertEquals(length - 1, digits.length) }
            rule.onNodeWithTag("$PACKAGE_ID:id/Delete").performTouchInput { longClick() }
            rule.runOnIdle { assertEquals("", digits) }
        }
    }

    @Test fun reducedMotionErrorCompletesOnceAndOfflineCheckReplacesKeypad() {
        var wrong by mutableStateOf(false)
        var migrating by mutableStateOf(false)
        var completed = 0
        rule.setContent {
            SoraAppTheme(darkTheme = true) {
                CompositionLocalProvider(LocalFx provides minimal) {
                    PincodeScreen(
                        PinCodeScreenState(toolbarTitleString = "Enter PIN", checkedDotsCount = 6,
                            enableShakeAnimation = wrong, migrating = migrating, checkNeedsNetwork = true,
                            isConnected = false), {}, {}, {}, { completed++; wrong = false }, {},
                    )
                }
            }
        }
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { wrong = true }
        rule.mainClock.advanceTimeBy(700)
        rule.runOnIdle { assertEquals(1, completed) }
        rule.mainClock.autoAdvance = true
        rule.runOnIdle { migrating = true }
        val waiting = context.getString(jp.co.soramitsu.common.R.string.safe_mode_waiting_network_body)
        rule.onNodeWithText(waiting).assertExists()
        rule.onNodeWithTag("$PACKAGE_ID:id/1").assertDoesNotExist()
        capture("pin-offline-check")
    }

    @Test fun ordinaryBackupIsSecureVeiledAndHidesAgainOnPause() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        val words = listOf("legal", "winner", "thank", "year", "wave", "sausage",
            "worth", "useful", "legal", "winner", "thank", "yellow")
        rule.setContent {
            SoraAppTheme(darkTheme = true) {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    BackupScreen(BackupScreenState(mnemonicWords = words), {})
                }
            }
        }
        rule.onNodeWithText("sausage").assertDoesNotExist()
        rule.runOnIdle {
            assertTrue(rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
        rule.onNodeWithTag(SafeModeTags.PHRASE_VEIL).performClick()
        rule.onNodeWithText("sausage").assertExists()
        rule.runOnIdle { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE) }
        rule.onNodeWithText("sausage").assertDoesNotExist()
        rule.runOnIdle { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
        rule.onNodeWithTag(SafeModeTags.PHRASE_VEIL).assertExists()
        rule.onNodeWithText("sausage").assertDoesNotExist()
    }

    @Test fun everyImportSourceAndMnemonicConfirmationProtectTheWindow() {
        var source by mutableStateOf<RecoveryType?>(RecoveryType.PASSPHRASE)
        var confirmation by mutableStateOf(false)
        rule.setContent {
            SoraAppTheme(darkTheme = true) {
                if (confirmation) MnemonicConfirmationScreen(MnemonicConfirmationState(1, listOf("one", "two", "three"), 1), {})
                else source?.let {
                    EnterPassphraseScreen(RecoveryState(recoveryType = it, title = jp.co.soramitsu.common.R.string.common_passphrase_title), {}, onNextClicked = {})
                }
            }
        }
        for (type in RecoveryType.values()) {
            rule.runOnIdle { source = type }
            rule.runOnIdle { assertTrue(rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
        }
        rule.runOnIdle { source = null; confirmation = true }
        rule.runOnIdle { assertTrue(rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
        rule.runOnIdle { confirmation = false }
        rule.runOnIdle { assertEquals(0, rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) }
    }

    @OptIn(ExperimentalUnitApi::class)
    @Test fun welcomeActionsKeepTheirDestinationsAndSettingsChooseOneTheme() {
        var settings by mutableStateOf(false)
        var dark by mutableStateOf(true)
        var system by mutableStateOf(false)
        val actions = mutableListOf<String>()
        rule.setContent {
            SoraAppTheme(darkTheme = dark, reduceEffects = true) {
                Box(Modifier.fillMaxSize()) {
                    NeonBackground()
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 64.dp)) {
                    if (!settings) TutorialScreen(TutorialScreenState(isGoogleBackupAvailable = true),
                        { actions += "create" }, { actions += "import" }, { actions += "drive" }, {})
                    else AppSettingsScreen(system, dark, false, false,
                        { system = it; dark = false }, { dark = it; system = false }, {})
                    }
                }
            }
        }
        for ((tag, expected) in listOf("CreateNewAccount" to "create", "ImportAccount" to "import", "GoogleSignin" to "drive")) {
            rule.onNodeWithTag("$PACKAGE_ID:id/$tag").performScrollTo().assertIsDisplayed().performClick()
            rule.runOnIdle { assertEquals(expected, actions.last()) }
        }
        capture("welcome-dark-actions")
        rule.runOnIdle { settings = true }
        for ((id, result) in listOf(jp.co.soramitsu.common.R.string.neon_theme_light to (false to false),
            jp.co.soramitsu.common.R.string.neon_theme_system to (true to false),
            jp.co.soramitsu.common.R.string.neon_theme_dark to (false to true))) {
            rule.onNodeWithText(context.getString(id)).performScrollTo().performClick()
            rule.runOnIdle { assertEquals(result, system to dark) }
        }
        capture("settings-dark")
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val bitmap = if (Build.VERSION.SDK_INT >= 29) {
            captureCommittedRoot()
        } else {
            // Retain Compose's existing native capture path on older supported APIs.
            rule.onRoot().captureToImage().asAndroidBitmap()
        }
        try {
            val dir = File(context.getExternalFilesDir(null), "neon-production").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use {
                assertTrue("The captured root must be written as PNG", bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private class TransientPixelCopyFailure(val status: Int) : RuntimeException()

    @RequiresApi(29)
    private fun captureCommittedRoot(): Bitmap {
        val deadline = SystemClock.uptimeMillis() + 60_000L
        repeat(3) { attempt ->
            try {
                return captureCommittedRootAttempt(deadline)
            } catch (failure: TransientPixelCopyFailure) {
                // PixelCopy can time out acquiring a GPU buffer even after frame
                // commitment. Retry acquisition only, with a newly committed frame;
                // layout, lifecycle and pixel assertions are never retried.
                assertTrue("PixelCopy acquisition failed after ${attempt + 1} attempts: ${failure.status}",
                    attempt < 2 && SystemClock.uptimeMillis() < deadline)
            }
        }
        error("PixelCopy acquisition did not return a bitmap")
    }

    @RequiresApi(29)
    private fun captureCommittedRootAttempt(deadline: Long): Bitmap {
        val node = rule.onRoot().fetchSemanticsNode()
        val composeRoot = checkNotNull(node.root as? ViewRootForTest) { "The captured root must belong to the activity window" }
        val activity = rule.activity
        val window = activity.window
        val decor = window.decorView
        val frameCommitted = CountDownLatch(1)
        val onFrameCommitted = Runnable { frameCommitted.countDown() }
        var observer: ViewTreeObserver? = null
        val bounds = Rect()
        val drawingWasEnabled = HardwareRendererCompat.isDrawingEnabled()
        try {
            // Compose's own capture path temporarily enables native drawing too.
            // Restore the prior global state even when capture fails.
            if (!drawingWasEnabled) HardwareRendererCompat.setDrawingEnabled(true)
            rule.runOnUiThread {
                assertEquals("Capture requires a resumed activity", Lifecycle.State.RESUMED, activity.lifecycle.currentState)
                assertTrue("The capture window must be attached", decor.isAttachedToWindow)
                assertTrue("The capture window must be visible", decor.isShown)
                assertTrue("The capture window must have focus", decor.hasWindowFocus())
                assertTrue("Capture must use the hardware renderer", decor.isHardwareAccelerated)
                assertTrue("The Compose root must still be attached", composeRoot.view.isAttachedToWindow)
                assertTrue("The Compose root must be visible", composeRoot.view.isShown)

                // Match captureToImage exactly: rounded boundsInRoot plus the
                // Compose host view's location in its window, not screen insets.
                val rootBounds = node.boundsInRoot
                bounds.set(rootBounds.left.roundToInt(), rootBounds.top.roundToInt(),
                    rootBounds.right.roundToInt(), rootBounds.bottom.roundToInt())
                val location = IntArray(2)
                composeRoot.view.getLocationInWindow(location)
                bounds.offset(location[0], location[1])
                assertTrue("The captured root must have a nonempty crop", bounds.width() > 0 && bounds.height() > 0)
                observer = decor.viewTreeObserver
                observer?.registerFrameCommitCallback(onFrameCommitted)
                composeRoot.invalidateDescendants()
                decor.invalidate()
            }
            // The framework helper allows only 2s for this native callback.
            // Wait for the actual GPU frame; do not sleep or redraw in software.
            val frameWait = deadline - SystemClock.uptimeMillis()
            assertTrue("The native root frame was not committed before the capture deadline",
                frameWait > 0 && frameCommitted.await(frameWait, TimeUnit.MILLISECONDS))
            val bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
            val copied = CountDownLatch(1)
            val result = AtomicInteger(-1)
            PixelCopy.request(window, bounds, bitmap, { status ->
                result.set(status)
                copied.countDown()
            }, Handler(Looper.getMainLooper()))
            val copyWait = deadline - SystemClock.uptimeMillis()
            assertTrue("PixelCopy did not complete before the capture deadline",
                copyWait > 0 && copied.await(copyWait, TimeUnit.MILLISECONDS))
            val status = result.get()
            if (status == PixelCopy.ERROR_TIMEOUT || status == PixelCopy.ERROR_SOURCE_NO_DATA) {
                bitmap.recycle()
                throw TransientPixelCopyFailure(status)
            }
            if (status != PixelCopy.SUCCESS) bitmap.recycle()
            assertEquals("PixelCopy must capture the committed root successfully", PixelCopy.SUCCESS, status)
            return bitmap
        } finally {
            try {
                rule.runOnUiThread { observer?.takeIf { it.isAlive }?.unregisterFrameCommitCallback(onFrameCommitted) }
            } finally {
                if (!drawingWasEnabled) HardwareRendererCompat.setDrawingEnabled(false)
            }
        }
    }
}
