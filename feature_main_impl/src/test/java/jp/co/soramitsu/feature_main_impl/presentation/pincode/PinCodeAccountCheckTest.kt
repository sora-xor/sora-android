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

package jp.co.soramitsu.feature_main_impl.presentation.pincode

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

/**
 * The account check after unlocking (design spec 9.2 U1, U2): it says it waits for the network
 * only when it needs the network, and it opens the wallet exactly once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PinCodeAccountCheckTest {

    @get:Rule
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val connection = MutableStateFlow(true)
    private val interactor = mockk<PinCodeInteractor>(relaxed = true).also {
        coEvery { it.isCodeSet() } returns true
        coEvery { it.isBiometryEnabled() } returns false
        coEvery { it.retrieveTriesUsed() } returns 0
        coEvery { it.retrieveTimerStartedTimestamp() } returns 0L
        coEvery { it.needsMigration() } returns false
    }
    private val mainRouter = mockk<MainRouter>(relaxed = true)
    private val connectionManager = mockk<ConnectionManager>().also {
        every { it.connectionState } returns connection
    }
    private val resourceManager = mockk<ResourceManager>().also {
        every { it.getString(any()) } returns "Enter your PIN"
    }

    private fun viewModel() = PinCodeViewModel(
        interactor,
        mockk(relaxed = true),
        mainRouter,
        resourceManager,
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        connectionManager,
    )

    @Test
    fun `a second trigger while the check starts never opens the wallet twice`() = runTest {
        val needsNetwork = CompletableDeferred<Boolean>()
        coEvery { interactor.migrationCheckNeedsNetwork() } coAnswers { needsNetwork.await() }
        val vm = viewModel()
        vm.startAuth(PinCodeAction.TIMEOUT_CHECK)
        advanceUntilIdle()
        vm.onAuthenticationSucceeded()
        advanceUntilIdle()
        // While the first check waits, the biometrics dialog answer starts another one.
        vm.biometryDialogNoClicked()
        advanceUntilIdle()
        needsNetwork.complete(false)
        advanceUntilIdle()
        verify(exactly = 1) { mainRouter.popBackStack() }
        coVerify(exactly = 1) { interactor.needsMigration() }
    }

    @Test
    fun `offline, an account checked on the phone never says it waits for the network`() = runTest {
        connection.value = false
        val claim = CompletableDeferred<Boolean>()
        coEvery { interactor.migrationCheckNeedsNetwork() } returns false
        coEvery { interactor.needsMigration() } coAnswers { claim.await() }
        val vm = viewModel()
        vm.startAuth(PinCodeAction.TIMEOUT_CHECK)
        advanceUntilIdle()
        vm.onAuthenticationSucceeded()
        advanceUntilIdle()
        assertTrue(vm.state.migrating)
        assertFalse(vm.state.checkNeedsNetwork)
        claim.complete(false)
        advanceUntilIdle()
        verify(exactly = 1) { mainRouter.popBackStack() }
    }

    @Test
    fun `a check that needs the network waits for it and continues by itself`() = runTest {
        connection.value = false
        coEvery { interactor.migrationCheckNeedsNetwork() } returns true
        val vm = viewModel()
        vm.startAuth(PinCodeAction.TIMEOUT_CHECK)
        advanceUntilIdle()
        vm.onAuthenticationSucceeded()
        advanceUntilIdle()
        assertTrue(vm.state.migrating && vm.state.checkNeedsNetwork)
        verify(exactly = 0) { mainRouter.popBackStack() }
        connection.value = true
        advanceUntilIdle()
        verify(exactly = 1) { mainRouter.popBackStack() }
        assertFalse(vm.state.migrating)
    }
}
