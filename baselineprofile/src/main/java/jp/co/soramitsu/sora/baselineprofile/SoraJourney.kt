/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026 Polka Biome Ltd.
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

package jp.co.soramitsu.sora.baselineprofile

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import java.security.MessageDigest
import java.util.regex.Pattern
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue

/**
 * The ways people open SORA, driven through its screens for the profile generator and the startup benchmark.
 *
 * The wallet is the public BIP39 test phrase ("abandon … about"), imported on the benchmark device only, behind a
 * PIN made for these runs. No real secret is ever typed. Runs only against the production-shaped benchmark build
 * signed with the test key, installed as it is: such a build is disposable by contract (its data is cleared and its
 * PIN typed), so nobody's own wallet belongs in it. The screens are found by their English text: run on a device in
 * English.
 */
internal object SoraJourney {
    /**
     * The benchmark build types' own application id (`applicationIdSuffix ".benchmark"`): no store, debug or test
     * install of the app ever has it, so these journeys can never clear or unlock an install that holds someone's
     * wallet, whatever was installed or uninstalled before.
     */
    const val PACKAGE_NAME = "jp.co.soramitsu.sora.benchmark"

    /** The exported launcher alias of the startup screen (the screen itself is not exported). */
    const val LAUNCHER_ACTIVITY = "jp.co.soramitsu.sora.splash.presentation.SplashActivity"
    const val ONBOARDING_ACTIVITY = "jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingActivity"
    const val MAIN_ACTIVITY = "jp.co.soramitsu.feature_main_impl.presentation.MainActivity"

    /** The public BIP39 test vector: every wallet tool knows its accounts; it guards nothing. */
    private const val TEST_PHRASE =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    /** A PIN made for the benchmark device's test wallet only. */
    const val TEST_PIN = "258036"

    /** The app's own trace sections (`UnlockJourneyTrace`, `StartupTimeTrace`). */
    const val SECTION_STARTUP_TO_SAFE_ROUTE = "soraStartupToSafeRoute"
    const val SECTION_LAUNCH_TO_PIN_PAD = "soraLaunchToPinPad"
    const val SECTION_PIN_CHECK = "soraPinCheck"
    const val SECTION_UNLOCK_TO_HOME = "soraUnlockToHome"

    const val TIMEOUT_MILLIS = 30_000L

    /**
     * Long enough to watch the launch scene leave by itself once the PIN pad (or the welcome) is there: the rest of
     * its one-second intro, the mark's flight of 0.6 s and the fade, with room for a loaded emulator.
     */
    private const val LAUNCH_SCENE_WATCH_MILLIS = 3_000L

    /** What the row of PIN dots says to a screen reader, up to the count ("PIN digits entered: 2 of 6"). */
    private const val PIN_DIGITS_ENTERED = "PIN digits entered: "

    /** How long Import account has to offer its sources, and how often it is pressed again. */
    private const val IMPORT_SOURCE_MILLIS = 5_000L
    private const val IMPORT_TRIES = 3

    /** How long a pressed PIN key has to show as entered. */
    private const val PIN_KEY_SHOWN_MILLIS = 5_000L
    const val STARTUP_TIMEOUT = TIMEOUT_MILLIS
    private const val POLL_MILLIS = 100L

    /** Compose test tags are exported as resource ids under this prefix (`testTagAsId`). */
    private const val TAG_PACKAGE = "jp.co.soramitsu.sora.develop"

    fun tag(name: String): BySelector = By.res(TAG_PACKAGE, name)

    fun pinKey(digit: Char): BySelector = tag(digit.toString())

    /** The Wallet home with its Assets card, which the portfolio draws once it has a picture to show. */
    val homeAssets: BySelector = By.text("Assets")

    /** SHA-256 of the certificate of `key/testdebug.jks`, the test key the benchmark build types sign with. */
    private const val TEST_KEY_SHA256 = "87a04d87933e53af0b8e16b5089135fb49bbfea50bee3086743a1509a44149e9"

