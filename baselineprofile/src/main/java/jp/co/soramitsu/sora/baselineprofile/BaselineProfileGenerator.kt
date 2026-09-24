/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026 Polka Biome Ltd.
SPDX-License-Identifier: BSD-4-Clause
*/

package jp.co.soramitsu.sora.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        // Keep collecting through Splash's asynchronous Room open and wallet migration. This is
        // a production-shaped fresh install, whose expected destination is onboarding.
        assertTrue(
            "Splash did not complete while collecting the startup profile",
            device.wait(
                Until.gone(By.res(PACKAGE_NAME, "animation_view")),
                STARTUP_TIMEOUT_MILLIS,
            ),
        )
        assertFreshInstallReachedOnboarding(device)
        device.waitForIdle()
    }

    private fun assertFreshInstallReachedOnboarding(device: UiDevice) {
        assertTrue(
            "Fresh install did not reach onboarding; a recovery route must not generate the profile",
            waitForActivity(device, ONBOARDING_ACTIVITY),
        )
    }

    private fun waitForActivity(device: UiDevice, activityName: String): Boolean {
        val deadline = SystemClock.uptimeMillis() + STARTUP_TIMEOUT_MILLIS
        do {
            val activityState = device.executeShellCommand("dumpsys activity activities")
            if (activityState.contains(activityName)) return true
            SystemClock.sleep(ACTIVITY_POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        return false
    }

    private companion object {
        const val PACKAGE_NAME = "jp.co.soramitsu.sora"
        const val ONBOARDING_ACTIVITY =
            "jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingActivity"
        const val STARTUP_TIMEOUT_MILLIS = 30_000L
        const val ACTIVITY_POLL_INTERVAL_MILLIS = 100L
    }
}
