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

import android.graphics.Bitmap
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
        val dir = File(context.getExternalFilesDir(null), "neon-production").apply { mkdirs() }
        rule.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
