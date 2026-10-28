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
