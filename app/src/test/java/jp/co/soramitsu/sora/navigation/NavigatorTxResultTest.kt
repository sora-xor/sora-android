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
import androidx.navigation.NavOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.feature_assets_api.presentation.TransactionScreen
import jp.co.soramitsu.sora.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A transaction is followed until it is final, which can take two minutes, and its page is
 * opened afterwards. By then the app can have been in the background for more than five minutes
 * and the PIN lock is on top of the screen that sent it. The PIN lock is only a destination on
 * the navigation stack: opening the page with a pop back to the wallet hub removes it, and the
 * wallet is open with no PIN or biometric entered. The page may therefore only be opened while
 * the screen that sent the transaction is itself the current destination.
 */
class NavigatorTxResultTest {

    private val txHash = "0x" + "ab".repeat(32)

    private val controller = mockk<NavController>(relaxed = true)
    private val navigator = Navigator().also { it.attachNavController(controller) }

    /** The options of every navigation to the transaction page, in order. */
    private val openedWith = mutableListOf<NavOptions?>()

    /** False while the fragment manager has saved its state: navigation is then dropped. */
    private var navigationIsCarriedOut = true

    private val destinations = mapOf(
        TransactionScreen.SEND to R.id.transferAmountFragment,
        TransactionScreen.SWAP to R.id.swapFragment,
        TransactionScreen.ADD_LIQUIDITY to R.id.addLiquidityFragment,
        TransactionScreen.REMOVE_LIQUIDITY to R.id.liquidityRemoveFragment,
        TransactionScreen.DEMETER_CLAIM to R.id.claimDemeterFragment,
        TransactionScreen.DEMETER_STAKE to R.id.editFarmFragment,
        TransactionScreen.REFERRAL to R.id.referralFragment,
    )

    init {
        every {
            controller.navigate(R.id.txDetailsFragment, any(), captureNullable(openedWith))
        } answers {
            if (navigationIsCarriedOut) currentDestination(R.id.txDetailsFragment)
        }
    }

    private fun currentDestination(id: Int?) {
        every { controller.currentDestination } returns id?.let { destinationId ->
            mockk<NavDestination> { every { this@mockk.id } returns destinationId }
        }
    }

    private fun assertNothingChanged() {
        assertTrue(openedWith.isEmpty())
        verify(exactly = 0) { controller.navigate(any<Int>(), any(), any<NavOptions>()) }
        verify(exactly = 0) { controller.navigate(any<Int>(), any()) }
        verify(exactly = 0) { controller.navigate(any<Int>()) }
        verify(exactly = 0) { controller.popBackStack() }
        verify(exactly = 0) { controller.popBackStack(any<Int>(), any()) }
    }

    @Test
    fun `every screen that sends a transaction has its own destination`() {
        assertEquals(TransactionScreen.values().toSet(), destinations.keys)
        assertEquals(destinations.size, destinations.values.toSet().size)
    }

    @Test
    fun `the page opens while the screen that sent the transaction is in front`() {
        destinations.forEach { (screen, destination) ->
            openedWith.clear()
            currentDestination(destination)

            assertTrue("$screen", navigator.showTxResult(txHash, screen))

            assertEquals("$screen", 1, openedWith.size)
            val options = openedWith.single()
            if (screen.replacedByPage) {
                // The page takes the place of the screen and of the steps that led to it.
                assertEquals("$screen", R.id.cardsHubFragment, options?.popUpToId)
                assertEquals("$screen", false, options?.isPopUpToInclusive())
            } else {
                // The page is put on top of the screen; nothing is removed.
                assertNull("$screen", options)
            }
        }
        verify(exactly = 0) { controller.popBackStack() }
        verify(exactly = 0) { controller.popBackStack(any<Int>(), any()) }
    }

    @Test
    fun `nothing is opened and nothing is popped while the PIN lock is on top`() {
        // Before the fix every screen but the referral one navigated here with a pop back to
        // the hub, which removed the PIN lock; the referral one put the page over it.
        currentDestination(R.id.pincodeFragment)

        TransactionScreen.values().forEach { screen ->
            assertFalse("$screen", navigator.showTxResult(txHash, screen))
        }

        assertNothingChanged()
    }

    @Test
    fun `nothing is opened while another screen or no screen is in front`() {
        currentDestination(R.id.cardsHubFragment)
        TransactionScreen.values().forEach { screen ->
            assertFalse("$screen", navigator.showTxResult(txHash, screen))
        }
        // A result is only shown by the screen that sent the transaction.
        currentDestination(R.id.swapFragment)
        assertFalse(navigator.showTxResult(txHash, TransactionScreen.SEND))
        // The page itself is in front: it is not opened a second time.
        currentDestination(R.id.txDetailsFragment)
        assertFalse(navigator.showTxResult(txHash, TransactionScreen.SWAP))
        currentDestination(null)
        assertFalse(navigator.showTxResult(txHash, TransactionScreen.SWAP))

        assertNothingChanged()
    }

    @Test
    fun `a navigation that was dropped is reported as not done`() {
        // With the activity stopped the fragment manager has saved its state and ignores both
        // the pop and the navigation. The caller must know, so it can show the result later.
        navigationIsCarriedOut = false
        currentDestination(R.id.swapFragment)

        assertFalse(navigator.showTxResult(txHash, TransactionScreen.SWAP))

        // The app is back in front.
        navigationIsCarriedOut = true
        assertTrue(navigator.showTxResult(txHash, TransactionScreen.SWAP))
    }

    @Test
    fun `nothing happens without a navigation controller`() {
        assertFalse(Navigator().showTxResult(txHash, TransactionScreen.SEND))
    }

    @Test
    fun `a tap on a list entry opens the page on top of the list`() {
        currentDestination(R.id.cardsHubFragment)

        navigator.showTxDetails(txHash)

        verify(exactly = 1) { controller.navigate(R.id.txDetailsFragment, any()) }
        verify(exactly = 0) { controller.popBackStack() }
        verify(exactly = 0) { controller.popBackStack(any<Int>(), any()) }
    }
}
