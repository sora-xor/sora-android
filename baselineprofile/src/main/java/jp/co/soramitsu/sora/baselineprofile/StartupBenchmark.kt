/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026 Polka Biome Ltd.
SPDX-License-Identifier: BSD-4-Clause
*/

package jp.co.soramitsu.sora.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupWithoutCompilation() = measure(CompilationMode.None())

    @Test
    fun coldStartupWithBaselineProfile() = measure(
        CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Require,
        )
    )

    private fun measure(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(
                StartupTimingMetric(),
                TraceSectionMetric(
                    sectionName = STARTUP_TO_SAFE_ROUTE_SECTION,
                    mode = TraceSectionMetric.Mode.First,
                    label = "timeToSafeRouteDecision",
                ),
            ),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 10,
            setupBlock = {
                pressHome()
            },
            measureBlock = {
                startActivityAndWait()
                assertTrue(
                    "Splash did not complete during the startup benchmark",
                    device.wait(
                        Until.gone(By.res(PACKAGE_NAME, "animation_view")),
                        STARTUP_TIMEOUT_MILLIS,
                    ),
                )
                assertFreshInstallReachedOnboarding(device)
            },
        )
    }

    private fun assertFreshInstallReachedOnboarding(device: UiDevice) {
        assertTrue(
            "Fresh install did not reach onboarding; a recovery route must not pass the benchmark",
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
        const val STARTUP_TO_SAFE_ROUTE_SECTION = "soraStartupToSafeRoute"
        const val STARTUP_TIMEOUT_MILLIS = 30_000L
        const val ACTIVITY_POLL_INTERVAL_MILLIS = 100L
    }
}
