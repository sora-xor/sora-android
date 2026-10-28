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

package jp.co.soramitsu.sora.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.math.BigDecimal
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common.util.StringTriple
import jp.co.soramitsu.feature_assets_api.presentation.TransactionScreen
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.sora.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The PIN lock is only a destination on the navigation stack. This test runs the app's own
 * router ([Navigator]) and its own navigation graph in a real [NavController] and checks that
 * the result of a transaction, which can arrive up to two minutes after the tap, can never
 * remove or cover the PIN lock that was put on top in the meantime.
 *
 * Only the fragments are replaced: the destinations of the graph are kept on the stack by a
 * navigator that shows nothing, so no screen, wallet or network is needed.
 */
@RunWith(AndroidJUnit4::class)
class NavigatorPinLockStackTest {

    /** Keeps the graph's `<fragment>` destinations on the stack without showing fragments. */
    @androidx.navigation.Navigator.Name("fragment")
    private class StackOnlyNavigator : androidx.navigation.Navigator<NavDestination>() {
        override fun createDestination(): NavDestination = NavDestination(this)
    }

    private val txHash = "0x" + "ab".repeat(32)
    private val ids = StringTriple("0xtoken1", "0xtoken2", "0xtoken3")

    private lateinit var controller: NavController
    private lateinit var router: Navigator

    /** A navigation controller is used on the main thread; a failure is reported by the test. */
    private fun onMainThread(block: () -> Unit) {
        var failure: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                block()
            } catch (t: Throwable) {
                failure = t
            }
        }
        failure?.let { throw it }
    }

    /** The wallet hub with nothing on top of it, as after unlocking the app. */
    private fun startAtHub() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        controller = NavController(context)
        controller.navigatorProvider.addNavigator(StackOnlyNavigator())
        router = Navigator()
        router.attachNavController(controller)
        assertEquals(listOf(R.id.cardsHubFragment), screens())
    }

    /** The screens on the stack, bottom first (the graphs themselves are left out). */
    private fun screens(): List<Int> =
        controller.currentBackStack.value
            .map { it.destination }
            .filter { it !is NavGraph }
            .map { it.id }

    /** Opens each screen that sends a transaction the way the app opens it. */
    private val open: Map<TransactionScreen, () -> Unit> = mapOf(
        TransactionScreen.SEND to { router.showValTransferAmount("cnRecipient", "0xtoken1", null) },
        TransactionScreen.SWAP to { router.showSwap("0xtoken1", "0xtoken2") },
        TransactionScreen.ADD_LIQUIDITY to { router.showAddLiquidity("0xtoken1", "0xtoken2") },
        TransactionScreen.REMOVE_LIQUIDITY to {
            router.showRemoveLiquidity(StringPair("0xtoken1", "0xtoken2"))
        },
        TransactionScreen.DEMETER_CLAIM to { router.showClaimDemeter(ids, BigDecimal.ONE) },
        TransactionScreen.DEMETER_STAKE to { router.showEditFarm(ids) },
        TransactionScreen.REFERRAL to { router.showReferrals() },
    )

    @Test
    fun everyScreenThatSendsATransactionIsCovered() {
        assertEquals(TransactionScreen.values().toSet(), open.keys)
    }

    @Test
    fun theResultOpensThePageWhileItsScreenIsInFront() = onMainThread {
        TransactionScreen.values().forEach { screen ->
            startAtHub()
            open.getValue(screen)()
            val sender = checkNotNull(controller.currentDestination).id
            assertEquals("$screen", listOf(R.id.cardsHubFragment, sender), screens())

            assertTrue("$screen", router.showTxResult(txHash, screen))

            val expected = if (screen.replacedByPage) {
                // The page takes the place of the screen: Back leads to the hub.
                listOf(R.id.cardsHubFragment, R.id.txDetailsFragment)
            } else {
                listOf(R.id.cardsHubFragment, sender, R.id.txDetailsFragment)
            }
            assertEquals("$screen", expected, screens())
        }
    }

    @Test
    fun theResultNeitherRemovesNorCoversThePinLock() = onMainThread {
        TransactionScreen.values().forEach { screen ->
            startAtHub()
            open.getValue(screen)()
            val sender = checkNotNull(controller.currentDestination).id
            // Confirm was tapped, the app went to the background, and on return after more
            // than five minutes the activity put the PIN lock on top.
            router.showPin(PinCodeAction.TIMEOUT_CHECK)
            val locked = listOf(R.id.cardsHubFragment, sender, R.id.pincodeFragment)
            assertEquals("$screen", locked, screens())

            // The result arrives now. Before the fix the page was opened here with a pop back
            // to the hub: the stack became hub, page, and Back led into the wallet with no
            // PIN or biometric entered.
            assertFalse("$screen", router.showTxResult(txHash, screen))

            assertEquals("$screen", locked, screens())
            assertTrue("$screen", router.currentDestinationIsPincode())

            // The PIN is entered; the PIN lock removes itself and the screen is in front again.
            router.popBackStack()
            assertEquals("$screen", listOf(R.id.cardsHubFragment, sender), screens())

            // The result that was held is shown now.
            assertTrue("$screen", router.showTxResult(txHash, screen))
            assertEquals("$screen", R.id.txDetailsFragment, controller.currentDestination?.id)
            assertFalse("$screen", screens().contains(R.id.pincodeFragment))
        }
    }

    @Test
    fun aResultIsOnlyShownByTheScreenThatSentTheTransaction() = onMainThread {
        startAtHub()
        // The user left the swap screen while the swap was followed and is somewhere else.
        router.showReferrals()
        val elsewhere = screens()

        assertFalse(router.showTxResult(txHash, TransactionScreen.SWAP))
        assertEquals(elsewhere, screens())

        // On the hub itself nothing is opened either.
        router.popBackStack()
        assertFalse(router.showTxResult(txHash, TransactionScreen.SWAP))
        assertEquals(listOf(R.id.cardsHubFragment), screens())
    }
}
