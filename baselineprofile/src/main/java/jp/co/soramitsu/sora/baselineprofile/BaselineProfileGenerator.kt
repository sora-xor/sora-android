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

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.sora.baselineprofile.SoraJourney.ONBOARDING_ACTIVITY
import jp.co.soramitsu.sora.baselineprofile.SoraJourney.PACKAGE_NAME
import jp.co.soramitsu.sora.baselineprofile.SoraJourney.STARTUP_TIMEOUT
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Collects the profile from the ways people really open and use SORA. Nearly every start is an existing wallet:
 * launch, PIN, the Wallet home. A fresh install's way to onboarding is kept as well. Each test sets up the state it
 * needs itself, so they run in any order.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    /** A fresh install: launch through the startup checks to onboarding. */
    @Test
    fun startup() {
        // Another test may have left a wallet: this one starts from nothing, like a fresh install.
        SoraJourney.clearAppData(SoraJourney.instrumentationDevice())
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            // Keep collecting through the startup screen's asynchronous Room open and wallet migration. This is
            // a production-shaped fresh install, whose expected destination is onboarding.
            assertTrue(
                "Fresh install did not reach onboarding; a recovery route must not generate the profile",
                SoraJourney.waitForActivity(device, ONBOARDING_ACTIVITY, STARTUP_TIMEOUT),
            )
            // The launch scene carries on over the welcome and leaves by itself: that is part of every start.
            SoraJourney.watchLaunchScene()
            device.waitForIdle()
        }
    }

    /** An existing wallet: cold launch, PIN, the Wallet home with its balances. */
    @Test
    fun unlockExistingWallet() {
        SoraJourney.ensureTestWallet(SoraJourney.instrumentationDevice())
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            includeInStartupProfile = true,
        ) {
            // A cold start every time: a running wallet would open without its PIN.
            killProcess()
            pressHome()
            startActivityAndWait()
            SoraJourney.unlock(device)
        }
    }

    /** On the home: scroll it, open a token and come back, switch tabs. Not part of the startup profile. */
    @Test
    fun useWalletHome() {
        SoraJourney.ensureTestWallet(SoraJourney.instrumentationDevice())
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            includeInStartupProfile = false,
        ) {
            killProcess()
            pressHome()
            startActivityAndWait()
            SoraJourney.unlock(device)
            SoraJourney.scrollHome(device)
            assertTrue("The home's XOR row did not open its token page", SoraJourney.openTokenAndBack(device))
            SoraJourney.switchTabs(device)
        }
    }
}
