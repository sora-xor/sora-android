/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause
*/

package jp.co.soramitsu.sora.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.sora.R
import org.junit.Test

/**
 * The legacy claim screen closes itself when the claim is done, also from a timer. After five
 * minutes in the background the PIN lock is put on top of it. A blind `popBackStack()` then
 * removes the PIN lock, the claim screen below removes itself, and the wallet is open with no
 * PIN or biometric entered. The claim screen may therefore only ever pop itself.
 */
class NavigatorClaimScreenTest {

    private val controller = mockk<NavController>(relaxed = true)
    private val navigator = Navigator().also { it.attachNavController(controller) }

    private fun currentDestination(id: Int?) {
        every { controller.currentDestination } returns id?.let { destinationId ->
            mockk<NavDestination> { every { this@mockk.id } returns destinationId }
        }
    }

    @Test
    fun `the claim screen is popped while it is in front`() {
        currentDestination(R.id.claimFragment)

        navigator.closeClaimScreen()

        verify(exactly = 1) { controller.popBackStack(R.id.claimFragment, true) }
        verify(exactly = 0) { controller.popBackStack() }
    }

    @Test
    fun `nothing is popped while the PIN lock is on top of the claim screen`() {
        currentDestination(R.id.pincodeFragment)

        navigator.closeClaimScreen()

        verify(exactly = 0) { controller.popBackStack() }
        verify(exactly = 0) { controller.popBackStack(any<Int>(), any()) }
    }

    @Test
    fun `nothing is popped while any other screen or no screen is in front`() {
        currentDestination(R.id.cardsHubFragment)
        navigator.closeClaimScreen()
        currentDestination(null)
        navigator.closeClaimScreen()

        verify(exactly = 0) { controller.popBackStack() }
        verify(exactly = 0) { controller.popBackStack(any<Int>(), any()) }
    }

    @Test
    fun `nothing happens without a navigation controller`() {
        Navigator().closeClaimScreen()
    }
}
