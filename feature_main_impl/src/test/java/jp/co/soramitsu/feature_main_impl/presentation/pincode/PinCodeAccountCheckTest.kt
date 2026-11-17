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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

/**
 * The account check after unlocking. Update rule of 2026-11-09: the wallet opens as soon as the
 * PIN is right. The optional check for an older SORA balance to claim never keeps anyone at the
 * PIN screen: not offline, not while the network answers, and not on a claim it finds then.
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
        coEvery { it.checkPin("123456") } returns true
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

    private fun kotlinx.coroutines.test.TestScope.unlock(vm: PinCodeViewModel) {
        vm.startAuth(PinCodeAction.TIMEOUT_CHECK)
        advanceUntilIdle()
        vm.pinCodeNumberClicked("123456")
        advanceUntilIdle()
    }

    @Test
    fun `a second trigger while the wallet opens never opens it twice`() = runTest {
        val vm = viewModel()
        unlock(vm)
        // The biometrics dialog answer arrives as well.
        vm.biometryDialogNoClicked()
        advanceUntilIdle()
        verify(exactly = 1) { mainRouter.popBackStack() }
        coVerify(exactly = 1) { interactor.needsMigration() }
    }

    @Test
    fun `offline, a check that needs the network never keeps the wallet closed`() = runTest {
        connection.value = false
        val networkAnswer = CompletableDeferred<Boolean>()
        coEvery { interactor.migrationCheckNeedsNetwork() } returns true
        coEvery { interactor.needsMigration() } coAnswers { networkAnswer.await() }
        val vm = viewModel()
        unlock(vm)
        // The wallet is open while the check still waits for the network.
        verify(exactly = 1) { mainRouter.popBackStack() }
        assertFalse(vm.state.migrating)
        assertFalse(vm.state.checkNeedsNetwork)
        // Coming back online answers it in the background; nothing navigates again.
        connection.value = true
        networkAnswer.complete(true)
        advanceUntilIdle()
        verify(exactly = 1) { mainRouter.popBackStack() }
        verify(exactly = 0) { mainRouter.showClaim() }
        coVerify(exactly = 0) { interactor.migrationCheckNeedsNetwork() }
    }

    @Test
    fun `a claim found by the check is not pushed in front of the wallet that just opened`() = runTest {
        coEvery { interactor.needsMigration() } returns true
        val vm = viewModel()
        unlock(vm)
        verify(exactly = 1) { mainRouter.popBackStack() }
        verify(exactly = 0) { mainRouter.showClaim() }
        coVerify(exactly = 1) { interactor.needsMigration() }
    }

    @Test
    fun `a claim already known on this phone opens nothing in front of the wallet`() = runTest {
        // It is an entry on the wallet home; the unlock opens the wallet and only the wallet.
        coEvery { interactor.needsMigration() } returns true
        val vm = viewModel()
        unlock(vm)
        vm.onResume()
        advanceUntilIdle()
        verify(exactly = 1) { mainRouter.popBackStack() }
        verify(exactly = 0) { mainRouter.showClaim() }
    }
}
