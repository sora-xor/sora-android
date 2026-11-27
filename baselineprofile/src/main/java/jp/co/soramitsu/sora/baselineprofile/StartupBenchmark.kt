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

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.sora.baselineprofile.SoraJourney.ONBOARDING_ACTIVITY
import jp.co.soramitsu.sora.baselineprofile.SoraJourney.PACKAGE_NAME
import jp.co.soramitsu.sora.baselineprofile.SoraJourney.SECTION_LAUNCH_TO_PIN_PAD
import jp.co.soramitsu.sora.baselineprofile.SoraJourney.SECTION_PIN_CHECK
import jp.co.soramitsu.sora.baselineprofile.SoraJourney.SECTION_STARTUP_TO_SAFE_ROUTE
import jp.co.soramitsu.sora.baselineprofile.SoraJourney.SECTION_UNLOCK_TO_HOME
import jp.co.soramitsu.sora.baselineprofile.SoraJourney.STARTUP_TIMEOUT
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures how SORA opens, without compilation and with the shipped baseline profile, and how smoothly its Wallet
 * home moves. An existing wallet is measured from launch to its home with the sections the app marks: the PIN pad's
 * first frame ([SECTION_LAUNCH_TO_PIN_PAD]), the PIN's answer ([SECTION_PIN_CHECK]) and the home's first frame after
 * it ([SECTION_UNLOCK_TO_HOME]); the time a person takes to type the PIN is left out. Finer marks (the pad taking
 * keys, the first frame with read balances) come with the measuring build only.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /**
     * Before Macrobenchmark can reset compilation, which reinstalls the app on some Android versions and so clears
     * its data: only a disposable benchmark install is measured.
     */
    @Before
    fun onlyABenchmarkInstalledAsItself() = SoraJourney.requireInstalledAsItself()

    @Test
    fun coldStartupWithoutCompilation() = measureFreshInstall(CompilationMode.None())

    @Test
    fun coldStartupWithBaselineProfile() = measureFreshInstall(withProfile())

    @Test
    fun unlockToHomeWithoutCompilation() = measureUnlock(CompilationMode.None())

    @Test
    fun unlockToHomeWithBaselineProfile() = measureUnlock(withProfile())

    @Test
    fun homeScrollWithBaselineProfile() = measureOnHome { SoraJourney.scrollHome(device, flings = 3) }

    @Test
    fun openTokenAndBackWithBaselineProfile() = measureOnHome {
        assertTrue("The home lists no token to open", SoraJourney.openTokenAndBack(device))
    }

    @Test
    fun switchTabsWithBaselineProfile() = measureOnHome { SoraJourney.switchTabs(device) }

    /**
     * Frames of [action] on the unlocked Wallet home, with the shipped profile. Each iteration starts the app cold
     * and unlocks it in its setup (not measured); the process is not killed again before [action].
     */
    private fun measureOnHome(action: MacrobenchmarkScope.() -> Unit) {
        var provisioned = false
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = withProfile(),
            startupMode = null,
            iterations = ITERATIONS,
            setupBlock = {
                // After the compilation reset, which may reinstall the app on older unrooted devices.
                if (!provisioned) {
                    SoraJourney.ensureTestWallet(device)
                    provisioned = true
                }
                killProcess()
                pressHome()
                startActivityAndWait()
                SoraJourney.unlock(device)
            },
            measureBlock = action,
        )
    }

    private fun withProfile() = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require)

    private fun measureFreshInstall(compilationMode: CompilationMode) {
        // Another test may have left a wallet: this one measures a fresh install.
        SoraJourney.clearAppData(SoraJourney.instrumentationDevice())
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(
                StartupTimingMetric(),
                section(SECTION_STARTUP_TO_SAFE_ROUTE, "timeToSafeRouteDecision"),
            ),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = ITERATIONS,
            setupBlock = {
                pressHome()
            },
            measureBlock = {
                startActivityAndWait()
                assertTrue(
                    "Fresh install did not reach onboarding; a recovery route must not pass the benchmark",
                    SoraJourney.waitForActivity(device, ONBOARDING_ACTIVITY, STARTUP_TIMEOUT),
                )
            },
        )
    }

    private fun measureUnlock(compilationMode: CompilationMode) {
        var provisioned = false
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(
                StartupTimingMetric(),
                section(SECTION_STARTUP_TO_SAFE_ROUTE, "timeToSafeRouteDecision"),
                section(SECTION_LAUNCH_TO_PIN_PAD, "timeToPinPad"),
                section(SECTION_PIN_CHECK, "pinCheck"),
                section(SECTION_UNLOCK_TO_HOME, "unlockToHome"),
            ),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = ITERATIONS,
            setupBlock = {
                // After the compilation reset, which may reinstall the app on older unrooted devices.
                if (!provisioned) {
                    SoraJourney.ensureTestWallet(device)
                    provisioned = true
                }
                pressHome()
            },
            measureBlock = {
                startActivityAndWait()
                SoraJourney.unlock(device)
                SoraJourney.waitForReadBalances(device)
            },
        )
    }

    private fun section(name: String, label: String) =
        TraceSectionMetric(sectionName = name, mode = TraceSectionMetric.Mode.First, label = label)

    private companion object {
        /** `-e sora.iterations N` runs fewer or more. */
        val ITERATIONS: Int
            get() = InstrumentationRegistry.getArguments().getString("sora.iterations")?.toInt() ?: 10
    }
}