    /**
     * Refuses to touch an install that is not the benchmark build: the journeys clear app data and type a test PIN,
     * which must never reach a wallet someone uses. The benchmark builds have their own application id
     * ([PACKAGE_NAME]), which nothing else is installed as; on top of that the install must be signed with the
     * repository's test key and not debuggable. Every journey asks this before it touches the app, and the benchmark
     * and the profile generator ask it before Macrobenchmark can reset (reinstall) anything.
     */
    fun requireTestKeyInstall() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val info = try {
            context.packageManager.getPackageInfo(PACKAGE_NAME, PackageManager.GET_SIGNING_CERTIFICATES)
        } catch (missing: PackageManager.NameNotFoundException) {
            throw AssertionError("$PACKAGE_NAME is not installed", missing)
        }
        val signers = info.signingInfo?.apkContentsSigners.orEmpty().map { signer ->
            MessageDigest.getInstance("SHA-256").digest(signer.toByteArray()).joinToString("") { "%02x".format(it) }
        }
        assertTrue(
            "Refusing to clear or unlock $PACKAGE_NAME: it is not signed with the repository's test key ($signers)",
            signers == listOf(TEST_KEY_SHA256),
        )
        val debuggable = (info.applicationInfo?.flags ?: ApplicationInfo.FLAG_DEBUGGABLE) and
            ApplicationInfo.FLAG_DEBUGGABLE != 0
        assertTrue(
            "Refusing to clear or unlock $PACKAGE_NAME: it is a debuggable build, not a benchmark build",
            !debuggable,
        )
    }

    /**
     * Clears the benchmark build's data ([requireTestKeyInstall]), for the user these tests run as, and only if the
     * clear succeeds.
     */
    fun clearAppData(device: UiDevice) {
        requireTestKeyInstall()
        // The user these tests run as, which the package was looked up for (a uid is user * 100000 + app id).
        val user = android.os.Process.myUid() / PER_USER_RANGE
        val result = device.executeShellCommand("pm clear --user $user $PACKAGE_NAME").trim()
        assertTrue("Clearing $PACKAGE_NAME for user $user did not succeed: $result", result == "Success")
    }

    private const val PER_USER_RANGE = 100_000

    fun launchCold(device: UiDevice) {
        device.executeShellCommand("am force-stop $PACKAGE_NAME")
        val result = device.executeShellCommand("am start -W -n $PACKAGE_NAME/$LAUNCHER_ACTIVITY")
        assertTrue("SORA did not start: $result", result.contains("Status: ok"))
    }

    fun waitForActivity(device: UiDevice, activityName: String, timeoutMillis: Long = TIMEOUT_MILLIS): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        do {
            if (device.executeShellCommand("dumpsys activity activities").contains(activityName)) return true
            SystemClock.sleep(POLL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        return false
    }

    fun waitFor(device: UiDevice, selector: BySelector, what: String, timeoutMillis: Long = TIMEOUT_MILLIS): UiObject2 {
        val found = device.wait(Until.findObject(selector), timeoutMillis)
        assertTrue("$what did not appear", found != null)
        return found!!
    }

    /**
     * Leaves the device with the test wallet behind [TEST_PIN], whatever state the app was in: a fresh install is
     * taken through Import account; an existing wallet is only unlocked. Ends on the Wallet home.
     */
    fun ensureTestWallet(device: UiDevice) {
        requireTestKeyInstall()
        launchCold(device)
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            if (device.hasObject(tag("ImportAccount"))) {
                importTestWallet(device)
                return
            }
            if (device.hasObject(pinKey('1'))) {
                unlock(device)
                return
            }
            SystemClock.sleep(POLL_MILLIS)
        }
        throw AssertionError("Neither onboarding nor the PIN pad appeared")
    }

    private fun importTestWallet(device: UiDevice) {
        // Import account first asks for the source: Google, Passphrase, Raw seed or a backup file. One loop watches for
        // both: the source list is taken whenever it shows, however late, and Import account is pressed again only
        // after it has had its time to answer.
        var source: UiObject2? = null
        var presses = 0
        var lastPress = 0L
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (source == null && SystemClock.uptimeMillis() < deadline) {
            source = device.findObject(By.text("Passphrase"))
            if (source != null) break
            val importButton = device.findObject(tag("ImportAccount"))
            if (importButton != null && presses < IMPORT_TRIES && SystemClock.uptimeMillis() - lastPress > IMPORT_SOURCE_MILLIS) {
                importButton.click()
                presses += 1
                lastPress = SystemClock.uptimeMillis()
            }
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue("Import account was pressed $presses times and never offered the Passphrase source", source != null)
        source!!.click()
        val field = waitFor(device, By.clazz("android.widget.EditText"), "The recovery phrase field")
        field.click()
        field.text = TEST_PHRASE
        device.pressBack() // closes the keyboard
        continueThrough(device)
        createPin(device)
        waitForHome(device)
    }

    /** Continue on each onboarding screen (phrase, name, backup offers) until the PIN pad shows. */
    private fun continueThrough(device: UiDevice) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            if (device.hasObject(pinKey('1'))) return
            val next = device.findObject(By.text("Continue"))
                ?: device.findObject(By.text("Skip"))
                ?: device.findObject(By.text("Not now"))
            if (next != null && next.isEnabled) next.click()
            SystemClock.sleep(500)
        }
        throw AssertionError("The PIN setup did not appear after importing the test wallet")
    }

    private fun createPin(device: UiDevice) {
        typePin(device) // Setup pin code
        // The confirmation pad starts with no digit entered.
        assertTrue(
            "The PIN confirmation did not come after the PIN was set up",
            device.wait(Until.hasObject(By.descStartsWith("${PIN_DIGITS_ENTERED}0 ")), TIMEOUT_MILLIS) == true,
        )
        typePin(device) // Confirm pin code
        // A phone with a fingerprint is asked once whether to use it: not for the test wallet.
        device.wait(Until.findObject(By.text("Cancel")), 3_000)?.click()
    }

    /**
     * Types the test PIN, each key once. Every digit but the last (which starts the check, and the pad moves on) must
     * show as entered before the next is pressed; one that does not fails the run rather than being pressed again,
     * which could enter it twice and spend a PIN attempt. (The keys are seen only once the launch scene has let the
     * pad through: while it covers the pad, its views are kept from UI Automator as from a screen reader.)
     */
    fun typePin(device: UiDevice) {
        TEST_PIN.forEachIndexed { index, digit ->
            val entered = index + 1
            waitFor(device, pinKey(digit), "PIN key $digit").click()
            if (entered < TEST_PIN.length) {
                assertTrue(
                    "PIN key $digit did not show as entered; not pressed again",
                    device.wait(Until.hasObject(By.descStartsWith("$PIN_DIGITS_ENTERED$entered ")), PIN_KEY_SHOWN_MILLIS) == true,
                )
            }
        }
    }

    /**
     * From a cold start of an existing wallet: the PIN pad, the launch scene over it (left to end by itself, as for
     * someone who watches it; [tapFlight] ends it with a tap, as for someone in a hurry), the PIN, and the Wallet
     * home with its balances.
     */
    fun unlock(device: UiDevice, tapFlight: Boolean = false) {
        waitFor(device, pinKey('1'), "The PIN pad")
        if (tapFlight) dismissLaunchFlight(device) else watchLaunchScene()
        typePin(device)
        waitForHome(device)
    }

    /**
     * A tap on the launch scene, at the PIN screen's empty top: the scene goes as soon as the pad is ready, at once
     * if it is, without playing its way out. The tap changes nothing when no scene is there.
     */
    fun dismissLaunchFlight(device: UiDevice) {
        device.click(device.displayWidth / 2, device.displayHeight / 12)
        SystemClock.sleep(400)
    }

    /**
     * Leaves the launch scene the time to go by itself. It lies over the first screen until that screen is laid
     * out, then the mark flies to the PIN emblem and the scene fades; it is kept out of what a screen reader (and
     * so UI Automator) sees, so its end is waited for by the clock.
     */
    fun watchLaunchScene() {
        SystemClock.sleep(LAUNCH_SCENE_WATCH_MILLIS)
    }

    /** The home says its balances are live (the chip "LIVE · 3s"). False when it does not within [timeoutMillis]. */
    fun waitForLiveHome(device: UiDevice, timeoutMillis: Long): Boolean =
        device.wait(Until.hasObject(By.textStartsWith("LIVE")), timeoutMillis) == true

    fun waitForHome(device: UiDevice) {
        waitFor(device, homeAssets, "The Wallet home's Assets card")
        device.waitForIdle()
    }

    /**
     * The home lists read balances: saved, live, or a confirmed empty wallet, as the Assets card's heading says by
     * its id (nothing a person sees). A read that failed ("Balance unavailable") fails the run: the time to it is not
     * a time to balances.
     */
    fun waitForReadBalances(device: UiDevice) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            if (device.hasObject(tag(ASSETS_READ))) return
            if (device.hasObject(tag(ASSETS_UNAVAILABLE))) throw AssertionError("The Wallet home could not read its balances")
            SystemClock.sleep(POLL_MILLIS / 2)
        }
        throw AssertionError("The Wallet home was still reading its balances after $TIMEOUT_MILLIS ms")
    }

    /** The Assets card heading's ids (feature_wallet_impl PortfolioHome). */
    private const val ASSETS_READ = "PortfolioAssetsRead"
    private const val ASSETS_UNAVAILABLE = "PortfolioAssetsUnavailable"

    /** The home's own scroller (the Column under the hero). */
    private fun homeScroller(device: UiDevice): UiObject2 =
        device.findObject(By.scrollable(true)) ?: throw AssertionError("The Wallet home does not scroll")

    fun scrollHome(device: UiDevice, flings: Int = 2) {
        val scroller = homeScroller(device)
        // Away from the screen's edges: an edge swipe is the system's back gesture.
        scroller.setGestureMargin(device.displayWidth / 5)
        repeat(flings) {
            scroller.fling(Direction.DOWN)
            device.waitForIdle()
        }
        repeat(flings) {
            scroller.fling(Direction.UP)
            device.waitForIdle()
        }
    }

    /**
     * Opens the first token the home lists ("Where it lives") and goes back. Returns false when the home lists no
     * token to open.
     */
    fun openTokenAndBack(device: UiDevice): Boolean {
        val row = device.findObject(By.text("XOR")) ?: return false
        row.click()
        // The token's page lists where the token is held.
        if (device.wait(Until.hasObject(tokenPage), TIMEOUT_MILLIS) != true) return false
        device.waitForIdle()
        device.pressBack()
        waitForHome(device)
        return true
    }

    /** The token page's "Where it lives" list. */
    private val tokenPage: BySelector = By.text("Where it lives")

    /** Explore, Activity and back to the Wallet tab. */
    fun switchTabs(device: UiDevice) {
        listOf("explore_nav_graph", "activities_nav_graph", "wallet_nav_graph").forEach { tab ->
            // A view id's package is the app's resources' own, whatever the application id: matched by its name.
            device.findObject(By.res(Pattern.compile(".*:id/$tab")))?.click()
            device.waitForIdle()
        }
        waitForHome(device)
    }

    fun instrumentationDevice(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
}
